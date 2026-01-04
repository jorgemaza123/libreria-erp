package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.DevolucionDTO;
import com.libreria.sistema.model.dto.SunatResponseDTO;
import com.libreria.sistema.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DevolucionService {

    @Autowired
    private DevolucionVentaRepository devolucionRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private KardexRepository kardexRepository;

    @Autowired
    private CorrelativoRepository correlativoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CajaService cajaService;

    @Autowired
    private FacturacionElectronicaService facturacionService;

    private static final int DIAS_MAXIMO_DEVOLUCION = 30;

    /**
     * Procesa una devolución completa
     */
    @Transactional
    @Auditable(modulo = "DEVOLUCIONES", accion = "CREAR", descripcion = "Procesar devolución")
    public Map<String, Object> procesarDevolucion(DevolucionDTO dto) {
        // 1. Validar devolución
        validarDevolucion(dto);

        // 2. Obtener venta original
        Venta ventaOriginal = ventaRepository.findById(dto.getVentaOriginalId())
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        // 3. Determinar modo de facturación
        boolean facturaElectronicaActiva = facturacionService.isFacturacionElectronicaActiva();

        // 4. Obtener correlativo de nota de crédito
        String serie = facturaElectronicaActiva ? "C001" : "NC01";
        Correlativo correlativo = correlativoRepository.findByCodigoAndSerie("NOTA_CREDITO", serie)
                .orElse(new Correlativo("NOTA_CREDITO", serie, 0));
        Integer nuevoNumero = correlativo.getUltimoNumero() + 1;
        correlativo.setUltimoNumero(nuevoNumero);
        correlativoRepository.save(correlativo);

        // 5. Crear DevolucionVenta
        DevolucionVenta devolucion = new DevolucionVenta();
        devolucion.setVentaOriginal(ventaOriginal);
        devolucion.setSerie(serie);
        devolucion.setNumero(nuevoNumero);
        devolucion.setMotivoDevolucion(dto.getMotivoDevolucion());
        devolucion.setObservaciones(dto.getObservaciones());
        devolucion.setMetodoReembolso(dto.getMetodoReembolso());

        // Usuario actual
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);
        devolucion.setUsuario(usuario);

        // 6. Procesar detalles y calcular total
        BigDecimal totalDevuelto = procesarDetalles(devolucion, dto.getItems(), ventaOriginal);
        devolucion.setTotalDevuelto(totalDevuelto);

        // 7. Guardar devolución
        DevolucionVenta devolucionGuardada = devolucionRepository.save(devolucion);

        // 8. Actualizar estado de venta original
        actualizarEstadoVenta(ventaOriginal, totalDevuelto);

        // 9. Registrar egreso en caja (si reembolso es efectivo)
        if ("EFECTIVO".equals(dto.getMetodoReembolso())) {
            registrarEgresoCaja(devolucionGuardada);
        }

        // 10. Enviar a SUNAT si aplica
        String estadoSunat = "NO_APLICA";
        if (facturaElectronicaActiva) {
            try {
                estadoSunat = enviarNotaCreditoSunat(devolucionGuardada.getId());
            } catch (Exception e) {
                log.error("Error al enviar nota de crédito a SUNAT: {}", e.getMessage(), e);
                estadoSunat = "ERROR_ENVIO";
            }
        }

        return Map.of(
                "id", devolucionGuardada.getId(),
                "serie", devolucionGuardada.getSerie(),
                "numero", devolucionGuardada.getNumero(),
                "totalDevuelto", totalDevuelto,
                "estadoSunat", estadoSunat,
                "facturaElectronica", facturaElectronicaActiva
        );
    }

    /**
     * Procesa los detalles de la devolución
     */
    private BigDecimal procesarDetalles(DevolucionVenta devolucion, List<DevolucionDTO.ItemDevolucion> items, Venta ventaOriginal) {
        BigDecimal totalDevuelto = BigDecimal.ZERO;

        for (DevolucionDTO.ItemDevolucion item : items) {
            Producto producto = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            // Crear detalle
            DetalleDevolucion detalle = new DetalleDevolucion();
            detalle.setDevolucion(devolucion);
            detalle.setProducto(producto);
            detalle.setDescripcion(item.getDescripcion() != null ? item.getDescripcion() : producto.getNombre());
            detalle.setCantidadDevuelta(item.getCantidadDevuelta());
            detalle.setPrecioUnitario(item.getPrecioUnitario());
            detalle.setSubtotal(item.getCantidadDevuelta().multiply(item.getPrecioUnitario()));

            devolucion.getDetalles().add(detalle);

            totalDevuelto = totalDevuelto.add(detalle.getSubtotal());

            // Regresar stock al inventario
            int cantidadDevuelta = item.getCantidadDevuelta().intValue();
            producto.setStockActual(producto.getStockActual() + cantidadDevuelta);
            productoRepository.save(producto);

            // Registrar en Kardex
            registrarKardex(producto, cantidadDevuelta, devolucion, ventaOriginal);
        }

        return totalDevuelto;
    }

    /**
     * Registra movimiento en Kardex
     */
    private void registrarKardex(Producto producto, int cantidad, DevolucionVenta devolucion, Venta ventaOriginal) {
        Kardex kardex = new Kardex();
        kardex.setProducto(producto);
        kardex.setTipo("INGRESO");
        kardex.setMotivo("DEVOLUCIÓN NC " + devolucion.getSerie() + "-" + devolucion.getNumero() +
                         " (Venta " + ventaOriginal.getSerie() + "-" + ventaOriginal.getNumero() + ")");
        kardex.setCantidad(cantidad);
        kardex.setStockAnterior(producto.getStockActual() - cantidad);
        kardex.setStockActual(producto.getStockActual());
        kardexRepository.save(kardex);
    }

    /**
     * Actualiza el estado de la venta original
     */
    private void actualizarEstadoVenta(Venta venta, BigDecimal totalDevuelto) {
        // Verificar si es devolución total o parcial
        if (totalDevuelto.compareTo(venta.getTotal()) >= 0) {
            venta.setEstado("DEVUELTO_TOTAL");
        } else {
            venta.setEstado("DEVUELTO_PARCIAL");
        }

        // Si la venta era a crédito, ajustar saldo pendiente
        if ("CREDITO".equals(venta.getFormaPago()) && venta.getSaldoPendiente() != null) {
            BigDecimal nuevoSaldo = venta.getSaldoPendiente().subtract(totalDevuelto);
            if (nuevoSaldo.compareTo(BigDecimal.ZERO) < 0) {
                nuevoSaldo = BigDecimal.ZERO;
            }
            venta.setSaldoPendiente(nuevoSaldo);
        }

        ventaRepository.save(venta);
    }

    /**
     * Registra egreso en caja por reembolso
     */
    private void registrarEgresoCaja(DevolucionVenta devolucion) {
        try {
            String concepto = "REEMBOLSO DEVOLUCIÓN NC " + devolucion.getSerie() + "-" + devolucion.getNumero();
            cajaService.registrarMovimiento("EGRESO", concepto, devolucion.getTotalDevuelto());
        } catch (Exception e) {
            log.warn("No se pudo registrar movimiento en caja: {}", e.getMessage());
        }
    }

    /**
     * Valida que la devolución pueda procesarse
     */
    public void validarDevolucion(DevolucionDTO dto) {
        // Validar que la venta existe
        Venta venta = ventaRepository.findById(dto.getVentaOriginalId())
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        // Validar que la venta no esté anulada o ya devuelta totalmente
        if ("ANULADO".equals(venta.getEstado())) {
            throw new RuntimeException("No se puede devolver una venta anulada");
        }
        if ("DEVUELTO_TOTAL".equals(venta.getEstado())) {
            throw new RuntimeException("Esta venta ya fue devuelta completamente");
        }

        // Validar plazo de devolución
        long diasTranscurridos = ChronoUnit.DAYS.between(venta.getFechaEmision(), LocalDate.now());
        if (diasTranscurridos > DIAS_MAXIMO_DEVOLUCION) {
            throw new RuntimeException("El plazo máximo para devoluciones es de " + DIAS_MAXIMO_DEVOLUCION + " días");
        }

        // Validar que hay items
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new RuntimeException("Debe seleccionar al menos un producto para devolver");
        }

        // Validar cantidades (esto requeriría comparar con las cantidades originales de la venta)
        // Por simplicidad, validamos que sean mayores a cero
        for (DevolucionDTO.ItemDevolucion item : dto.getItems()) {
            if (item.getCantidadDevuelta().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Las cantidades deben ser mayores a cero");
            }
        }
    }

    /**
     * Envía nota de crédito a SUNAT
     */
    public String enviarNotaCreditoSunat(Long devolucionId) {
        DevolucionVenta devolucion = devolucionRepository.findById(devolucionId)
                .orElseThrow(() -> new RuntimeException("Devolución no encontrada"));

        try {
            SunatResponseDTO respuesta = facturacionService.enviarNotaCreditoSunat(devolucion);

            // Actualizar estado SUNAT
            devolucion.setSunatEstado(respuesta.getPayload() != null ? respuesta.getPayload().getEstado() : "ERROR");
            devolucion.setSunatFechaEnvio(LocalDateTime.now());

            if (respuesta.getPayload() != null) {
                devolucion.setSunatHash(respuesta.getPayload().getHash());
                devolucion.setSunatXmlUrl(respuesta.getPayload().getXml());
                devolucion.setSunatCdrUrl(respuesta.getPayload().getCdr());
                if (respuesta.getPayload().getPdf() != null) {
                    devolucion.setSunatPdfUrl(respuesta.getPayload().getPdf().getTicket());
                }
            }

            if (!respuesta.getSuccess()) {
                devolucion.setSunatMensajeError(respuesta.getMessage());
            }

            devolucionRepository.save(devolucion);

            return devolucion.getSunatEstado();

        } catch (Exception e) {
            devolucion.setSunatEstado("ERROR");
            devolucion.setSunatMensajeError(e.getMessage());
            devolucion.setSunatFechaEnvio(LocalDateTime.now());
            devolucionRepository.save(devolucion);

            throw new RuntimeException("Error al enviar a SUNAT: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene devoluciones por venta
     */
    public List<DevolucionVenta> obtenerDevolucionesPorVenta(Long ventaId) {
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));
        return devolucionRepository.findByVentaOriginal(venta);
    }

    /**
     * Buscar devoluciones con filtros
     */
    public Page<DevolucionVenta> buscarConFiltros(String estado, LocalDate fechaInicio, LocalDate fechaFin, Pageable pageable) {
        return devolucionRepository.buscarConFiltros(estado, fechaInicio, fechaFin, pageable);
    }

    /**
     * Obtener devolución por ID
     */
    public DevolucionVenta obtenerPorId(Long id) {
        return devolucionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Devolución no encontrada"));
    }

    /**
     * Anular una devolución (solo ADMIN)
     */
    @Transactional
    @Auditable(modulo = "DEVOLUCIONES", accion = "ANULAR", descripcion = "Anular devolución")
    public void anularDevolucion(Long devolucionId) {
        DevolucionVenta devolucion = devolucionRepository.findById(devolucionId)
                .orElseThrow(() -> new RuntimeException("Devolución no encontrada"));

        if ("ANULADA".equals(devolucion.getEstado())) {
            throw new RuntimeException("La devolución ya está anulada");
        }

        // Cambiar estado
        devolucion.setEstado("ANULADA");

        // Revertir stock (quitar el stock que se había regresado)
        for (DetalleDevolucion detalle : devolucion.getDetalles()) {
            Producto producto = detalle.getProducto();
            int cantidadDevuelta = detalle.getCantidadDevuelta().intValue();
            producto.setStockActual(producto.getStockActual() - cantidadDevuelta);
            productoRepository.save(producto);

            // Kardex de reversión
            Kardex kardex = new Kardex();
            kardex.setProducto(producto);
            kardex.setTipo("SALIDA");
            kardex.setMotivo("ANULACIÓN DEVOLUCIÓN " + devolucion.getSerie() + "-" + devolucion.getNumero());
            kardex.setCantidad(cantidadDevuelta);
            kardex.setStockAnterior(producto.getStockActual() + cantidadDevuelta);
            kardex.setStockActual(producto.getStockActual());
            kardexRepository.save(kardex);
        }

        // Revertir estado de venta original (volver a EMITIDO o PAGADO_TOTAL)
        Venta ventaOriginal = devolucion.getVentaOriginal();
        if ("DEVUELTO_TOTAL".equals(ventaOriginal.getEstado()) || "DEVUELTO_PARCIAL".equals(ventaOriginal.getEstado())) {
            // Determinar el estado correcto
            if (ventaOriginal.getSaldoPendiente() != null &&
                ventaOriginal.getSaldoPendiente().compareTo(BigDecimal.ZERO) == 0) {
                ventaOriginal.setEstado("PAGADO_TOTAL");
            } else {
                ventaOriginal.setEstado("EMITIDO");
            }
            ventaRepository.save(ventaOriginal);
        }

        devolucionRepository.save(devolucion);
    }
}
