package com.libreria.sistema.service;

import com.libreria.sistema.model.ConfigCuentaFija;
import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.Compra;
import com.libreria.sistema.model.DetalleCompra;
import com.libreria.sistema.model.HistorialPrecioProducto;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Proveedor;
import com.libreria.sistema.model.dto.CosteoProductoDTO;
import com.libreria.sistema.model.dto.CosteoVentaSnapshotDTO;
import com.libreria.sistema.repository.ConfigCuentaFijaRepository;
import com.libreria.sistema.repository.DetalleCompraRepository;
import com.libreria.sistema.repository.DetalleVentaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class CosteoEmpresarialService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_UNIDADES_MES = BigDecimal.valueOf(300);
    private static final BigDecimal DEFAULT_MINUTOS_MES = BigDecimal.valueOf(26L * 10L * 60L);
    private static final BigDecimal DEFAULT_IMPRESIONES_MES = BigDecimal.valueOf(1200);

    private final ConfigCuentaFijaRepository cuentaFijaRepository;
    private final ProductoRepository productoRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final DetalleCompraRepository detalleCompraRepository;
    private final ConfiguracionService configuracionService;
    private final CosteoCalculatorService costeoCalculatorService;
    private final HistorialPrecioProductoService historialPrecioProductoService;

    public CosteoEmpresarialService(ConfigCuentaFijaRepository cuentaFijaRepository,
                                    ProductoRepository productoRepository,
                                    DetalleVentaRepository detalleVentaRepository,
                                    DetalleCompraRepository detalleCompraRepository,
                                    ConfiguracionService configuracionService,
                                    CosteoCalculatorService costeoCalculatorService,
                                    HistorialPrecioProductoService historialPrecioProductoService) {
        this.cuentaFijaRepository = cuentaFijaRepository;
        this.productoRepository = productoRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.detalleCompraRepository = detalleCompraRepository;
        this.configuracionService = configuracionService;
        this.costeoCalculatorService = costeoCalculatorService;
        this.historialPrecioProductoService = historialPrecioProductoService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerResumen() {
        List<CosteoProductoDTO> productos = analizarProductos();
        List<ConfigCuentaFija> cuentas = cuentasCosteo();

        BigDecimal totalMensualCosteo = cuentas.stream()
                .map(this::montoCosteoMensual)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long productosBajoPrecio = productos.stream()
                .filter(p -> "SUBIR".equals(p.getEstadoPrecio()) || "PERDIDA".equals(p.getEstadoPrecio()))
                .count();
        long productosSinCosto = productos.stream()
                .filter(p -> p.getCostoDirectoUnitario().compareTo(BigDecimal.ZERO) <= 0)
                .count();
        BigDecimal diferenciaNecesaria = productos.stream()
                .map(CosteoProductoDTO::getDiferenciaPrecio)
                .filter(Objects::nonNull)
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalMensualCosteo", totalMensualCosteo);
        out.put("cuentasCosteo", cuentas.size());
        out.put("productosAnalizados", productos.size());
        out.put("productosBajoPrecio", productosBajoPrecio);
        out.put("productosSinCosto", productosSinCosto);
        out.put("diferenciaNecesaria", diferenciaNecesaria);
        return out;
    }

    @Transactional(readOnly = true)
    public List<CosteoProductoDTO> analizarProductos() {
        CosteoContext context = construirContexto();
        return productoRepository.findByActivoTrueOrderByNombreAsc().stream()
                .filter(p -> !p.esLamina())
                .map(producto -> analizarProducto(producto, context))
                .toList();
    }

    @Transactional(readOnly = true)
    public CosteoProductoDTO analizarProducto(Long productoId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        return analizarProducto(producto, construirContexto());
    }

    public CosteoProductoDTO analizarProducto(Producto producto) {
        return analizarProducto(producto, construirContexto());
    }

    private CosteoProductoDTO analizarProducto(Producto producto, CosteoContext context) {
        Configuracion config = context.configuracion();
        CosteoBreakdown breakdown = calcularCostoIndirecto(producto, context);
        BigDecimal costoDirecto = positivo(producto.getPrecioCompra()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal costoIndirecto = breakdown.total().setScale(4, RoundingMode.HALF_UP);
        BigDecimal costoTotal = costoDirecto.add(costoIndirecto).setScale(4, RoundingMode.HALF_UP);
        BigDecimal gananciaObjetivo = resolverGananciaObjetivo(producto, config);
        BigDecimal gananciaMinima = resolverGananciaMinima(producto, config);
        BigDecimal precioMinimo = aplicarMargen(costoTotal, gananciaMinima).setScale(2, RoundingMode.HALF_UP);
        BigDecimal precioSugerido = costeoCalculatorService.aplicarRedondeoRetail(
                aplicarMargen(costoTotal, gananciaObjetivo).setScale(2, RoundingMode.HALF_UP), config);
        BigDecimal precioActual = positivo(producto.getPrecioVenta()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal diferencia = precioSugerido.subtract(precioActual).setScale(2, RoundingMode.HALF_UP);
        BigDecimal margenActual = precioActual.compareTo(BigDecimal.ZERO) > 0
                ? porcentaje(precioActual.subtract(costoTotal), precioActual, 2)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        String estado = estadoPrecio(precioActual, costoTotal, precioMinimo, precioSugerido);
        String faltantes = datosFaltantes(producto, breakdown, context);

        return CosteoProductoDTO.builder()
                .productoId(producto.getId())
                .codigoInterno(producto.getCodigoInterno())
                .nombre(producto.getNombre())
                .categoria(producto.getCategoria())
                .tipo(producto.getTipo())
                .stockActual(producto.getStockActual())
                .reglaCosteo(texto(producto.getReglaCosteo(), "AUTO"))
                .unidadesEstimadasMes(positivo(producto.getUnidadesEstimadasMes()))
                .minutosTrabajoUnidad(positivo(producto.getMinutosTrabajoUnidad()))
                .impresionesEquivalentesUnidad(positivo(producto.getImpresionesEquivalentesUnidad()))
                .usarReglaManualPrecio(Boolean.TRUE.equals(producto.getUsarReglaManualPrecio()))
                .costoDirectoUnitario(costoDirecto)
                .costoIndirectoUnitario(costoIndirecto)
                .costoTotalUnitario(costoTotal)
                .gananciaMinimaPct(gananciaMinima)
                .gananciaObjetivoPct(gananciaObjetivo)
                .precioMinimo(precioMinimo)
                .precioSugerido(precioSugerido)
                .precioVentaActual(precioActual)
                .diferenciaPrecio(diferencia)
                .margenActualPct(margenActual)
                .estadoPrecio(estado)
                .recomendacion(recomendacion(estado, diferencia))
                .reglaResumen(breakdown.resumenLegible())
                .datosFaltantes(faltantes)
                .fechaUltimoCosteo(producto.getFechaUltimoCosteo())
                .build();
    }

    public CosteoVentaSnapshotDTO snapshotParaVenta(Producto producto, BigDecimal precioVenta, BigDecimal cantidad) {
        CosteoContext context = construirContexto();
        CosteoBreakdown breakdown = calcularCostoIndirecto(producto, context);
        Configuracion config = context.configuracion();
        BigDecimal precio = positivo(precioVenta).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cant = positivo(cantidad).setScale(3, RoundingMode.HALF_UP);
        BigDecimal costoDirecto = positivo(producto.getPrecioCompra()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal costoIndirecto = breakdown.total().setScale(4, RoundingMode.HALF_UP);
        BigDecimal costoTotal = costoDirecto.add(costoIndirecto).setScale(4, RoundingMode.HALF_UP);
        BigDecimal gananciaMinima = resolverGananciaMinima(producto, config);
        BigDecimal gananciaObjetivo = resolverGananciaObjetivo(producto, config);
        BigDecimal precioMinimo = aplicarMargen(costoTotal, gananciaMinima).setScale(2, RoundingMode.HALF_UP);
        BigDecimal precioSugerido = costeoCalculatorService.aplicarRedondeoRetail(
                aplicarMargen(costoTotal, gananciaObjetivo).setScale(2, RoundingMode.HALF_UP), config);
        BigDecimal utilidadBrutaUnit = precio.subtract(costoDirecto).setScale(2, RoundingMode.HALF_UP);
        BigDecimal utilidadBrutaTotal = utilidadBrutaUnit.multiply(cant).setScale(2, RoundingMode.HALF_UP);
        BigDecimal utilidadNetaUnit = precio.subtract(costoTotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal utilidadNetaTotal = utilidadNetaUnit.multiply(cant).setScale(2, RoundingMode.HALF_UP);
        BigDecimal reposicion = costoDirecto.multiply(cant).setScale(2, RoundingMode.HALF_UP);

        return CosteoVentaSnapshotDTO.builder()
                .costoDirectoUnitario(costoDirecto)
                .costoIndirectoUnitario(costoIndirecto)
                .costoTotalUnitario(costoTotal)
                .precioMinimoSnapshot(precioMinimo)
                .precioSugeridoSnapshot(precioSugerido)
                .montoReposicionTotal(reposicion)
                .utilidadBrutaUnitaria(utilidadBrutaUnit)
                .utilidadBrutaTotal(utilidadBrutaTotal)
                .utilidadNetaUnitaria(utilidadNetaUnit)
                .utilidadNetaTotal(utilidadNetaTotal)
                .margenBrutoPct(porcentaje(utilidadBrutaUnit, precio, 2))
                .margenNetoPct(porcentaje(utilidadNetaUnit, precio, 2))
                .reglaResumen(breakdown.resumenLegible())
                .build();
    }

    public CosteoProductoDTO actualizarSnapshotProducto(Producto producto) {
        CosteoProductoDTO analisis = analizarProducto(producto);
        producto.setCostoIndirectoEstimado(analisis.getCostoIndirectoUnitario());
        producto.setCostoTotalEstimado(analisis.getCostoTotalUnitario());
        producto.setPrecioMinimoEmpresarial(analisis.getPrecioMinimo());
        producto.setPrecioSugeridoEmpresarial(analisis.getPrecioSugerido());
        producto.setFechaUltimoCosteo(java.time.LocalDateTime.now());
        return analisis;
    }

    @Transactional
    public CosteoProductoDTO aplicarPrecioSugerido(Long productoId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        BigDecimal precioAnterior = producto.getPrecioVenta();
        BigDecimal costoAnterior = producto.getPrecioCompra();
        CosteoProductoDTO analisis = actualizarSnapshotProducto(producto);
        producto.setPrecioVenta(analisis.getPrecioSugerido());
        productoRepository.save(producto);
        historialPrecioProductoService.registrar(
                producto,
                precioAnterior,
                producto.getPrecioVenta(),
                costoAnterior,
                producto.getPrecioCompra(),
                analisis.getPrecioMinimo(),
                analisis.getPrecioSugerido(),
                "COSTEO",
                "Aplicacion individual de precio sugerido");
        return analizarProducto(producto);
    }

    @Transactional
    public Map<String, Object> aplicarPreciosSugeridos(List<Long> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) {
            return Map.of("actualizados", 0);
        }
        int actualizados = 0;
        for (Long productoId : productoIds.stream().filter(Objects::nonNull).distinct().toList()) {
            Producto producto = productoRepository.findById(productoId).orElse(null);
            if (producto == null || !producto.isActivo()) {
                continue;
            }
            BigDecimal precioAnterior = producto.getPrecioVenta();
            BigDecimal costoAnterior = producto.getPrecioCompra();
            CosteoProductoDTO analisis = actualizarSnapshotProducto(producto);
            if (analisis.getPrecioSugerido().compareTo(BigDecimal.ZERO) > 0
                    && analisis.getPrecioSugerido().compareTo(positivo(producto.getPrecioVenta())) != 0) {
                producto.setPrecioVenta(analisis.getPrecioSugerido());
                productoRepository.save(producto);
                historialPrecioProductoService.registrar(
                        producto,
                        precioAnterior,
                        producto.getPrecioVenta(),
                        costoAnterior,
                        producto.getPrecioCompra(),
                        analisis.getPrecioMinimo(),
                        analisis.getPrecioSugerido(),
                        "COSTEO",
                        "Aplicacion por lote de precio sugerido");
                actualizados++;
            }
        }
        return Map.of("actualizados", actualizados);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> historialCostosProducto(Long productoId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        CosteoContext context = construirContexto();
        CosteoProductoDTO analisis = analizarProducto(producto, context);
        List<DetalleCompra> recientes = detalleCompraRepository.ultimasComprasProducto(productoId, PageRequest.of(0, 12));

        List<DetalleCompra> cronologico = new ArrayList<>(recientes);
        Collections.reverse(cronologico);
        List<Map<String, Object>> filasCronologicas = new ArrayList<>();
        BigDecimal costoAnterior = null;
        for (DetalleCompra detalle : cronologico) {
            BigDecimal costoNuevo = costoCompraDetalle(detalle);
            BigDecimal variacionSoles = costoAnterior != null
                    ? costoNuevo.subtract(costoAnterior).setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            BigDecimal variacionPct = costoAnterior != null && costoAnterior.compareTo(BigDecimal.ZERO) > 0
                    ? variacionSoles.divide(costoAnterior, 6, RoundingMode.HALF_UP).multiply(CIEN).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            filasCronologicas.add(filaHistorialCompra(detalle, producto, analisis, costoAnterior, costoNuevo, variacionSoles, variacionPct));
            costoAnterior = costoNuevo;
        }

        List<Map<String, Object>> filas = new ArrayList<>(filasCronologicas);
        Collections.reverse(filas);

        Map<String, Object> grafico = new LinkedHashMap<>();
        grafico.put("labels", filasCronologicas.stream().map(f -> f.get("fechaCorta")).toList());
        grafico.put("costos", filasCronologicas.stream().map(f -> f.get("costoNuevo")).toList());
        grafico.put("sugeridos", filasCronologicas.stream().map(f -> f.get("precioSugerido")).toList());
        grafico.put("margenes", filasCronologicas.stream().map(f -> f.get("margenSugeridoPct")).toList());
        List<Map<String, Object>> cambiosPrecio = historialPrecioProductoService.ultimos(productoId).stream()
                .map(this::filaHistorialPrecio)
                .toList();

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("productoId", producto.getId());
        resumen.put("codigoInterno", texto(producto.getCodigoInterno(), ""));
        resumen.put("nombre", producto.getNombre());
        resumen.put("categoria", texto(producto.getCategoria(), ""));
        resumen.put("stockActual", producto.getStockActual() != null ? producto.getStockActual() : 0);
        resumen.put("costoActual", positivo(producto.getPrecioCompra()).setScale(4, RoundingMode.HALF_UP));
        resumen.put("precioActual", positivo(producto.getPrecioVenta()).setScale(2, RoundingMode.HALF_UP));
        resumen.put("precioMinimo", analisis.getPrecioMinimo());
        resumen.put("precioSugerido", analisis.getPrecioSugerido());
        resumen.put("margenActualPct", analisis.getMargenActualPct());
        resumen.put("estadoPrecio", analisis.getEstadoPrecio());
        resumen.put("recomendacion", analisis.getRecomendacion());
        resumen.put("comprasRegistradas", filas.size());
        resumen.put("proveedorUltimo", filas.isEmpty() ? "Sin compras" : filas.get(0).get("proveedor"));
        resumen.put("decisionPrincipal", filas.isEmpty()
                ? "Regularizar costo inicial y registrar la siguiente compra."
                : filas.get(0).get("decision"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("producto", resumen);
        out.put("compras", filas);
        out.put("cambiosPrecio", cambiosPrecio);
        out.put("grafico", grafico);
        out.put("nota", "Compras muestra costos por proveedor. Cambios de precio muestra las decisiones aplicadas con precio anterior, nuevo y origen.");
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ventasDebajoMinimo(LocalDate inicio, LocalDate fin) {
        return detalleVentaRepository.productosVendidosDebajoMinimo(inicio, fin).stream()
                .map(row -> {
                    BigDecimal ingreso = toBigDecimal(row[5]).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal utilidad = toBigDecimal(row[6]).setScale(2, RoundingMode.HALF_UP);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productoId", row[0]);
                    item.put("codigoInterno", row[1]);
                    item.put("nombre", row[2]);
                    item.put("categoria", row[3]);
                    item.put("cantidad", toBigDecimal(row[4]).setScale(3, RoundingMode.HALF_UP));
                    item.put("ingreso", ingreso);
                    item.put("utilidadNeta", utilidad);
                    item.put("precioVendidoMin", toBigDecimal(row[7]).setScale(2, RoundingMode.HALF_UP));
                    item.put("precioMinimo", toBigDecimal(row[8]).setScale(2, RoundingMode.HALF_UP));
                    item.put("veces", row[9] != null ? ((Number) row[9]).longValue() : 0L);
                    item.put("margenNetoPct", porcentaje(utilidad, ingreso, 1));
                    return item;
                })
                .toList();
    }

    @Transactional
    public CosteoProductoDTO actualizarDatosCosteoProducto(Long productoId,
                                                           String reglaCosteo,
                                                           BigDecimal unidadesEstimadasMes,
                                                           BigDecimal minutosTrabajoUnidad,
                                                           BigDecimal impresionesEquivalentesUnidad,
                                                           BigDecimal gananciaObjetivoPct,
                                                           BigDecimal gananciaMinimaPct,
                                                           Boolean usarReglaManualPrecio) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        producto.setReglaCosteo(texto(reglaCosteo, "AUTO"));
        producto.setUnidadesEstimadasMes(positivo(unidadesEstimadasMes));
        producto.setMinutosTrabajoUnidad(positivo(minutosTrabajoUnidad));
        producto.setImpresionesEquivalentesUnidad(positivo(impresionesEquivalentesUnidad));
        producto.setGananciaObjetivoPct(gananciaObjetivoPct);
        producto.setGananciaMinimaPct(gananciaMinimaPct);
        producto.setUsarReglaManualPrecio(Boolean.TRUE.equals(usarReglaManualPrecio));

        CosteoProductoDTO analisis = actualizarSnapshotProducto(producto);
        productoRepository.save(producto);
        return analisis;
    }

    public byte[] exportarExcel(List<CosteoProductoDTO> productos) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Costeo productos");
            CellStyle title = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title.setFont(titleFont);

            CellStyle header = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setBorderBottom(BorderStyle.THIN);

            Row rowTitle = sheet.createRow(0);
            rowTitle.createCell(0).setCellValue("Reporte de costeo y precios para contador");
            rowTitle.getCell(0).setCellStyle(title);

            String[] cols = {"Codigo", "Producto", "Categoria", "Tipo", "Stock", "Costo compra",
                    "Costo indirecto", "Costo total", "Margen minimo %", "Margen objetivo %",
                    "Precio minimo", "Precio sugerido", "Precio actual", "Diferencia", "Margen actual %",
                    "Estado", "Reglas aplicadas", "Datos faltantes"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < cols.length; i++) {
                headerRow.createCell(i).setCellValue(cols[i]);
                headerRow.getCell(i).setCellStyle(header);
            }

            int i = 3;
            for (CosteoProductoDTO p : productos) {
                Row row = sheet.createRow(i++);
                row.createCell(0).setCellValue(texto(p.getCodigoInterno(), ""));
                row.createCell(1).setCellValue(texto(p.getNombre(), ""));
                row.createCell(2).setCellValue(texto(p.getCategoria(), ""));
                row.createCell(3).setCellValue(texto(p.getTipo(), ""));
                row.createCell(4).setCellValue(p.getStockActual() != null ? p.getStockActual() : 0);
                row.createCell(5).setCellValue(p.getCostoDirectoUnitario().doubleValue());
                row.createCell(6).setCellValue(p.getCostoIndirectoUnitario().doubleValue());
                row.createCell(7).setCellValue(p.getCostoTotalUnitario().doubleValue());
                row.createCell(8).setCellValue(p.getGananciaMinimaPct().doubleValue());
                row.createCell(9).setCellValue(p.getGananciaObjetivoPct().doubleValue());
                row.createCell(10).setCellValue(p.getPrecioMinimo().doubleValue());
                row.createCell(11).setCellValue(p.getPrecioSugerido().doubleValue());
                row.createCell(12).setCellValue(p.getPrecioVentaActual().doubleValue());
                row.createCell(13).setCellValue(p.getDiferenciaPrecio().doubleValue());
                row.createCell(14).setCellValue(p.getMargenActualPct().doubleValue());
                row.createCell(15).setCellValue(texto(p.getEstadoPrecio(), ""));
                row.createCell(16).setCellValue(texto(p.getReglaResumen(), ""));
                row.createCell(17).setCellValue(texto(p.getDatosFaltantes(), ""));
            }
            for (int c = 0; c < cols.length; c++) {
                sheet.autoSizeColumn(c);
            }
            crearVentasDebajoMinimoSheet(workbook, header);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public void exportarPdf(List<CosteoProductoDTO> productos, OutputStream out) throws DocumentException {
        Document doc = new Document(PageSize.A4.rotate(), 22, 22, 24, 24);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.WHITE);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 7);

        Paragraph titulo = new Paragraph("Reporte de costeo y precios para contador", title);
        titulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(titulo);
        doc.add(new Paragraph("Incluye costo directo, costo indirecto asignado, margen minimo, margen objetivo, precio sugerido y diferencia contra el precio actual.", small));
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(11);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.0f, 2.8f, 1.4f, 1.0f, 1.1f, 1.1f, 1.1f, 1.1f, 1.1f, 1.1f, 1.2f});
        String[] headers = {"Codigo", "Producto", "Categoria", "Stock", "C. compra", "C. indirecto",
                "C. total", "P. minimo", "P. sugerido", "P. actual", "Estado"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, head));
            cell.setBackgroundColor(new Color(0, 105, 92));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(4);
            table.addCell(cell);
        }
        productos.stream().limit(120).forEach(p -> {
            add(table, texto(p.getCodigoInterno(), ""), body, Element.ALIGN_LEFT);
            add(table, texto(p.getNombre(), ""), body, Element.ALIGN_LEFT);
            add(table, texto(p.getCategoria(), ""), body, Element.ALIGN_LEFT);
            add(table, String.valueOf(p.getStockActual() != null ? p.getStockActual() : 0), body, Element.ALIGN_CENTER);
            add(table, moneda(p.getCostoDirectoUnitario()), body, Element.ALIGN_RIGHT);
            add(table, moneda(p.getCostoIndirectoUnitario()), body, Element.ALIGN_RIGHT);
            add(table, moneda(p.getCostoTotalUnitario()), body, Element.ALIGN_RIGHT);
            add(table, moneda(p.getPrecioMinimo()), body, Element.ALIGN_RIGHT);
            add(table, moneda(p.getPrecioSugerido()), body, Element.ALIGN_RIGHT);
            add(table, moneda(p.getPrecioVentaActual()), body, Element.ALIGN_RIGHT);
            add(table, texto(p.getEstadoPrecio(), ""), body, Element.ALIGN_CENTER);
        });
        doc.add(table);
        if (productos.size() > 120) {
            doc.add(new Paragraph("El PDF muestra los primeros 120 productos. Use Excel para el detalle completo.", small));
        }
        List<Map<String, Object>> ventasDebajoMinimo = ventasDebajoMinimo(YearMonth.now().atDay(1), YearMonth.now().atEndOfMonth());
        if (!ventasDebajoMinimo.isEmpty()) {
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Ventas del mes debajo del precio minimo", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            PdfPTable riesgo = new PdfPTable(7);
            riesgo.setWidthPercentage(100);
            riesgo.setWidths(new float[]{1.1f, 2.8f, 1.3f, 1.0f, 1.2f, 1.2f, 1.2f});
            String[] riesgoHeaders = {"Codigo", "Producto", "Categoria", "Cant.", "Ingreso", "Utilidad", "Minimo"};
            for (String h : riesgoHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, head));
                cell.setBackgroundColor(new Color(153, 27, 27));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(4);
                riesgo.addCell(cell);
            }
            ventasDebajoMinimo.stream().limit(40).forEach(row -> {
                add(riesgo, texto(row.get("codigoInterno") != null ? row.get("codigoInterno").toString() : "", ""), body, Element.ALIGN_LEFT);
                add(riesgo, texto(row.get("nombre") != null ? row.get("nombre").toString() : "", ""), body, Element.ALIGN_LEFT);
                add(riesgo, texto(row.get("categoria") != null ? row.get("categoria").toString() : "", ""), body, Element.ALIGN_LEFT);
                add(riesgo, String.valueOf(row.get("cantidad")), body, Element.ALIGN_RIGHT);
                add(riesgo, moneda((BigDecimal) row.get("ingreso")), body, Element.ALIGN_RIGHT);
                add(riesgo, moneda((BigDecimal) row.get("utilidadNeta")), body, Element.ALIGN_RIGHT);
                add(riesgo, moneda((BigDecimal) row.get("precioMinimo")), body, Element.ALIGN_RIGHT);
            });
            doc.add(riesgo);
        }
        doc.close();
    }

    private void crearVentasDebajoMinimoSheet(Workbook workbook, CellStyle header) {
        Sheet sheet = workbook.createSheet("Ventas bajo minimo");
        String[] cols = {"Codigo", "Producto", "Categoria", "Cantidad", "Ingreso", "Utilidad neta",
                "Precio vendido min", "Precio minimo", "Veces", "Margen neto %"};
        Row h = sheet.createRow(0);
        for (int i = 0; i < cols.length; i++) {
            h.createCell(i).setCellValue(cols[i]);
            h.getCell(i).setCellStyle(header);
        }
        List<Map<String, Object>> ventas = ventasDebajoMinimo(YearMonth.now().atDay(1), YearMonth.now().atEndOfMonth());
        int rowIdx = 1;
        for (Map<String, Object> item : ventas) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(texto(item.get("codigoInterno") != null ? item.get("codigoInterno").toString() : "", ""));
            row.createCell(1).setCellValue(texto(item.get("nombre") != null ? item.get("nombre").toString() : "", ""));
            row.createCell(2).setCellValue(texto(item.get("categoria") != null ? item.get("categoria").toString() : "", ""));
            row.createCell(3).setCellValue(((BigDecimal) item.get("cantidad")).doubleValue());
            row.createCell(4).setCellValue(((BigDecimal) item.get("ingreso")).doubleValue());
            row.createCell(5).setCellValue(((BigDecimal) item.get("utilidadNeta")).doubleValue());
            row.createCell(6).setCellValue(((BigDecimal) item.get("precioVendidoMin")).doubleValue());
            row.createCell(7).setCellValue(((BigDecimal) item.get("precioMinimo")).doubleValue());
            row.createCell(8).setCellValue(((Number) item.get("veces")).longValue());
            row.createCell(9).setCellValue(((BigDecimal) item.get("margenNetoPct")).doubleValue());
        }
        for (int i = 0; i < cols.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private CosteoBreakdown calcularCostoIndirecto(Producto producto, CosteoContext context) {
        List<String> partes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ConfigCuentaFija cuenta : context.cuentas()) {
            if (!aplicaAProducto(cuenta, producto)) {
                continue;
            }
            BigDecimal unitario = costoUnitarioCuenta(cuenta, producto, context);
            if (unitario.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(unitario);
                if (partes.size() < 4) {
                    partes.add(nombreCorto(cuenta.getNombre()) + " " + reglaLegible(cuenta.getReglaReparto())
                            + " S/ " + unitario.setScale(2, RoundingMode.HALF_UP));
                }
            }
        }
        return new CosteoBreakdown(total.setScale(4, RoundingMode.HALF_UP), String.join(" + ", partes));
    }

    private List<ConfigCuentaFija> cuentasCosteo() {
        return cuentaFijaRepository.findByActivaTrueOrderByOrdenAscNombreAsc().stream()
                .filter(c -> c.getIncluirEnCosteo() == null || Boolean.TRUE.equals(c.getIncluirEnCosteo()))
                .toList();
    }

    private BigDecimal costoUnitarioCuenta(ConfigCuentaFija cuenta, Producto producto, CosteoContext context) {
        BigDecimal monto = montoCosteoMensual(cuenta);
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        String regla = texto(cuenta.getReglaReparto(), "UNIDADES");
        return switch (regla) {
            case "COSTO_COMPRA" -> {
                BigDecimal baseCosto = base(cuenta, context.valorInventarioCosto());
                yield positivo(producto.getPrecioCompra()).multiply(monto).divide(baseCosto, 4, RoundingMode.HALF_UP);
            }
            case "CATEGORIA" -> {
                BigDecimal baseCategoria = base(cuenta, cantidadVendidaCategoria(producto.getCategoria(), context));
                yield monto.divide(baseCategoria, 4, RoundingMode.HALF_UP);
            }
            case "TIEMPO" -> {
                BigDecimal baseMinutos = base(cuenta, DEFAULT_MINUTOS_MES);
                yield monto.divide(baseMinutos, 6, RoundingMode.HALF_UP)
                        .multiply(positivo(producto.getMinutosTrabajoUnidad()))
                        .setScale(4, RoundingMode.HALF_UP);
            }
            case "IMPRESION" -> {
                BigDecimal baseImpresiones = base(cuenta, DEFAULT_IMPRESIONES_MES);
                yield monto.divide(baseImpresiones, 6, RoundingMode.HALF_UP)
                        .multiply(positivo(producto.getImpresionesEquivalentesUnidad()))
                        .setScale(4, RoundingMode.HALF_UP);
            }
            case "VENTAS_ESPERADAS" -> {
                BigDecimal baseUnidades = positivo(producto.getUnidadesEstimadasMes()).compareTo(BigDecimal.ZERO) > 0
                        ? positivo(producto.getUnidadesEstimadasMes())
                        : base(cuenta, context.cantidadVendidaMes());
                yield monto.divide(baseUnidades, 4, RoundingMode.HALF_UP);
            }
            default -> {
                BigDecimal baseUnidades = base(cuenta, context.cantidadVendidaMes());
                yield monto.divide(baseUnidades, 4, RoundingMode.HALF_UP);
            }
        };
    }

    private BigDecimal montoCosteoMensual(ConfigCuentaFija cuenta) {
        BigDecimal pct = cuenta.getPorcentajeUsoCosteo() != null ? cuenta.getPorcentajeUsoCosteo() : CIEN;
        if (pct.compareTo(BigDecimal.ZERO) < 0) pct = BigDecimal.ZERO;
        if (pct.compareTo(CIEN) > 0) pct = CIEN;
        return positivo(cuenta.getMontoMensual())
                .multiply(pct)
                .divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    private boolean aplicaAProducto(ConfigCuentaFija cuenta, Producto producto) {
        String objetivo = normalizar(cuenta.getCategoriaObjetivo());
        if (objetivo.isBlank() || "TODAS".equals(objetivo) || "GENERAL".equals(objetivo)) {
            return true;
        }
        String categoria = normalizar(producto.getCategoria());
        String tipo = normalizar(producto.getTipo());
        String regla = normalizar(producto.getReglaCosteo());
        return objetivo.equals(categoria) || objetivo.equals(tipo) || objetivo.equals(regla);
    }

    private CosteoContext construirContexto() {
        YearMonth mes = YearMonth.now();
        LocalDate inicio = mes.atDay(1);
        LocalDate fin = mes.atEndOfMonth();
        List<ConfigCuentaFija> cuentas = cuentasCosteo();
        Configuracion config = configuracionService.obtenerConfiguracion();
        BigDecimal cantidadMes = positivo(detalleVentaRepository.sumarCantidadVendidaPorPeriodo(inicio, fin));
        if (cantidadMes.compareTo(BigDecimal.ZERO) <= 0) {
            cantidadMes = DEFAULT_UNIDADES_MES;
        }
        BigDecimal valorInventario = positivo(productoRepository.calcularValorInventario());
        if (valorInventario.compareTo(BigDecimal.ZERO) <= 0) {
            valorInventario = BigDecimal.ONE;
        }
        Map<String, BigDecimal> ventasCategoria = new LinkedHashMap<>();
        for (Object[] row : detalleVentaRepository.sumarCantidadVendidaAgrupadaCategoria(inicio, fin)) {
            if (row == null || row.length < 2) {
                continue;
            }
            ventasCategoria.put(normalizar(String.valueOf(row[0])), positivo(toBigDecimal(row[1])));
        }
        return new CosteoContext(cuentas, config, cantidadMes, valorInventario, ventasCategoria);
    }

    private BigDecimal cantidadVendidaCategoria(String categoria, CosteoContext context) {
        if (categoria == null || categoria.isBlank()) {
            return context.cantidadVendidaMes();
        }
        BigDecimal cantidad = context.ventasPorCategoria().get(normalizar(categoria));
        return positivo(cantidad).compareTo(BigDecimal.ZERO) > 0 ? cantidad : DEFAULT_UNIDADES_MES;
    }

    private BigDecimal base(ConfigCuentaFija cuenta, BigDecimal fallback) {
        BigDecimal base = positivo(cuenta.getBaseMensual());
        if (base.compareTo(BigDecimal.ZERO) > 0) {
            return base;
        }
        BigDecimal safeFallback = positivo(fallback);
        return safeFallback.compareTo(BigDecimal.ZERO) > 0 ? safeFallback : BigDecimal.ONE;
    }

    private BigDecimal resolverGananciaObjetivo(Producto producto, Configuracion config) {
        if (Boolean.TRUE.equals(producto.getUsarReglaManualPrecio()) && producto.getGananciaObjetivoPct() != null) {
            return positivo(producto.getGananciaObjetivoPct());
        }
        return positivo(config.getGananciaObjetivoGlobalPct());
    }

    private BigDecimal resolverGananciaMinima(Producto producto, Configuracion config) {
        if (Boolean.TRUE.equals(producto.getUsarReglaManualPrecio()) && producto.getGananciaMinimaPct() != null) {
            return positivo(producto.getGananciaMinimaPct());
        }
        return positivo(config.getGananciaMinimaGlobalPct());
    }

    private String datosFaltantes(Producto producto, CosteoBreakdown breakdown, CosteoContext context) {
        List<String> datos = new ArrayList<>();
        if (positivo(producto.getPrecioCompra()).compareTo(BigDecimal.ZERO) <= 0) {
            datos.add("sin costo compra");
        }
        if (breakdown.total().compareTo(BigDecimal.ZERO) <= 0 && !context.cuentas().isEmpty()) {
            datos.add("sin indirecto asignado");
        }
        if ("TIEMPO".equals(texto(producto.getReglaCosteo(), "AUTO"))
                && positivo(producto.getMinutosTrabajoUnidad()).compareTo(BigDecimal.ZERO) <= 0) {
            datos.add("falta tiempo");
        }
        if ("IMPRESION".equals(texto(producto.getReglaCosteo(), "AUTO"))
                && positivo(producto.getImpresionesEquivalentesUnidad()).compareTo(BigDecimal.ZERO) <= 0) {
            datos.add("falta equivalencia impresion");
        }
        return String.join(", ", datos);
    }

    private String estadoPrecio(BigDecimal precioActual, BigDecimal costoTotal, BigDecimal precioMinimo, BigDecimal precioSugerido) {
        if (precioActual.compareTo(BigDecimal.ZERO) <= 0) return "SIN_PRECIO";
        if (precioActual.compareTo(costoTotal) < 0) return "PERDIDA";
        if (precioActual.compareTo(precioMinimo) < 0) return "BAJO";
        if (precioActual.compareTo(precioSugerido) < 0) return "SUBIR";
        return "OK";
    }

    private String recomendacion(String estado, BigDecimal diferencia) {
        return switch (estado) {
            case "PERDIDA" -> "Pierde dinero: subir urgente o revisar costo.";
            case "BAJO" -> "Gana poco: ajustar al minimo saludable.";
            case "SUBIR" -> "Conviene subir aprox. S/ " + diferencia.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            case "SIN_PRECIO" -> "Falta precio de venta.";
            default -> "Precio saludable.";
        };
    }

    private Map<String, Object> filaHistorialCompra(DetalleCompra detalle,
                                                    Producto producto,
                                                    CosteoProductoDTO analisis,
                                                    BigDecimal costoAnterior,
                                                    BigDecimal costoNuevo,
                                                    BigDecimal variacionSoles,
                                                    BigDecimal variacionPct) {
        Compra compra = detalle.getCompra();
        Proveedor proveedor = compra != null ? compra.getProveedor() : null;
        DateTimeFormatter fechaLarga = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        DateTimeFormatter fechaCorta = DateTimeFormatter.ofPattern("dd/MM");
        BigDecimal sugerido = positivo(detalle.getPrecioSugeridoSnapshot());
        if (sugerido.compareTo(BigDecimal.ZERO) <= 0) {
            sugerido = analisis.getPrecioSugerido();
        }
        BigDecimal minimo = positivo(detalle.getPrecioMinimoSnapshot());
        if (minimo.compareTo(BigDecimal.ZERO) <= 0) {
            minimo = analisis.getPrecioMinimo();
        }
        BigDecimal margenSugerido = sugerido.compareTo(BigDecimal.ZERO) > 0
                ? sugerido.subtract(costoNuevo).divide(sugerido, 6, RoundingMode.HALF_UP).multiply(CIEN).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("detalleId", detalle.getId());
        item.put("compraId", compra != null ? compra.getId() : null);
        item.put("fecha", compra != null && compra.getFecha() != null ? compra.getFecha().format(fechaLarga) : "-");
        item.put("fechaCorta", compra != null && compra.getFecha() != null ? compra.getFecha().format(fechaCorta) : "-");
        item.put("proveedor", proveedor != null ? proveedor.getRazonSocial() : "Sin proveedor");
        item.put("documento", documentoCompra(compra));
        item.put("cantidad", detalle.getCantidad() != null ? detalle.getCantidad() : 0);
        item.put("presentacion", texto(detalle.getPresentacionNombre(), "UNIDAD"));
        item.put("costoAnterior", costoAnterior != null ? costoAnterior.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        item.put("costoBase", positivo(detalle.getCostoUnitarioBase()).setScale(4, RoundingMode.HALF_UP));
        item.put("indirectoUnitario", positivo(detalle.getCargoIndirectoUnitario()).setScale(4, RoundingMode.HALF_UP));
        item.put("costoNuevo", costoNuevo);
        item.put("variacionSoles", variacionSoles);
        item.put("variacionPct", variacionPct);
        item.put("precioMinimo", minimo);
        item.put("precioSugerido", sugerido);
        item.put("precioActual", positivo(producto.getPrecioVenta()).setScale(2, RoundingMode.HALF_UP));
        item.put("margenSugeridoPct", margenSugerido);
        item.put("decision", decisionHistorial(analisis, costoAnterior, costoNuevo, variacionPct, detalle));
        return item;
    }

    private Map<String, Object> filaHistorialPrecio(HistorialPrecioProducto historial) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fecha", historial.getFecha() != null ? historial.getFecha().format(fmt) : "-");
        item.put("precioAnterior", positivo(historial.getPrecioAnterior()).setScale(2, RoundingMode.HALF_UP));
        item.put("precioNuevo", positivo(historial.getPrecioNuevo()).setScale(2, RoundingMode.HALF_UP));
        item.put("costoAnterior", positivo(historial.getCostoAnterior()).setScale(4, RoundingMode.HALF_UP));
        item.put("costoNuevo", positivo(historial.getCostoNuevo()).setScale(4, RoundingMode.HALF_UP));
        item.put("precioMinimo", positivo(historial.getPrecioMinimo()).setScale(2, RoundingMode.HALF_UP));
        item.put("precioSugerido", positivo(historial.getPrecioSugerido()).setScale(2, RoundingMode.HALF_UP));
        item.put("origen", texto(historial.getOrigen(), "SISTEMA"));
        item.put("motivo", texto(historial.getMotivo(), ""));
        item.put("usuario", texto(historial.getUsuario(), ""));
        return item;
    }

    private BigDecimal costoCompraDetalle(DetalleCompra detalle) {
        BigDecimal costo = positivo(detalle.getCostoUnitarioReal());
        if (costo.compareTo(BigDecimal.ZERO) <= 0) {
            costo = positivo(detalle.getCostoUnitarioBase());
        }
        if (costo.compareTo(BigDecimal.ZERO) <= 0) {
            costo = positivo(detalle.getPrecioUnitario());
        }
        return costo.setScale(4, RoundingMode.HALF_UP);
    }

    private String documentoCompra(Compra compra) {
        if (compra == null) {
            return "-";
        }
        String tipo = compra.getTipoComprobante() != null && !compra.getTipoComprobante().isBlank()
                ? compra.getTipoComprobante().trim()
                : "Compra";
        String numero = compra.getNumeroComprobante() != null && !compra.getNumeroComprobante().isBlank()
                ? compra.getNumeroComprobante().trim()
                : "#" + compra.getId();
        return tipo + " " + numero;
    }

    private String decisionHistorial(CosteoProductoDTO analisis,
                                     BigDecimal costoAnterior,
                                     BigDecimal costoNuevo,
                                     BigDecimal variacionPct,
                                     DetalleCompra detalle) {
        if ("PERDIDA".equals(analisis.getEstadoPrecio()) || "BAJO".equals(analisis.getEstadoPrecio())) {
            return "Subir precio o revisar costo: el precio actual queda bajo el minimo.";
        }
        if (costoAnterior == null || costoAnterior.compareTo(BigDecimal.ZERO) <= 0) {
            return "Primer costo confiable: usar como base de regularizacion.";
        }
        if (variacionPct.compareTo(new BigDecimal("15.00")) >= 0) {
            return "Revisar proveedor y simular subida: el costo subio fuerte.";
        }
        if (variacionPct.compareTo(new BigDecimal("-8.00")) <= 0) {
            return "Buen costo: evaluar compra por mayor si rota rapido.";
        }
        if (detalle.getCantidad() != null && detalle.getCantidad() >= 12) {
            return "Mantener precio y vigilar margen; compra ya parece mayorista.";
        }
        if ("SUBIR".equals(analisis.getEstadoPrecio())) {
            return "Conviene subir al sugerido para llegar al margen objetivo.";
        }
        if (Boolean.TRUE.equals(detalle.getProducto() != null ? detalle.getProducto().getEnLiquidacion() : false)) {
            return "Producto en liquidacion: validar si conviene reponerlo.";
        }
        return "Mantener precio y monitorear la siguiente compra.";
    }

    private BigDecimal aplicarMargen(BigDecimal base, BigDecimal margenPct) {
        return positivo(base).multiply(BigDecimal.ONE.add(positivo(margenPct).divide(CIEN, 6, RoundingMode.HALF_UP)));
    }

    private BigDecimal porcentaje(BigDecimal parte, BigDecimal total, int escala) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(escala, RoundingMode.HALF_UP);
        }
        return positivo(parte).divide(total, 6, RoundingMode.HALF_UP).multiply(CIEN).setScale(escala, RoundingMode.HALF_UP);
    }

    private BigDecimal positivo(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return valor;
    }

    private BigDecimal toBigDecimal(Object valor) {
        if (valor instanceof BigDecimal bd) {
            return bd;
        }
        if (valor instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (valor != null) {
            try {
                return new BigDecimal(valor.toString());
            } catch (NumberFormatException ignored) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private String texto(String valor, String defecto) {
        return valor == null || valor.isBlank() ? defecto : valor.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .trim();
    }

    private String nombreCorto(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "Costo";
        }
        String limpio = nombre.trim();
        return limpio.length() <= 18 ? limpio : limpio.substring(0, 18);
    }

    private String reglaLegible(String regla) {
        return switch (texto(regla, "UNIDADES")) {
            case "COSTO_COMPRA" -> "por costo";
            case "CATEGORIA" -> "por categoria";
            case "TIEMPO" -> "por tiempo";
            case "IMPRESION" -> "por impresion";
            case "VENTAS_ESPERADAS" -> "por meta";
            default -> "por unidad";
        };
    }

    private String moneda(BigDecimal valor) {
        return "S/ " + positivo(valor).setScale(2, RoundingMode.HALF_UP);
    }

    private void add(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(3);
        table.addCell(cell);
    }

    private record CosteoBreakdown(BigDecimal total, String resumen) {
        String resumenLegible() {
            return resumen == null || resumen.isBlank() ? "Sin indirectos configurados" : resumen;
        }
    }

    private record CosteoContext(List<ConfigCuentaFija> cuentas,
                                 Configuracion configuracion,
                                 BigDecimal cantidadVendidaMes,
                                 BigDecimal valorInventarioCosto,
                                 Map<String, BigDecimal> ventasPorCategoria) {
    }
}
