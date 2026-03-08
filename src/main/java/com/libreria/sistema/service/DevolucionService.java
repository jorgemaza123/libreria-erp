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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Autowired
    private VentaListaEscolarRepository ventaListaRepository;

    @Autowired
    private DetalleListaEscolarRepository detalleListaRepository;

    @Autowired
    private ListaEscolarRepository listaEscolarRepository;

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

        // 4. Obtener correlativo de nota de crédito CON LOCK PESIMISTA (evita duplicados en concurrencia)
        // Si la serie no existe, se crea automáticamente con ultimoNumero = 0
        String serie = facturaElectronicaActiva ? "C001" : "NC01";
        Correlativo correlativo = correlativoRepository.findByCodigoAndSerieWithLock("NOTA_CREDITO", serie)
                .orElseGet(() -> {
                    Correlativo nuevo = new Correlativo("NOTA_CREDITO", serie, 0);
                    return correlativoRepository.save(nuevo);
                });
        // SAFE UNBOXING DEFENSIVO: Triple protección contra NPE
        Integer ultimoActual = correlativo.getUltimoNumero();
        int nuevoNumero = (ultimoActual != null ? ultimoActual : 0) + 1;
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

        // 8b. Si la venta provino de una lista escolar, revertir los items devueltos
        revertirItemsListaEscolar(ventaOriginal, dto.getItems(), totalDevuelto);

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

            // FIX ERROR-5: no restaurar stock si el producto es un servicio
            boolean esServicio = producto.getTipo() != null && "SERVICIO".equalsIgnoreCase(producto.getTipo());
            if (!esServicio) {
                int cantidadDevuelta = item.getCantidadDevuelta().intValue();
                producto.setStockActual(producto.getStockActual() + cantidadDevuelta);
                productoRepository.save(producto);
                registrarKardex(producto, cantidadDevuelta, devolucion, ventaOriginal);
            }
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
        // Determinar nuevo estado
        String nuevoEstado = totalDevuelto.compareTo(venta.getTotal()) >= 0
                ? "DEVUELTO_TOTAL" : "DEVUELTO_PARCIAL";

        // UPDATE directo: evita que Hibernate omita el cambio por dirty-checking
        ventaRepository.actualizarEstado(venta.getId(), nuevoEstado);

        // Si la venta era a crédito, ajustar saldo pendiente
        if ("CREDITO".equals(venta.getFormaPago()) && venta.getSaldoPendiente() != null) {
            BigDecimal nuevoSaldo = venta.getSaldoPendiente().subtract(totalDevuelto);
            if (nuevoSaldo.compareTo(BigDecimal.ZERO) < 0) {
                nuevoSaldo = BigDecimal.ZERO;
            }
            ventaRepository.actualizarSaldoPendiente(venta.getId(), nuevoSaldo);
        }
    }

    /**
     * Registra egreso en caja por reembolso - OBLIGATORIO: Si falla, debe abortar la transacción
     */
    private void registrarEgresoCaja(DevolucionVenta devolucion) {
        String concepto = "REEMBOLSO DEVOLUCIÓN NC " + devolucion.getSerie() + "-" + devolucion.getNumero();
        cajaService.registrarMovimiento("EGRESO", concepto, devolucion.getTotalDevuelto(), CategoriaMovimiento.DEVOLUCION);
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

        // Validar cantidades
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
        // CORRECCIÓN: Convertir string vacío a NULL para que la query JPQL funcione
        if (estado != null && estado.trim().isEmpty()) {
            estado = null;
        }
        return devolucionRepository.buscarConFiltros(estado, fechaInicio, fechaFin, pageable);
    }

    /**
     * Mapa productoId → cantidadTotalDevuelta para una venta.
     * Solo considera devoluciones activas (estado != ANULADA).
     * Usado por el modal de detalle de venta para mostrar cantidades restantes.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> obtenerCantidadesDevueltasPorVenta(Long ventaId) {
        Map<Long, BigDecimal> cantidadesDevueltas = new HashMap<>();
        Venta venta = ventaRepository.findById(ventaId).orElse(null);
        if (venta == null) return cantidadesDevueltas;

        List<DevolucionVenta> devoluciones = devolucionRepository.findByVentaOriginal(venta);
        for (DevolucionVenta dev : devoluciones) {
            if (!"ANULADA".equals(dev.getEstado())) {
                for (DetalleDevolucion det : dev.getDetalles()) {
                    if (det.getProducto() != null) {
                        cantidadesDevueltas.merge(
                            det.getProducto().getId(),
                            det.getCantidadDevuelta(),
                            BigDecimal::add
                        );
                    }
                }
            }
        }
        return cantidadesDevueltas;
    }

    /**
     * Obtiene en una sola query el total devuelto agrupado por ventaId
     * para una lista de IDs. Usado en el listado de ventas para no hacer N+1.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> obtenerMapaTotalesDevueltos(List<Long> ventaIds) {
        if (ventaIds == null || ventaIds.isEmpty()) return new HashMap<>();
        List<Object[]> rows = devolucionRepository.sumTotalesDevueltosPorVentas(ventaIds);
        Map<Long, BigDecimal> mapa = new HashMap<>();
        for (Object[] row : rows) {
            mapa.put((Long) row[0], (BigDecimal) row[1]);
        }
        return mapa;
    }

    /**
     * Suma el totalDevuelto de todas las devoluciones activas de una venta.
     * Usado para calcular el monto neto restante en el modal y en la impresión.
     */
    @Transactional(readOnly = true)
    public BigDecimal obtenerTotalDevueltoPorVenta(Long ventaId) {
        Venta venta = ventaRepository.findById(ventaId).orElse(null);
        if (venta == null) return BigDecimal.ZERO;
        List<DevolucionVenta> devoluciones = devolucionRepository.findByVentaOriginal(venta);
        return devoluciones.stream()
                .filter(d -> !"ANULADA".equals(d.getEstado()))
                .map(DevolucionVenta::getTotalDevuelto)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

        // FIX ERROR-9: restaurar el saldoPendiente de la venta original cuando la devolución
        // era de una venta a crédito. El monto devuelto había reducido el saldo; al anular
        // la devolución ese monto debe volver a ser adeudado.
        Venta ventaOriginal = devolucion.getVentaOriginal();
        BigDecimal saldoParaEstado = ventaOriginal.getSaldoPendiente() != null
                ? ventaOriginal.getSaldoPendiente() : BigDecimal.ZERO;

        if ("CREDITO".equals(ventaOriginal.getFormaPago())
                && devolucion.getTotalDevuelto() != null
                && devolucion.getTotalDevuelto().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal saldoRestaurado = saldoParaEstado.add(devolucion.getTotalDevuelto());
            // No puede superar el total original de la venta
            if (saldoRestaurado.compareTo(ventaOriginal.getTotal()) > 0) {
                saldoRestaurado = ventaOriginal.getTotal();
            }
            ventaRepository.actualizarSaldoPendiente(ventaOriginal.getId(), saldoRestaurado);
            // Usar el saldo ya restaurado para determinar el estado correcto
            saldoParaEstado = saldoRestaurado;
        }

        // Revertir estado de venta original usando el saldo calculado (no el valor en caché)
        if ("DEVUELTO_TOTAL".equals(ventaOriginal.getEstado()) || "DEVUELTO_PARCIAL".equals(ventaOriginal.getEstado())) {
            if (saldoParaEstado.compareTo(BigDecimal.ZERO) == 0) {
                ventaOriginal.setEstado("PAGADO_TOTAL");
            } else {
                ventaOriginal.setEstado("EMITIDO");
            }
            ventaRepository.save(ventaOriginal);
        }

        // CORRECCION: Si la devolución tuvo reembolso en efectivo, registrar ingreso para anular el egreso
        if ("EFECTIVO".equals(devolucion.getMetodoReembolso()) && devolucion.getTotalDevuelto() != null) {
            try {
                String concepto = "ANULACIÓN REEMBOLSO NC " + devolucion.getSerie() + "-" + devolucion.getNumero();
                cajaService.registrarMovimiento("INGRESO", concepto, devolucion.getTotalDevuelto(), CategoriaMovimiento.OTRO_INGRESO);
            } catch (Exception e) {
                log.warn("No se pudo registrar ingreso de anulación de devolución (¿caja cerrada?): {}", e.getMessage());
                // No bloqueamos la anulación si la caja está cerrada, pero queda registrado
            }
        }

        devolucionRepository.save(devolucion);
    }

    /**
     * Revierte los items de lista escolar que fueron devueltos.
     * Solo actúa si la venta original provino de una lista escolar.
     *
     * - Los items devueltos vuelven a estado COTIZADO (disponibles para reventa desde la lista).
     * - El montoVendido de la lista se reduce en el monto devuelto.
     * - Se recalculan itemsPendientes e itemsVendidos.
     * - Si la lista estaba COMPLETADA, vuelve a EN_PROCESO.
     */
    private void revertirItemsListaEscolar(Venta ventaOriginal,
                                            List<DevolucionDTO.ItemDevolucion> itemsDevueltos,
                                            BigDecimal totalDevuelto) {
        // 1. Verificar si la venta provino de una lista escolar
        ventaListaRepository.findByVentaId(ventaOriginal.getId()).ifPresent(ventaLista -> {

            Long listaId = ventaLista.getListaEscolar().getId();

            // 2. Cargar la lista con sus detalles frescos desde DB
            ListaEscolar lista = listaEscolarRepository.findByIdConDetalles(listaId).orElse(null);
            if (lista == null) {
                log.warn("Lista escolar {} no encontrada al revertir devolución", listaId);
                return;
            }

            // 3. Obtener los detalles de lista que fueron vendidos en esta venta
            List<DetalleListaEscolar> detallesVendidos = detalleListaRepository.findByVentaId(ventaOriginal.getId());

            // 4. Set de IDs de productos devueltos para matching rápido
            Set<Long> productosDevueltos = itemsDevueltos.stream()
                    .map(DevolucionDTO.ItemDevolucion::getProductoId)
                    .collect(Collectors.toSet());

            // 5. Revertir los items cuyo producto fue devuelto
            for (DetalleListaEscolar detalle : detallesVendidos) {
                Producto productoFinal = detalle.getProductoFinal();
                if (productoFinal != null && productosDevueltos.contains(productoFinal.getId())) {
                    detalle.setEstado("COTIZADO");
                    detalle.setVenta(null);
                    detalle.setDetalleVentaId(null);
                    detalleListaRepository.save(detalle);
                    log.info("Item lista {} revertido a COTIZADO por devolución de venta {}",
                            detalle.getId(), ventaOriginal.getId());
                }
            }

            // 6. Recalcular montoVendido de la lista
            BigDecimal nuevoMonto = lista.getMontoVendido().subtract(totalDevuelto);
            lista.setMontoVendido(nuevoMonto.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : nuevoMonto);

            // 7. Recalcular contadores (recalcularPendientes necesita detalles actualizados en memoria)
            // Actualizamos el estado en la colección en memoria para que el cálculo sea correcto
            for (DetalleListaEscolar detalleLista : lista.getDetalles()) {
                detallesVendidos.stream()
                        .filter(d -> d.getId().equals(detalleLista.getId()))
                        .filter(d -> "COTIZADO".equals(d.getEstado()))
                        .findFirst()
                        .ifPresent(d -> detalleLista.setEstado("COTIZADO"));
            }
            lista.recalcularPendientes();

            // 8. Ajustar estado de la lista
            if ("COMPLETADA".equals(lista.getEstado()) && lista.getItemsPendientes() > 0) {
                lista.setEstado("EN_PROCESO");
            }
            listaEscolarRepository.save(lista);

            // 9. Reducir el montoVendido del registro VentaListaEscolar
            BigDecimal nuevoMontoVenta = ventaLista.getMontoVendido().subtract(totalDevuelto);
            ventaLista.setMontoVendido(nuevoMontoVenta.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : nuevoMontoVenta);
            ventaListaRepository.save(ventaLista);

            log.info("Lista escolar {} actualizada tras devolución: monto={}, pendientes={}",
                    lista.getCodigoCompleto(), lista.getMontoVendido(), lista.getItemsPendientes());
        });
    }
}