package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.SunatResponseDTO;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

@Service
public class VentaService {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final KardexRepository kardexRepository;
    private final CorrelativoRepository correlativoRepository;
    private final ClienteRepository clienteRepository;
    private final AmortizacionRepository amortizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CajaService cajaService;
    private final FacturacionElectronicaService facturacionService;

    public VentaService(ProductoRepository productoRepository,
                        VentaRepository ventaRepository,
                        KardexRepository kardexRepository,
                        CorrelativoRepository correlativoRepository,
                        ClienteRepository clienteRepository,
                        AmortizacionRepository amortizacionRepository,
                        UsuarioRepository usuarioRepository,
                        CajaService cajaService,
                        FacturacionElectronicaService facturacionService) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.kardexRepository = kardexRepository;
        this.correlativoRepository = correlativoRepository;
        this.clienteRepository = clienteRepository;
        this.amortizacionRepository = amortizacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.cajaService = cajaService;
        this.facturacionService = facturacionService;
    }

    /**
     * Crea una nueva venta con soporte DUAL-MODE:
     * - MODO INTERNO (facturaElectronicaActiva = false): Series I001/IF001, no envía a SUNAT
     * - MODO ELECTRÓNICO (facturaElectronicaActiva = true): Series B001/F001, envía automáticamente a SUNAT
     *
     * @param dto Datos de la venta
     * @return Map con id de la venta y estado SUNAT (si aplica)
     * @throws RuntimeException si hay error de stock o al procesar
     * @throws OptimisticLockingFailureException si hay conflicto de concurrencia en stock
     */
    @Transactional
    @Auditable(modulo = "VENTAS", accion = "CREAR", descripcion = "Registrar nueva venta")
    public Map<String, Object> crearVenta(VentaDTO dto) throws OptimisticLockingFailureException {

        // 1. VERIFICAR MODO DE FACTURACIÓN
        boolean facturaElectronicaActiva = facturacionService.isFacturacionElectronicaActiva();

        // 2. CLIENTE
        Cliente cliente = obtenerOCrearCliente(dto);

        // 3. DETERMINAR TIPO Y SERIE SEGÚN MODO
        String tipo = dto.getTipoComprobante() != null ? dto.getTipoComprobante() : "NOTA_VENTA";
        String serie = facturacionService.obtenerSerie(tipo, facturaElectronicaActiva);

        // 4. OBTENER CORRELATIVO
        Correlativo correlativo = correlativoRepository.findByCodigoAndSerie(tipo, serie)
                .orElse(new Correlativo(tipo, serie, 0));
        Integer nuevoNumero = correlativo.getUltimoNumero() + 1;
        correlativo.setUltimoNumero(nuevoNumero);
        correlativoRepository.save(correlativo);

        // 5. CREAR CABECERA VENTA
        Venta venta = new Venta();
        venta.setClienteEntity(cliente);
        venta.setClienteDenominacion(cliente.getNombreRazonSocial());
        venta.setClienteNumeroDocumento(cliente.getNumeroDocumento());
        venta.setClienteTipoDocumento(cliente.getTipoDocumento());
        venta.setClienteDireccion(cliente.getDireccion());
        venta.setTipoComprobante(tipo);
        venta.setSerie(serie);
        venta.setNumero(nuevoNumero);
        venta.setFechaEmision(LocalDate.now());
        venta.setEstado("EMITIDO");

        // Usuario actual
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);
        venta.setUsuario(usuario);

        // 6. PROCESAR DETALLES
        BigDecimal[] totales = procesarDetalles(venta, dto);
        BigDecimal totalVenta = totales[0];
        BigDecimal totalGravada = totales[1];
        BigDecimal totalIgv = totales[2];

        venta.setTotal(totalVenta);
        venta.setTotalGravada(totalGravada);
        venta.setTotalIgv(totalIgv);

        // 7. FORMA DE PAGO
        BigDecimal montoAbonado = procesarFormaPago(venta, dto, totalVenta);

        // 8. GUARDAR VENTA
        Venta ventaGuardada = ventaRepository.save(venta);

        // 9. REGISTRAR PAGO Y MOVIMIENTO DE CAJA
        if (montoAbonado.compareTo(BigDecimal.ZERO) > 0) {
            registrarPagoYCaja(ventaGuardada, montoAbonado);
        }

        // 10. ENVÍO A SUNAT (SOLO EN MODO ELECTRÓNICO)
        String estadoSunat = "NO_APLICA";
        if (facturaElectronicaActiva && !tipo.equals("NOTA_VENTA")) {
            try {
                SunatResponseDTO respuestaSunat = facturacionService.enviarComprobanteSunat(ventaGuardada.getId());
                estadoSunat = respuestaSunat.getPayload() != null ?
                        respuestaSunat.getPayload().getEstado() : "ERROR";
            } catch (Exception e) {
                System.err.println("ERROR AL ENVIAR A SUNAT: " + e.getMessage());
                estadoSunat = "ERROR_ENVIO";
            }
        }

        return Map.of(
                "id", ventaGuardada.getId(),
                "serie", ventaGuardada.getSerie(),
                "numero", ventaGuardada.getNumero(),
                "estadoSunat", estadoSunat,
                "facturaElectronica", facturaElectronicaActiva
        );
    }

    /**
     * Obtiene un cliente existente o crea uno nuevo
     */
    private Cliente obtenerOCrearCliente(VentaDTO dto) {
        return clienteRepository.findByNumeroDocumento(dto.getClienteDocumento())
                .orElseGet(() -> {
                    Cliente c = new Cliente();
                    c.setNumeroDocumento(dto.getClienteDocumento());
                    c.setNombreRazonSocial(dto.getClienteNombre());
                    c.setDireccion(dto.getClienteDireccion());
                    c.setTelefono(dto.getClienteTelefono());
                    c.setTipoDocumento(dto.getClienteDocumento().length() == 11 ? "6" : "1");
                    return clienteRepository.save(c);
                });
    }

    /**
     * Procesa los detalles de venta: validación de stock, creación de detalles y kardex
     *
     * @return Array [totalVenta, totalGravada, totalIgv]
     */
    private BigDecimal[] procesarDetalles(Venta venta, VentaDTO dto) {
        BigDecimal totalVenta = BigDecimal.ZERO;
        BigDecimal totalGravada = BigDecimal.ZERO;
        BigDecimal totalIgv = BigDecimal.ZERO;

        for (VentaDTO.DetalleDTO item : dto.getItems()) {
            Producto prod = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            // Validación estricta de Stock
            if (prod.getStockActual() < item.getCantidad().intValue()) {
                throw new RuntimeException("Stock insuficiente para: " + prod.getNombre());
            }

            // Cálculos
            BigDecimal precioFinal = item.getPrecioVenta();
            BigDecimal cantidad = item.getCantidad();
            BigDecimal subtotalItem = precioFinal.multiply(cantidad);
            BigDecimal valorUnitario = precioFinal.divide(new BigDecimal("1.18"), 6, RoundingMode.HALF_UP);
            BigDecimal valorVenta = valorUnitario.multiply(cantidad);
            BigDecimal igvItem = subtotalItem.subtract(valorVenta);

            // Crear detalle
            DetalleVenta det = new DetalleVenta();
            det.setVenta(venta);
            det.setProducto(prod);
            det.setCantidad(cantidad);
            det.setDescripcion(prod.getNombre());
            det.setUnidadMedida(prod.getUnidadMedida() != null ? prod.getUnidadMedida() : "NIU");
            det.setPrecioUnitario(precioFinal);
            det.setValorUnitario(valorUnitario);
            det.setSubtotal(subtotalItem);
            det.setCodigoTipoAfectacionIgv(prod.getTipoAfectacionIgv() != null ?
                mapearTipoAfectacion(prod.getTipoAfectacionIgv()) : "10");

            venta.getItems().add(det);

            // Acumular totales
            totalVenta = totalVenta.add(subtotalItem);
            totalGravada = totalGravada.add(valorVenta);
            totalIgv = totalIgv.add(igvItem);

            // Kardex
            registrarKardex(prod, cantidad.intValue(), venta);

            // Actualizar Stock
            prod.setStockActual(prod.getStockActual() - cantidad.intValue());
            productoRepository.save(prod);
        }

        return new BigDecimal[]{totalVenta, totalGravada, totalIgv};
    }

    /**
     * Registra movimiento en Kardex
     */
    private void registrarKardex(Producto prod, int cantidad, Venta venta) {
        Kardex k = new Kardex();
        k.setProducto(prod);
        k.setTipo("SALIDA");
        k.setMotivo("VENTA " + venta.getSerie() + "-" + venta.getNumero());
        k.setCantidad(cantidad);
        k.setStockAnterior(prod.getStockActual());
        k.setStockActual(prod.getStockActual() - cantidad);
        kardexRepository.save(k);
    }

    /**
     * Procesa la forma de pago (Contado/Crédito)
     *
     * @return Monto abonado
     */
    private BigDecimal procesarFormaPago(Venta venta, VentaDTO dto, BigDecimal totalVenta) {
        BigDecimal montoAbonado;

        if ("CREDITO".equals(dto.getFormaPago())) {
            venta.setFormaPago("CREDITO");
            BigDecimal inicial = dto.getMontoInicial() != null ? dto.getMontoInicial() : BigDecimal.ZERO;
            montoAbonado = inicial;
            venta.setMontoPagado(inicial);
            venta.setSaldoPendiente(totalVenta.subtract(inicial));
            int dias = dto.getDiasCredito() != null ? dto.getDiasCredito() : 7;
            venta.setFechaVencimiento(LocalDate.now().plusDays(dias));
        } else {
            venta.setFormaPago("CONTADO");
            venta.setMontoPagado(totalVenta);
            venta.setSaldoPendiente(BigDecimal.ZERO);
            venta.setFechaVencimiento(LocalDate.now());
            montoAbonado = totalVenta;
        }

        return montoAbonado;
    }

    /**
     * Registra el pago inicial y el movimiento de caja
     */
    private void registrarPagoYCaja(Venta venta, BigDecimal monto) {
        // Amortización
        Amortizacion amo = new Amortizacion();
        amo.setVenta(venta);
        amo.setMonto(monto);
        amo.setMetodoPago("EFECTIVO");
        amo.setObservacion("PAGO INICIAL / CONTADO");
        amortizacionRepository.save(amo);

        // Movimiento de caja
        try {
            cajaService.registrarMovimiento("INGRESO",
                    "VENTA " + venta.getSerie() + "-" + venta.getNumero(),
                    monto);
        } catch (Exception e) {
            System.err.println("ADVERTENCIA: Venta sin movimiento de caja: " + e.getMessage());
        }
    }

    /**
     * Mapea tipo de afectación de texto a código SUNAT
     */
    private String mapearTipoAfectacion(String tipoAfectacion) {
        return switch (tipoAfectacion.toUpperCase()) {
            case "GRAVADO" -> "10";
            case "EXONERADO" -> "20";
            case "INAFECTO" -> "30";
            default -> "10";
        };
    }

    /**
     * Verifica si la facturación electrónica está activa
     */
    public boolean isFacturacionElectronicaActiva() {
        return facturacionService.isFacturacionElectronicaActiva();
    }
}
