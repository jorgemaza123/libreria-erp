package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.CotizacionDTO;
import com.libreria.sistema.repository.*;
import com.libreria.sistema.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class CotizacionService {

    private final CotizacionRepository cotizacionRepository;
    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final KardexRepository kardexRepository;
    private final UsuarioRepository usuarioRepository;
    private final CorrelativoRepository correlativoRepository;
    private final AmortizacionRepository amortizacionRepository;
    private final ServicioCategoriaRepository servicioCategoriaRepository;
    private final CajaService cajaService;
    private final ConfiguracionService configuracionService;

    public CotizacionService(CotizacionRepository cotizacionRepository,
                             ProductoRepository productoRepository,
                             VentaRepository ventaRepository,
                             MovimientoCajaRepository movimientoCajaRepository,
                             KardexRepository kardexRepository,
                             UsuarioRepository usuarioRepository,
                             CorrelativoRepository correlativoRepository,
                             AmortizacionRepository amortizacionRepository,
                             ServicioCategoriaRepository servicioCategoriaRepository,
                             CajaService cajaService,
                             ConfiguracionService configuracionService) {
        this.cotizacionRepository = cotizacionRepository;
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.kardexRepository = kardexRepository;
        this.usuarioRepository = usuarioRepository;
        this.correlativoRepository = correlativoRepository;
        this.amortizacionRepository = amortizacionRepository;
        this.servicioCategoriaRepository = servicioCategoriaRepository;
        this.cajaService = cajaService;
        this.configuracionService = configuracionService;
    }

    // =====================================================
    //  CRUD
    // =====================================================

    public Cotizacion obtenerPorId(Long id) {
        return cotizacionRepository.findById(id).orElse(null);
    }

    public Page<Cotizacion> buscarFiltrado(String termino, String estado, String fechaDesde, String fechaHasta, Pageable pageable) {
        String terminoParam = (termino != null && !termino.trim().isEmpty()) ? termino.trim() : null;
        String estadoParam = (estado != null && !estado.trim().isEmpty()) ? estado.trim() : null;
        String desdeParam = (fechaDesde != null && !fechaDesde.trim().isEmpty()) ? fechaDesde.trim() + " 00:00:00" : null;
        String hastaParam = (fechaHasta != null && !fechaHasta.trim().isEmpty()) ? fechaHasta.trim() + " 23:59:59" : null;
        return cotizacionRepository.buscarFiltrado(terminoParam, estadoParam, desdeParam, hastaParam, pageable);
    }

    @Transactional
    public Cotizacion crearCotizacion(CotizacionDTO dto) throws Exception {
        Cotizacion c = new Cotizacion();

        // Correlativo
        Correlativo correlativo = correlativoRepository.findByCodigoAndSerieWithLock("COTIZACION", "C001")
                .orElseGet(() -> {
                    Correlativo nuevo = new Correlativo("COTIZACION", "C001", 0);
                    return correlativoRepository.save(nuevo);
                });
        Integer ultimoActual = correlativo.getUltimoNumero();
        int nuevoNumero = (ultimoActual != null ? ultimoActual : 0) + 1;
        correlativo.setUltimoNumero(nuevoNumero);
        correlativoRepository.save(correlativo);

        c.setSerie("C001");
        c.setNumero(nuevoNumero);
        c.setClienteDocumento(dto.getClienteDocumento());
        c.setClienteNombre(dto.getClienteNombre());
        c.setClienteTelefono(dto.getClienteTelefono());
        c.setObservaciones(dto.getObservaciones());
        c.setCondiciones(dto.getCondiciones());

        // Forma de pago
        c.setFormaPago(dto.getFormaPago() != null ? dto.getFormaPago() : "CONTADO");
        c.setMetodoPago(dto.getMetodoPago());
        c.setDiasCredito(dto.getDiasCredito());

        if ("CREDITO".equals(c.getFormaPago()) && dto.getDiasCredito() != null) {
            c.setFechaVencimiento(LocalDate.now().plusDays(dto.getDiasCredito()));
        }

        // Estado
        c.setEstado(dto.getEstado() != null ? dto.getEstado() : "EMITIDA");

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        usuarioRepository.findByUsername(username).ifPresent(c::setUsuario);

        // Procesar items
        BigDecimal totalBruto = BigDecimal.ZERO;
        BigDecimal igvFactor = configuracionService.getIgvFactor();

        for (CotizacionDTO.ItemDTO item : dto.getItems()) {
            DetalleCotizacion det = new DetalleCotizacion();
            det.setCotizacion(c);
            det.setTipoItem(item.getTipoItem() != null ? item.getTipoItem() : "PRODUCTO");
            det.setCategoriaServicio(item.getCategoriaServicio());
            det.setCantidad(item.getCantidad());
            det.setPrecioUnitario(item.getPrecioUnitario());
            det.setCostoEstimado(item.getCostoEstimado());
            det.setParametrosJson(item.getParametrosJson());
            det.setSubtotal(item.getCantidad().multiply(item.getPrecioUnitario()));

            if ("SERVICIO".equals(det.getTipoItem())) {
                det.setDescripcion(item.getDescripcion() != null ? item.getDescripcion() : "SERVICIO");
                det.setProducto(null);
            } else {
                Producto p = productoRepository.findById(item.getProductoId())
                        .orElseThrow(() -> new Exception("Producto no encontrado: ID " + item.getProductoId()));
                det.setProducto(p);
                det.setDescripcion(item.getDescripcion() != null ? item.getDescripcion() : p.getNombre());
            }

            c.getItems().add(det);
            totalBruto = totalBruto.add(det.getSubtotal());
        }

        // Calcular totales fiscales
        BigDecimal descuento = dto.getDescuento() != null ? dto.getDescuento() : BigDecimal.ZERO;
        BigDecimal totalConDescuento = totalBruto.subtract(descuento);
        BigDecimal subtotal = totalConDescuento.divide(igvFactor, 2, RoundingMode.HALF_UP);
        BigDecimal igv = totalConDescuento.subtract(subtotal);

        c.setSubtotal(subtotal);
        c.setIgv(igv);
        c.setDescuento(descuento);
        c.setTotal(totalConDescuento);

        // Pago
        BigDecimal montoInicial = dto.getMontoInicial() != null ? dto.getMontoInicial() : BigDecimal.ZERO;
        c.setMontoInicial(montoInicial);
        if ("CREDITO".equals(c.getFormaPago())) {
            c.setSaldoPendiente(totalConDescuento.subtract(montoInicial));
        } else {
            c.setSaldoPendiente(BigDecimal.ZERO);
        }

        return cotizacionRepository.save(c);
    }

    @Transactional
    public void actualizarCotizacion(Long id, CotizacionDTO dto) throws Exception {
        Cotizacion c = cotizacionRepository.findById(id)
                .orElseThrow(() -> new Exception("Cotización no encontrada"));

        if (!esEditable(c.getEstado())) {
            throw new Exception("No se puede editar una cotización en estado " + c.getEstado());
        }

        c.setClienteDocumento(dto.getClienteDocumento());
        c.setClienteNombre(dto.getClienteNombre());
        c.setClienteTelefono(dto.getClienteTelefono());
        c.setObservaciones(dto.getObservaciones());
        c.setCondiciones(dto.getCondiciones());
        c.setFormaPago(dto.getFormaPago() != null ? dto.getFormaPago() : "CONTADO");
        c.setMetodoPago(dto.getMetodoPago());
        c.setDiasCredito(dto.getDiasCredito());

        if ("CREDITO".equals(c.getFormaPago()) && dto.getDiasCredito() != null) {
            c.setFechaVencimiento(LocalDate.now().plusDays(dto.getDiasCredito()));
        }

        if (dto.getEstado() != null) {
            c.setEstado(dto.getEstado());
        }

        c.getItems().clear();

        BigDecimal totalBruto = BigDecimal.ZERO;
        BigDecimal igvFactor = configuracionService.getIgvFactor();

        for (CotizacionDTO.ItemDTO item : dto.getItems()) {
            DetalleCotizacion det = new DetalleCotizacion();
            det.setCotizacion(c);
            det.setTipoItem(item.getTipoItem() != null ? item.getTipoItem() : "PRODUCTO");
            det.setCategoriaServicio(item.getCategoriaServicio());
            det.setCantidad(item.getCantidad());
            det.setPrecioUnitario(item.getPrecioUnitario());
            det.setCostoEstimado(item.getCostoEstimado());
            det.setParametrosJson(item.getParametrosJson());
            det.setSubtotal(item.getCantidad().multiply(item.getPrecioUnitario()));

            if ("SERVICIO".equals(det.getTipoItem())) {
                det.setDescripcion(item.getDescripcion() != null ? item.getDescripcion() : "SERVICIO");
                det.setProducto(null);
            } else {
                Producto p = productoRepository.findById(item.getProductoId())
                        .orElseThrow(() -> new Exception("Producto no encontrado"));
                det.setProducto(p);
                det.setDescripcion(item.getDescripcion() != null ? item.getDescripcion() : p.getNombre());
            }

            c.getItems().add(det);
            totalBruto = totalBruto.add(det.getSubtotal());
        }

        BigDecimal descuento = dto.getDescuento() != null ? dto.getDescuento() : BigDecimal.ZERO;
        BigDecimal totalConDescuento = totalBruto.subtract(descuento);
        BigDecimal subtotal = totalConDescuento.divide(igvFactor, 2, RoundingMode.HALF_UP);
        BigDecimal igv = totalConDescuento.subtract(subtotal);

        c.setSubtotal(subtotal);
        c.setIgv(igv);
        c.setDescuento(descuento);
        c.setTotal(totalConDescuento);

        BigDecimal montoInicial = dto.getMontoInicial() != null ? dto.getMontoInicial() : BigDecimal.ZERO;
        c.setMontoInicial(montoInicial);
        if ("CREDITO".equals(c.getFormaPago())) {
            c.setSaldoPendiente(totalConDescuento.subtract(montoInicial));
        } else {
            c.setSaldoPendiente(BigDecimal.ZERO);
        }

        cotizacionRepository.save(c);
    }

    // =====================================================
    //  CONVERSIÓN A VENTA
    // =====================================================

    @Transactional
    public Long convertirAVenta(Long cotizacionId, String tipoComprobante) throws Exception {
        Cotizacion c = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new Exception("Cotización no encontrada"));

        if (!esConvertible(c.getEstado())) {
            throw new Exception("Esta cotización no puede ser convertida (estado: " + c.getEstado() + ")");
        }

        SesionCaja sesionActiva = cajaService.obtenerSesionActiva()
                .orElseThrow(() -> new Exception("CAJA CERRADA: Debe abrir caja antes de generar ventas."));

        // Buscar SERV-001 por código interno (NO por ID hardcodeado)
        Producto productoServicio = productoRepository.findByCodigoInterno("SERV-001").orElse(null);

        Venta v = new Venta();
        v.setClienteNumeroDocumento(c.getClienteDocumento());
        v.setClienteDenominacion(c.getClienteNombre());
        v.setClienteTipoDocumento(tipoComprobante.equals("FACTURA") ? "6" : "1");

        String serie = tipoComprobante.equals("FACTURA") ? "F001" : "B001";

        Correlativo correlativo = correlativoRepository.findByCodigoAndSerieWithLock(tipoComprobante, serie)
                .orElseGet(() -> {
                    Correlativo nuevo = new Correlativo(tipoComprobante, serie, 0);
                    return correlativoRepository.save(nuevo);
                });
        Integer ultimoActual = correlativo.getUltimoNumero();
        int nuevoNumero = (ultimoActual != null ? ultimoActual : 0) + 1;
        correlativo.setUltimoNumero(nuevoNumero);
        correlativoRepository.save(correlativo);

        v.setTipoComprobante(tipoComprobante);
        v.setSerie(serie);
        v.setNumero(nuevoNumero);
        v.setFechaEmision(LocalDate.now());
        v.setMoneda("PEN");
        v.setTipoOperacion("0101");
        v.setEstado("EMITIDO");
        v.setUsuario(c.getUsuario());

        // Forma de pago
        boolean esCredito = "CREDITO".equals(c.getFormaPago());
        v.setFormaPago(esCredito ? "Crédito" : "Contado");
        v.setMetodoPago(c.getMetodoPago() != null ? c.getMetodoPago() : "EFECTIVO");

        if (esCredito) {
            v.setFechaVencimiento(c.getDiasCredito() != null
                    ? LocalDate.now().plusDays(c.getDiasCredito()) : LocalDate.now().plusDays(30));
        } else {
            v.setFechaVencimiento(LocalDate.now());
        }

        BigDecimal totalVenta = BigDecimal.ZERO;
        BigDecimal totalGravada = BigDecimal.ZERO;
        BigDecimal totalIgv = BigDecimal.ZERO;

        BigDecimal igvFactor = configuracionService.getIgvFactor();
        BigDecimal igvPorcentaje = configuracionService.getIgvPorcentaje();

        for (DetalleCotizacion itemCoti : c.getItems()) {
            boolean esServicioItem = "SERVICIO".equals(itemCoti.getTipoItem()) || itemCoti.getProducto() == null;
            Producto p;

            if (esServicioItem) {
                p = productoServicio;
                if (p == null) {
                    throw new Exception("Producto SERV-001 no encontrado. Ejecute DataInitializer.");
                }
            } else {
                p = itemCoti.getProducto();

                // Validar stock
                int stockActual = p.getStockActual() != null ? p.getStockActual() : 0;
                if (stockActual < itemCoti.getCantidad().intValue()) {
                    throw new Exception("Stock insuficiente: " + p.getNombre());
                }

                p.setStockActual(stockActual - itemCoti.getCantidad().intValue());
                productoRepository.save(p);

                Kardex k = new Kardex();
                k.setProducto(p);
                k.setTipo("SALIDA");
                k.setMotivo("VENTA COTIZ. " + v.getSerie() + "-" + v.getNumero());
                k.setCantidad(itemCoti.getCantidad().intValue());
                k.setStockAnterior(stockActual);
                k.setStockActual(p.getStockActual());
                kardexRepository.save(k);
            }

            DetalleVenta dv = new DetalleVenta();
            dv.setVenta(v);
            dv.setProducto(p);
            dv.setDescripcion(itemCoti.getDescripcion());
            dv.setCantidad(itemCoti.getCantidad());
            dv.setUnidadMedida("NIU");

            // Propagar tipo y categoría para rentabilidad post-conversión
            dv.setTipoItem(itemCoti.getTipoItem());
            dv.setCategoriaServicio(itemCoti.getCategoriaServicio());

            BigDecimal precioFinal = itemCoti.getPrecioUnitario();
            BigDecimal cantidad = itemCoti.getCantidad();
            BigDecimal subtotal = precioFinal.multiply(cantidad);

            BigDecimal valorUnitario = precioFinal.divide(igvFactor, 2, RoundingMode.HALF_UP);
            BigDecimal valorVentaItem = valorUnitario.multiply(cantidad);
            BigDecimal igvItem = subtotal.subtract(valorVentaItem);

            dv.setPrecioUnitario(precioFinal);
            dv.setValorUnitario(valorUnitario);
            dv.setSubtotal(subtotal);

            // Congelar costo al momento de la conversión
            BigDecimal costoUnit = p != null && p.getPrecioCompra() != null ? p.getPrecioCompra() : BigDecimal.ZERO;
            dv.setCostoUnitario(costoUnit);
            dv.setUtilidadUnitaria(precioFinal.subtract(costoUnit));
            dv.setUtilidadTotal(precioFinal.subtract(costoUnit).multiply(cantidad));

            dv.setPorcentajeIgv(igvPorcentaje);
            dv.setCodigoTipoAfectacionIgv(Constants.AFECTACION_GRAVADO);

            v.getItems().add(dv);
            totalVenta = totalVenta.add(subtotal);
            totalGravada = totalGravada.add(valorVentaItem);
            totalIgv = totalIgv.add(igvItem);
        }

        v.setTotal(totalVenta);
        v.setTotalGravada(totalGravada);
        v.setTotalIgv(totalIgv);

        // Pago
        BigDecimal montoInicial = c.getMontoInicial() != null ? c.getMontoInicial() : BigDecimal.ZERO;
        if (esCredito) {
            v.setMontoPagado(montoInicial);
            v.setSaldoPendiente(totalVenta.subtract(montoInicial));
        } else {
            v.setMontoPagado(totalVenta);
            v.setSaldoPendiente(BigDecimal.ZERO);
        }

        ventaRepository.save(v);

        // Amortización inicial si es crédito con adelanto
        if (esCredito && montoInicial.compareTo(BigDecimal.ZERO) > 0) {
            Amortizacion amort = new Amortizacion();
            amort.setVenta(v);
            amort.setMonto(montoInicial);
            amort.setMetodoPago(c.getMetodoPago() != null ? c.getMetodoPago() : "EFECTIVO");
            amort.setObservacion("Adelanto inicial - Cotización " + c.getSerie() + "-" + c.getNumero());
            amortizacionRepository.save(amort);
        }

        // Actualizar cotización
        c.setEstado("CONVERTIDA");
        c.setVentaId(v.getId());
        cotizacionRepository.save(c);

        // Registrar en caja (solo lo efectivamente cobrado)
        BigDecimal montoCaja = esCredito ? montoInicial : totalVenta;
        if (montoCaja.compareTo(BigDecimal.ZERO) > 0) {
            MovimientoCaja caja = new MovimientoCaja();
            caja.setFecha(LocalDateTime.now());
            caja.setTipo("INGRESO");
            caja.setConcepto("VENTA COTIZ. " + v.getSerie() + "-" + v.getNumero());
            caja.setMonto(montoCaja);
            caja.setSesion(sesionActiva);
            caja.setUsuario(c.getUsuario());
            movimientoCajaRepository.save(caja);
        }

        return v.getId();
    }

    // =====================================================
    //  ESTADOS
    // =====================================================

    @Transactional
    public void cambiarEstado(Long id, String nuevoEstado) throws Exception {
        Cotizacion c = cotizacionRepository.findById(id)
                .orElseThrow(() -> new Exception("Cotización no encontrada"));

        if ("CONVERTIDA".equals(c.getEstado()) || "CONVERTIDO_VENTA".equals(c.getEstado())) {
            throw new Exception("No se puede cambiar el estado de una cotización ya convertida");
        }

        c.setEstado(nuevoEstado);
        cotizacionRepository.save(c);
    }

    @Transactional
    public void anularCotizacion(Long id) throws Exception {
        Cotizacion c = cotizacionRepository.findById(id)
                .orElseThrow(() -> new Exception("Cotización no encontrada"));

        if ("CONVERTIDA".equals(c.getEstado()) || "CONVERTIDO_VENTA".equals(c.getEstado())) {
            throw new Exception("No se puede anular una cotización ya convertida en venta");
        }

        c.setEstado("ANULADA");
        cotizacionRepository.save(c);
    }

    // =====================================================
    //  KPIs
    // =====================================================

    public Map<String, Object> obtenerKPIs() {
        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime ahora = LocalDateTime.now();

        long totalMes = cotizacionRepository.countByFechaCreacionBetween(inicioMes, ahora);
        BigDecimal montoCotizadoMes = cotizacionRepository.sumTotalByFechaCreacionBetween(inicioMes, ahora);

        List<String> estadosPendientes = Arrays.asList("EMITIDA", "EMITIDO", "ENVIADA", "APROBADA", "EN_NEGOCIACION");
        long pendientes = cotizacionRepository.countByEstadoIn(estadosPendientes);

        List<String> estadosConvertidos = Arrays.asList("CONVERTIDA", "CONVERTIDO_VENTA");
        long convertidasMes = cotizacionRepository.countByEstadoInAndFechaCreacionBetween(estadosConvertidos, inicioMes, ahora);

        double porcentajeConversion = totalMes > 0 ? (double) convertidasMes / totalMes * 100 : 0;

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("totalMes", totalMes);
        kpis.put("montoCotizadoMes", montoCotizadoMes);
        kpis.put("pendientes", pendientes);
        kpis.put("porcentajeConversion", Math.round(porcentajeConversion * 10.0) / 10.0);
        return kpis;
    }

    public List<Map<String, Object>> obtenerRentabilidadServicios(String desde, String hasta) {
        List<Object[]> results = cotizacionRepository.rentabilidadPorCategoriaServicio(desde, hasta);
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("categoria", row[0] != null ? row[0].toString() : "SIN CATEGORÍA");
            item.put("ingresos", row[1]);
            item.put("costos", row[2]);
            item.put("margen", row[3]);
            item.put("cantidad", row[4]);
            lista.add(item);
        }
        return lista;
    }

    public List<Map<String, Object>> obtenerTendenciaMensual() {
        String desde = LocalDate.now().minusMonths(11).withDayOfMonth(1).toString() + " 00:00:00";
        List<Object[]> results = cotizacionRepository.tendenciaMensual(desde);
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("mes", row[0]);
            item.put("totalCotizaciones", row[1]);
            item.put("montoTotal", row[2]);
            item.put("convertidas", row[3]);
            lista.add(item);
        }
        return lista;
    }

    public List<Map<String, Object>> obtenerConversionPorCategoria(String desde, String hasta) {
        List<Object[]> results = cotizacionRepository.conversionPorCategoria(desde, hasta);
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("categoria", row[0] != null ? row[0].toString() : "SIN CATEGORÍA");
            long total = ((Number) row[1]).longValue();
            long convertidas = ((Number) row[2]).longValue();
            item.put("totalCotizaciones", total);
            item.put("convertidas", convertidas);
            item.put("tasaConversion", total > 0 ? Math.round((double) convertidas / total * 1000.0) / 10.0 : 0);
            lista.add(item);
        }
        return lista;
    }

    // Rentabilidad desde ventas reales (post-conversión)
    public List<Map<String, Object>> obtenerRentabilidadVentas(LocalDate desde, LocalDate hasta) {
        List<Object[]> results = cotizacionRepository.rentabilidadServiciosVentas(desde, hasta);
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("categoria", row[0] != null ? row[0].toString() : "SIN CATEGORÍA");
            item.put("ingresos", row[1]);
            item.put("cantidad", row[2]);
            lista.add(item);
        }
        return lista;
    }

    public List<ServicioCategoria> obtenerCategoriasActivas() {
        return servicioCategoriaRepository.findByActivaTrueOrderByOrdenAsc();
    }

    // =====================================================
    //  SCHEDULER: VENCIMIENTO AUTOMÁTICO
    // =====================================================

    @Scheduled(cron = "0 0 1 * * ?") // Todos los días a la 1:00 AM
    @Transactional
    public void vencerCotizaciones() {
        List<String> estadosActivos = Arrays.asList("EMITIDA", "EMITIDO", "ENVIADA", "APROBADA", "EN_NEGOCIACION");
        List<Cotizacion> vencidas = cotizacionRepository
                .findByFechaVencimientoBeforeAndEstadoIn(LocalDate.now(), estadosActivos);

        for (Cotizacion c : vencidas) {
            c.setEstado("VENCIDA");
            cotizacionRepository.save(c);
        }

        if (!vencidas.isEmpty()) {
            log.info("Se vencieron {} cotizaciones automáticamente", vencidas.size());
        }
    }

    // =====================================================
    //  EXPORT EXCEL
    // =====================================================

    public byte[] exportarExcel(String termino, String estado, String fechaDesde, String fechaHasta) throws IOException {
        Pageable pageable = PageRequest.of(0, 10000, Sort.by("id").descending());
        Page<Cotizacion> page = buscarFiltrado(termino, estado, fechaDesde, fechaHasta, pageable);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Cotizaciones");

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"N° Cotización", "Fecha", "Cliente", "Documento", "Forma Pago", "Subtotal", "IGV", "Total", "Estado"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            int rowNum = 1;
            for (Cotizacion c : page.getContent()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(c.getSerie() + "-" + String.format("%06d", c.getNumero()));
                row.createCell(1).setCellValue(c.getFechaEmision() != null ? c.getFechaEmision().format(fmt) : "");
                row.createCell(2).setCellValue(c.getClienteNombre() != null ? c.getClienteNombre() : "");
                row.createCell(3).setCellValue(c.getClienteDocumento() != null ? c.getClienteDocumento() : "");
                row.createCell(4).setCellValue(c.getFormaPago() != null ? c.getFormaPago() : "CONTADO");
                row.createCell(5).setCellValue(c.getSubtotal() != null ? c.getSubtotal().doubleValue() : 0);
                row.createCell(6).setCellValue(c.getIgv() != null ? c.getIgv().doubleValue() : 0);
                row.createCell(7).setCellValue(c.getTotal() != null ? c.getTotal().doubleValue() : 0);
                row.createCell(8).setCellValue(c.getEstado() != null ? c.getEstado() : "");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    // =====================================================
    //  HELPERS
    // =====================================================

    private boolean esEditable(String estado) {
        return estado == null || "BORRADOR".equals(estado) || "EMITIDA".equals(estado)
                || "EMITIDO".equals(estado) || "EN_NEGOCIACION".equals(estado);
    }

    private boolean esConvertible(String estado) {
        return "EMITIDA".equals(estado) || "EMITIDO".equals(estado)
                || "ENVIADA".equals(estado) || "APROBADA".equals(estado) || "EN_NEGOCIACION".equals(estado);
    }
}
