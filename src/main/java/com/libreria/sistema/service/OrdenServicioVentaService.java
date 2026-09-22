package com.libreria.sistema.service;

import com.libreria.sistema.model.Amortizacion;
import com.libreria.sistema.model.CategoriaMovimiento;
import com.libreria.sistema.model.Cliente;
import com.libreria.sistema.model.Correlativo;
import com.libreria.sistema.model.DetalleVenta;
import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.model.OrdenItem;
import com.libreria.sistema.model.OrdenServicio;
import com.libreria.sistema.model.OrdenServicioPago;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.model.dto.OrdenDTO;
import com.libreria.sistema.model.dto.OrdenServicioPagoDTO;
import com.libreria.sistema.repository.AmortizacionRepository;
import com.libreria.sistema.repository.ClienteRepository;
import com.libreria.sistema.repository.CorrelativoRepository;
import com.libreria.sistema.repository.OrdenServicioPagoRepository;
import com.libreria.sistema.repository.OrdenServicioRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import com.libreria.sistema.repository.VentaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class OrdenServicioVentaService {

    private static final String SERVICIO_BASE_CODE = "SERV-001";
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal CIEN = new BigDecimal("100");

    private final OrdenServicioRepository ordenServicioRepository;
    private final OrdenServicioPagoRepository ordenServicioPagoRepository;
    private final CajaService cajaService;
    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final CorrelativoRepository correlativoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AmortizacionRepository amortizacionRepository;
    private final ConfiguracionService configuracionService;
    private final ClienteService clienteService;
    private final ServicioCategoriaService servicioCategoriaService;

    public OrdenServicioVentaService(OrdenServicioRepository ordenServicioRepository,
                                     OrdenServicioPagoRepository ordenServicioPagoRepository,
                                     CajaService cajaService,
                                     VentaRepository ventaRepository,
                                     ProductoRepository productoRepository,
                                     CorrelativoRepository correlativoRepository,
                                     ClienteRepository clienteRepository,
                                     UsuarioRepository usuarioRepository,
                                     AmortizacionRepository amortizacionRepository,
                                     ConfiguracionService configuracionService,
                                     ClienteService clienteService,
                                     ServicioCategoriaService servicioCategoriaService) {
        this.ordenServicioRepository = ordenServicioRepository;
        this.ordenServicioPagoRepository = ordenServicioPagoRepository;
        this.cajaService = cajaService;
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.correlativoRepository = correlativoRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.amortizacionRepository = amortizacionRepository;
        this.configuracionService = configuracionService;
        this.clienteService = clienteService;
        this.servicioCategoriaService = servicioCategoriaService;
    }

    @Transactional
    public OrdenServicio guardarOrden(OrdenDTO dto) {
        boolean esEdicion = dto.getId() != null;
        OrdenServicio orden = esEdicion
                ? ordenServicioRepository.findDetalleCompletoById(dto.getId())
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"))
                : new OrdenServicio();

        if (orden.getVentaId() != null) {
            throw new RuntimeException("La orden ya fue cerrada como venta y no puede editarse desde este formulario.");
        }

        mapearCabecera(orden, dto);
        orden.getItems().clear();

        BigDecimal total = BigDecimal.ZERO;
        if (dto.getItems() != null) {
            for (OrdenDTO.ItemDTO itemDto : dto.getItems()) {
                String descripcion = texto(itemDto.getDescripcion());
                if (descripcion.isBlank()) {
                    continue;
                }
                OrdenItem item = new OrdenItem();
                item.setOrden(orden);
                item.setDescripcion(descripcion);
                item.setCosto(monto(itemDto.getCosto()));
                orden.getItems().add(item);
                total = total.add(item.getCosto());
            }
        }

        if (orden.getItems().isEmpty()) {
            throw new RuntimeException("Debe agregar al menos una tarea o item.");
        }

        orden.setTotal(total.setScale(2, RoundingMode.HALF_UP));

        boolean tienePagos = !orden.getPagos().isEmpty();
        if (!tienePagos) {
            BigDecimal adelanto = limitarPagoInicial(dto.getACuenta(), orden.getTotal());
            orden.setACuenta(adelanto);
            orden.setSaldo(orden.getTotal().subtract(adelanto).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        }

        if (orden.getEstado() == null || orden.getEstado().isBlank()) {
            orden.setEstado("PENDIENTE");
        }

        OrdenServicio guardada = ordenServicioRepository.save(orden);

        if (!esEdicion) {
            BigDecimal adelanto = limitarPagoInicial(dto.getACuenta(), guardada.getTotal());
            if (adelanto.compareTo(BigDecimal.ZERO) > 0) {
                registrarPagoInterno(guardada, adelanto, dto.getMetodoPago(), "Adelanto inicial del servicio",
                        CategoriaMovimiento.ANTICIPO_SERVICIO);
            }
        } else if (!guardada.getPagos().isEmpty()) {
            recalcularMontos(guardada);
            guardada = ordenServicioRepository.save(guardada);
        }

        return guardada;
    }

    @Transactional
    public OrdenServicioPago registrarAbono(Long ordenId, OrdenServicioPagoDTO dto) {
        OrdenServicio orden = ordenServicioRepository.findDetalleCompletoById(ordenId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        if (orden.getVentaId() != null) {
            throw new RuntimeException("La orden ya fue convertida en venta. Los cobros posteriores deben registrarse desde cobranzas.");
        }

        materializarPagoLegadoSiCorresponde(orden);
        return registrarPagoInterno(orden, dto.getMonto(), dto.getMetodoPago(), dto.getConcepto(),
                CategoriaMovimiento.ANTICIPO_SERVICIO);
    }

    @Transactional
    public OrdenServicio actualizarEstadoTrabajo(Long ordenId, String estado) {
        OrdenServicio orden = ordenServicioRepository.findDetalleCompletoById(ordenId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        if (orden.getVentaId() != null) {
            throw new RuntimeException("La orden ya fue cerrada como venta y no puede cambiar de estado operativo.");
        }

        String estadoNormalizado = normalizarEstadoSeguimiento(estado);
        orden.setEstado(estadoNormalizado);
        if ("ANULADO".equals(estadoNormalizado)) {
            orden.setRecordatorioActivo(false);
        }
        return ordenServicioRepository.save(orden);
    }

    @Transactional
    public OrdenServicio actualizarPrioridadTrabajo(Long ordenId, String prioridad) {
        OrdenServicio orden = ordenServicioRepository.findDetalleCompletoById(ordenId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        if (orden.getVentaId() != null) {
            throw new RuntimeException("La orden ya fue cerrada como venta y no puede cambiar de prioridad.");
        }

        orden.setPrioridad(normalizarPrioridad(prioridad));
        return ordenServicioRepository.save(orden);
    }

    @Transactional
    public Map<String, Object> finalizarOrden(Long ordenId, boolean cobrarSaldo, String metodoPagoSaldo) {
        OrdenServicio orden = ordenServicioRepository.findDetalleCompletoById(ordenId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        if (orden.getVentaId() != null) {
            return Map.of(
                    "message", "La orden ya fue cerrada anteriormente",
                    "ventaId", orden.getVentaId(),
                    "ordenId", orden.getId(),
                    "saldo", monto(orden.getSaldo())
            );
        }

        materializarPagoLegadoSiCorresponde(orden);
        recalcularMontos(orden);

        if (cobrarSaldo && monto(orden.getSaldo()).compareTo(BigDecimal.ZERO) > 0) {
            registrarPagoInterno(orden, orden.getSaldo(), metodoPagoSaldo, "Saldo final del servicio",
                    CategoriaMovimiento.VENTA);
        }

        recalcularMontos(orden);
        Venta venta = crearVentaDesdeOrden(orden);
        orden.setVentaId(venta.getId());
        orden.setEstado("ENTREGADO");
        orden.setRecordatorioActivo(false);
        ordenServicioRepository.save(orden);

        return Map.of(
                "message", "Orden finalizada y convertida en venta",
                "ventaId", venta.getId(),
                "ordenId", orden.getId(),
                "saldo", monto(orden.getSaldo()),
                "abonado", monto(orden.getACuenta())
        );
    }

    private void mapearCabecera(OrdenServicio orden, OrdenDTO dto) {
        orden.setTipoServicio(servicioCategoriaService.obtenerOCrearTipoParaOrden(dto.getTipoServicio()).getCodigo());
        orden.setTituloTrabajo(texto(dto.getTituloTrabajo()));
        orden.setClienteNombre(texto(dto.getClienteNombre()));
        orden.setClienteTelefono(texto(dto.getClienteTelefono()));
        orden.setClienteDocumento(texto(dto.getClienteDocumento()));
        orden.setClienteEmail(texto(dto.getClienteEmail()));
        orden.setClienteDireccion(texto(dto.getClienteDireccion()));
        orden.setFechaEntregaEstimada(dto.getFechaEntrega());
        orden.setPrioridad(normalizarPrioridad(dto.getPrioridad()));
        orden.setObservaciones(texto(dto.getObservaciones()));
        orden.setFechaRecordatorio(dto.getFechaRecordatorio());
        orden.setRecordatorioNota(texto(dto.getRecordatorioNota()));
        orden.setRecordatorioActivo(Boolean.TRUE.equals(dto.getRecordatorioActivo())
                || dto.getFechaRecordatorio() != null
                || !texto(dto.getRecordatorioNota()).isBlank());
    }

    private OrdenServicioPago registrarPagoInterno(OrdenServicio orden,
                                                   BigDecimal montoSolicitado,
                                                   String metodoPago,
                                                   String concepto,
                                                   String categoriaMovimiento) {
        BigDecimal saldoActual = monto(orden.getTotal()).subtract(totalPagosRegistrados(orden)).max(BigDecimal.ZERO);
        BigDecimal monto = monto(montoSolicitado);
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El monto del abono debe ser mayor a cero.");
        }
        if (saldoActual.compareTo(BigDecimal.ZERO) > 0 && monto.compareTo(saldoActual) > 0) {
            monto = saldoActual;
        }

        MovimientoCaja movimiento = cajaService.registrarMovimientoDetallado(
                "INGRESO",
                construirConceptoPago(orden, concepto),
                monto,
                categoriaMovimiento,
                orden.getId()
        );

        OrdenServicioPago pago = new OrdenServicioPago();
        pago.setOrdenServicio(orden);
        pago.setMonto(monto);
        pago.setMetodoPago(texto(metodoPago).isBlank() ? "EFECTIVO" : texto(metodoPago).toUpperCase());
        pago.setConcepto(texto(concepto).isBlank() ? "Abono de servicio" : texto(concepto));
        pago.setMovimientoCaja(movimiento);
        pago.setUsuario(obtenerUsuarioActual().orElse(null));
        OrdenServicioPago guardado = ordenServicioPagoRepository.save(pago);

        if (orden.getPagos() == null) {
            orden.setPagos(new ArrayList<>());
        }
        orden.getPagos().add(guardado);
        recalcularMontos(orden);
        ordenServicioRepository.save(orden);
        return guardado;
    }

    private BigDecimal totalPagosRegistrados(OrdenServicio orden) {
        if (orden == null || orden.getPagos() == null || orden.getPagos().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return orden.getPagos().stream()
                .map(OrdenServicioPago::getMonto)
                .map(this::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String construirConceptoPago(OrdenServicio orden, String concepto) {
        String base = texto(concepto).isBlank() ? "ABONO SERVICIO" : texto(concepto).toUpperCase();
        return base + " OS-" + String.format("%05d", orden.getId());
    }

    private void materializarPagoLegadoSiCorresponde(OrdenServicio orden) {
        if (orden == null || orden.getId() == null) {
            return;
        }
        if (orden.getPagos() != null && !orden.getPagos().isEmpty()) {
            return;
        }
        BigDecimal abonadoLegacy = monto(orden.getACuenta());
        if (abonadoLegacy.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        OrdenServicioPago legado = new OrdenServicioPago();
        legado.setOrdenServicio(orden);
        legado.setMonto(abonadoLegacy);
        legado.setMetodoPago("EFECTIVO");
        legado.setConcepto("Pago legado migrado desde orden");
        OrdenServicioPago guardado = ordenServicioPagoRepository.save(legado);

        if (orden.getPagos() == null) {
            orden.setPagos(new ArrayList<>());
        }
        orden.getPagos().add(guardado);
        recalcularMontos(orden);
        ordenServicioRepository.save(orden);
    }

    private void recalcularMontos(OrdenServicio orden) {
        BigDecimal total = monto(orden.getTotal());
        BigDecimal abonado = totalPagosRegistrados(orden);
        if (abonado.compareTo(total) > 0) {
            abonado = total;
        }
        orden.setACuenta(abonado.setScale(2, RoundingMode.HALF_UP));
        orden.setSaldo(total.subtract(abonado).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
    }

    private Venta crearVentaDesdeOrden(OrdenServicio orden) {
        Producto productoServicio = productoRepository.findByCodigoInterno(SERVICIO_BASE_CODE)
                .orElseThrow(() -> new RuntimeException("No existe el producto base SERV-001 para cerrar servicios."));
        Cliente cliente = obtenerOCrearClienteDesdeOrden(orden).orElse(null);
        Correlativo correlativo = correlativoRepository.findByCodigoAndSerieWithLock("NOTA_VENTA", "N001")
                .orElseGet(() -> correlativoRepository.save(new Correlativo("NOTA_VENTA", "N001", 0)));

        int numero = correlativo.getUltimoNumero() + 1;
        correlativo.setUltimoNumero(numero);
        correlativoRepository.save(correlativo);

        BigDecimal total = monto(orden.getTotal());
        BigDecimal totalPagado = monto(orden.getACuenta());
        BigDecimal saldoPendiente = total.subtract(totalPagado).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal igvPorcentaje = configuracionService.getIgvPorcentaje();
        BigDecimal factorIgv = BigDecimal.ONE.add(igvPorcentaje.divide(CIEN, 6, RoundingMode.HALF_UP));

        Venta venta = new Venta();
        venta.setTipoComprobante("NOTA_VENTA");
        venta.setSerie(correlativo.getSerie());
        venta.setNumero(numero);
        venta.setFechaEmision(LocalDate.now());
        venta.setFechaVencimiento(saldoPendiente.compareTo(BigDecimal.ZERO) > 0 ? LocalDate.now().plusDays(7) : LocalDate.now());
        venta.setClienteEntity(cliente);
        venta.setClienteDenominacion(texto(orden.getClienteNombre()).isBlank() ? "CLIENTE VARIOS" : texto(orden.getClienteNombre()));
        venta.setClienteNumeroDocumento(texto(orden.getClienteDocumento()));
        venta.setClienteTipoDocumento(tipoDocumentoSunat(orden.getClienteDocumento()));
        venta.setClienteDireccion(texto(orden.getClienteDireccion()));
        venta.setMetodoPago(metodoPagoPrincipal(orden));
        venta.setFormaPago(saldoPendiente.compareTo(BigDecimal.ZERO) > 0 ? "CREDITO" : "CONTADO");
        venta.setEstado(saldoPendiente.compareTo(BigDecimal.ZERO) > 0 ? "EMITIDO" : "PAGADO_TOTAL");
        venta.setMontoPagado(totalPagado);
        venta.setSaldoPendiente(saldoPendiente);
        venta.setCanalVenta("LOCAL");
        venta.setUsuario(obtenerUsuarioActual().orElse(null));

        BigDecimal totalGravada = BigDecimal.ZERO;
        BigDecimal totalIgv = BigDecimal.ZERO;
        List<DetalleVenta> detalles = new ArrayList<>();

        for (OrdenItem item : orden.getItems()) {
            BigDecimal precioFinal = monto(item.getCosto());
            BigDecimal igvItem = precioFinal.multiply(igvPorcentaje)
                    .divide(factorIgv.multiply(CIEN), 2, RoundingMode.HALF_UP);
            BigDecimal valorVenta = precioFinal.subtract(igvItem).setScale(2, RoundingMode.HALF_UP);

            DetalleVenta detalle = new DetalleVenta();
            detalle.setVenta(venta);
            detalle.setProducto(productoServicio);
            detalle.setUnidadMedida("ZZ");
            detalle.setDescripcion(texto(item.getDescripcion()).isBlank() ? texto(orden.getTituloTrabajo()) : texto(item.getDescripcion()));
            detalle.setCantidad(BigDecimal.ONE);
            detalle.setValorUnitario(valorVenta);
            detalle.setPrecioUnitario(precioFinal.setScale(2, RoundingMode.HALF_UP));
            detalle.setSubtotal(precioFinal.setScale(2, RoundingMode.HALF_UP));
            detalle.setCostoUnitario(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            detalle.setUtilidadUnitaria(precioFinal.setScale(2, RoundingMode.HALF_UP));
            detalle.setUtilidadTotal(precioFinal.setScale(2, RoundingMode.HALF_UP));
            detalle.setTipoItem("SERVICIO");
            detalle.setCategoriaServicio(texto(orden.getTipoServicio()).isBlank() ? "SERVICIO" : texto(orden.getTipoServicio()).toUpperCase());
            detalle.setPorcentajeIgv(igvPorcentaje.setScale(2, RoundingMode.HALF_UP));
            detalle.setCodigoTipoAfectacionIgv("10");
            detalles.add(detalle);

            totalGravada = totalGravada.add(valorVenta);
            totalIgv = totalIgv.add(igvItem);
        }

        venta.setItems(detalles);
        venta.setTotal(total.setScale(2, RoundingMode.HALF_UP));
        venta.setTotalGravada(totalGravada.setScale(2, RoundingMode.HALF_UP));
        venta.setTotalIgv(totalIgv.setScale(2, RoundingMode.HALF_UP));

        Venta guardada = ventaRepository.save(venta);

        for (OrdenServicioPago pago : orden.getPagos()) {
            Amortizacion amortizacion = new Amortizacion();
            amortizacion.setVenta(guardada);
            amortizacion.setMonto(monto(pago.getMonto()));
            amortizacion.setMetodoPago(texto(pago.getMetodoPago()).isBlank() ? "EFECTIVO" : texto(pago.getMetodoPago()).toUpperCase());
            amortizacion.setObservacion(texto(pago.getConcepto()).isBlank() ? "Pago servicio OS-" + orden.getId() : texto(pago.getConcepto()));
            amortizacion.setFechaPago(pago.getFecha());
            amortizacionRepository.save(amortizacion);
        }

        if (cliente != null && cliente.getId() != null) {
            clienteService.recalcularSaldoDeudor(cliente.getId());
        }

        return guardada;
    }

    private Optional<Cliente> obtenerOCrearClienteDesdeOrden(OrdenServicio orden) {
        String documento = texto(orden.getClienteDocumento()).replaceAll("\\s+", "");
        if (documento.isBlank()) {
            return Optional.empty();
        }
        return clienteRepository.findByNumeroDocumento(documento)
                .map(Optional::of)
                .orElseGet(() -> {
                    Cliente cliente = new Cliente();
                    cliente.setNumeroDocumento(documento);
                    cliente.setTipoDocumento(tipoDocumentoSunat(documento));
                    cliente.setNombreRazonSocial(texto(orden.getClienteNombre()).isBlank() ? "CLIENTE SERVICIO" : texto(orden.getClienteNombre()));
                    cliente.setDireccion(texto(orden.getClienteDireccion()));
                    cliente.setTelefono(texto(orden.getClienteTelefono()));
                    cliente.setEmail(texto(orden.getClienteEmail()));
                    cliente.setActivo(true);
                    return Optional.of(clienteRepository.save(cliente));
                });
    }

    private String tipoDocumentoSunat(String documento) {
        String valor = texto(documento).trim();
        if (valor.length() == 11) {
            return "6";
        }
        if (valor.length() == 8) {
            return "1";
        }
        return "0";
    }

    private String metodoPagoPrincipal(OrdenServicio orden) {
        if (orden.getPagos() == null || orden.getPagos().isEmpty()) {
            return "EFECTIVO";
        }
        return orden.getPagos().get(orden.getPagos().size() - 1).getMetodoPago();
    }

    private Optional<Usuario> obtenerUsuarioActual() {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return usuarioRepository.findByUsername(username);
    }

    private BigDecimal limitarPagoInicial(BigDecimal adelanto, BigDecimal total) {
        BigDecimal monto = monto(adelanto);
        BigDecimal totalSeguro = monto(total);
        if (monto.compareTo(totalSeguro) > 0) {
            return totalSeguro;
        }
        return monto;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String normalizarEstadoSeguimiento(String valor) {
        return switch (texto(valor).toUpperCase()) {
            case "PENDIENTE", "EN_PROCESO", "LISTO", "ANULADO" -> texto(valor).toUpperCase();
            default -> throw new RuntimeException("Estado de trabajo no valido.");
        };
    }

    private String normalizarPrioridad(String valor) {
        return switch (texto(valor).toUpperCase()) {
            case "BAJA", "NORMAL", "ALTA", "URGENTE" -> texto(valor).toUpperCase();
            default -> "NORMAL";
        };
    }

    private BigDecimal monto(BigDecimal valor) {
        return valor != null ? valor.setScale(2, RoundingMode.HALF_UP) : CERO;
    }
}
