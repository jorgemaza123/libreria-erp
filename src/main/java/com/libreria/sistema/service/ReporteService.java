package com.libreria.sistema.service;

import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import com.libreria.sistema.repository.VentaRepository;

// --- IMPORTS PDF (iText/OpenPDF) ---
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

// --- IMPORTS EXCEL (Apache POI) ---
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * Servicio de generación de reportes OPTIMIZADO.
 *
 * MEJORAS DE RENDIMIENTO:
 * - Ya NO trae TODAS las ventas/movimientos a memoria para filtrar en Java
 * - Usa consultas JPQL que filtran directamente en la Base de Datos
 * - Reduce drasticamente el consumo de RAM y tiempo de respuesta
 */
@Service
@Slf4j
public class ReporteService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final CajaRepository cajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionService configuracionService;

    public ReporteService(VentaRepository ventaRepository, ProductoRepository productoRepository,
                          CajaRepository cajaRepository, UsuarioRepository usuarioRepository,
                          ConfiguracionService configuracionService) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.cajaRepository = cajaRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionService = configuracionService;
    }

    // ==========================================
    //              LÓGICA EXCEL
    // ==========================================
    public void generarExcel(String tipo, LocalDate inicio, LocalDate fin, OutputStream outputStream) throws IOException {
        var config = configuracionService.obtenerConfiguracion();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(tipo);

        CellStyle headerStyle = crearEstiloCabecera(workbook);
        CellStyle dataStyle = crearEstiloDatos(workbook);

        // Normalizar fechas: si no se especifican, usar rango amplio
        LocalDate fechaInicio = inicio != null ? inicio : LocalDate.of(2020, 1, 1);
        LocalDate fechaFin = fin != null ? fin : LocalDate.now();

        log.info("Generando Excel {} para periodo: {} - {}", tipo, fechaInicio, fechaFin);

        if ("VENTAS".equals(tipo)) generarExcelVentas(sheet, fechaInicio, fechaFin, headerStyle, dataStyle, config);
        else if ("CAJA".equals(tipo)) generarExcelCaja(sheet, fechaInicio, fechaFin, headerStyle, dataStyle, config);
        else if ("INVENTARIO".equals(tipo)) generarExcelInventario(sheet, headerStyle, dataStyle, config);
        else if ("USUARIOS".equals(tipo)) generarExcelUsuarios(sheet, headerStyle, dataStyle);
        // NUEVOS REPORTES AVANZADOS
        else if ("VENTAS_POR_USUARIO".equals(tipo)) generarExcelVentasPorUsuario(sheet, fechaInicio, fechaFin, headerStyle, dataStyle, config);
        else if ("VENTAS_POR_PRODUCTO".equals(tipo)) generarExcelVentasPorProducto(sheet, fechaInicio, fechaFin, headerStyle, dataStyle, config);
        else if ("RESUMEN_FINANCIERO".equals(tipo)) generarExcelResumenFinanciero(sheet, fechaInicio, fechaFin, headerStyle, dataStyle, config);
        else if ("STOCK_BAJO".equals(tipo)) generarExcelStockBajo(sheet, headerStyle, dataStyle, config);

        for(int i=0; i<8; i++) sheet.autoSizeColumn(i);

        workbook.write(outputStream);
        workbook.close();
    }

    private CellStyle crearEstiloCabecera(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 12);

        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloDatos(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    /**
     * OPTIMIZADO: Ahora filtra en la BD, no en Java
     */
    private void generarExcelVentas(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle, com.libreria.sistema.model.Configuracion config) {
        crearFilaCabecera(sheet, headerStyle, "ID", "FECHA", "COMPROBANTE", "CLIENTE", "METODO PAGO", "TOTAL", "ESTADO");

        // OPTIMIZACIÓN: Consulta filtrada en BD
        List<Venta> lista = ventaRepository.findByFechaEmisionBetween(inicio, fin);
        log.debug("Ventas obtenidas para reporte: {} registros", lista.size());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(config.getFormatoFechaReportes() != null ? config.getFormatoFechaReportes() : "dd/MM/yyyy");
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";

        int rowIdx = 1;
        for (Venta v : lista) {
            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, v.getId().toString(), dataStyle);
            crearCelda(row, 1, v.getFechaEmision().format(formatter), dataStyle);
            crearCelda(row, 2, v.getTipoComprobante() + " " + v.getSerie() + "-" + v.getNumero(), dataStyle);
            crearCelda(row, 3, v.getClienteDenominacion(), dataStyle);
            crearCelda(row, 4, v.getMetodoPago() != null ? v.getMetodoPago() : "EFECTIVO", dataStyle);
            crearCelda(row, 5, moneda + v.getTotal(), dataStyle);
            crearCelda(row, 6, v.getEstado(), dataStyle);
        }
    }

    /**
     * OPTIMIZADO: Ahora filtra en la BD, no en Java
     */
    private void generarExcelCaja(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle, com.libreria.sistema.model.Configuracion config) {
        crearFilaCabecera(sheet, headerStyle, "FECHA", "TIPO", "CONCEPTO", "MONTO", "USUARIO");

        // OPTIMIZACIÓN: Consulta filtrada en BD
        List<MovimientoCaja> lista = cajaRepository.findByFechaBetweenDates(inicio, fin);
        log.debug("Movimientos de caja obtenidos para reporte: {} registros", lista.size());

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";

        int rowIdx = 1;
        for (MovimientoCaja m : lista) {
            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, m.getFecha().toString().replace("T", " "), dataStyle);
            crearCelda(row, 1, m.getTipo(), dataStyle);
            crearCelda(row, 2, m.getConcepto(), dataStyle);
            crearCelda(row, 3, moneda + m.getMonto(), dataStyle);
            crearCelda(row, 4, m.getUsuario() != null ? m.getUsuario().getUsername() : "-", dataStyle);
        }
    }

    private void generarExcelInventario(Sheet sheet, CellStyle headerStyle, CellStyle dataStyle, com.libreria.sistema.model.Configuracion config) {
        crearFilaCabecera(sheet, headerStyle, "CODIGO", "PRODUCTO", "STOCK", "P. COMPRA", "P. VENTA", "VALORIZADO");
        List<Producto> lista = productoRepository.findByActivoTrue();
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";
        int rowIdx = 1;
        for (Producto p : lista) {
            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, p.getCodigoInterno(), dataStyle);
            crearCelda(row, 1, p.getNombre(), dataStyle);
            crearCelda(row, 2, String.valueOf(p.getStockActual()), dataStyle);
            crearCelda(row, 3, moneda + (p.getPrecioCompra()!=null?p.getPrecioCompra():0), dataStyle);
            crearCelda(row, 4, moneda + p.getPrecioVenta(), dataStyle);

            double valor = p.getStockActual() * (p.getPrecioCompra()!=null?p.getPrecioCompra().doubleValue():0);
            crearCelda(row, 5, moneda + String.format("%.2f", valor), dataStyle);
        }
    }

    private void generarExcelUsuarios(Sheet sheet, CellStyle headerStyle, CellStyle dataStyle) {
        crearFilaCabecera(sheet, headerStyle, "ID", "USUARIO", "NOMBRE COMPLETO", "ESTADO", "ROLES");
        List<Usuario> lista = usuarioRepository.findAll();
        int rowIdx = 1;
        for (Usuario u : lista) {
            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, u.getId().toString(), dataStyle);
            crearCelda(row, 1, u.getUsername(), dataStyle);
            crearCelda(row, 2, u.getNombreCompleto(), dataStyle);
            crearCelda(row, 3, u.isActivo() ? "ACTIVO" : "INACTIVO", dataStyle);

            StringBuilder roles = new StringBuilder();
            u.getRoles().forEach(r -> roles.append(r.getNombre()).append(", "));
            crearCelda(row, 4, roles.toString(), dataStyle);
        }
    }

    private void crearFilaCabecera(Sheet sheet, CellStyle style, String... headers) {
        Row header = sheet.createRow(0);
        for(int i=0; i<headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void crearCelda(Row row, int column, String valor, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(valor);
        cell.setCellStyle(style);
    }

    // ==========================================
    //              LÓGICA PDF
    // ==========================================
    public void generarPdf(String tipo, LocalDate inicio, LocalDate fin, OutputStream outputStream) throws DocumentException {
        Document document = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(document, outputStream);
        document.open();

        // --- CONFIGURACIÓN ---
        var config = configuracionService.obtenerConfiguracion();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(config.getFormatoFechaReportes() != null ? config.getFormatoFechaReportes() : "dd/MM/yyyy");

        // Normalizar fechas
        LocalDate fechaInicio = inicio != null ? inicio : LocalDate.of(2020, 1, 1);
        LocalDate fechaFin = fin != null ? fin : LocalDate.now();

        log.info("Generando PDF {} para periodo: {} - {}", tipo, fechaInicio, fechaFin);

        // --- PARSEAR COLOR PRIMARIO ---
        Color colorPrimario = parseColor(config.getColorPrimario(), Color.BLUE);

        // --- ENCABEZADO PERSONALIZADO ---
        if (config.getEncabezadoReportes() != null && !config.getEncabezadoReportes().trim().isEmpty()) {
            Paragraph encabezado = new Paragraph(config.getEncabezadoReportes(), FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
            encabezado.setAlignment(Element.ALIGN_CENTER);
            document.add(encabezado);
            document.add(new Paragraph(" "));
        }

        // --- CABECERA EMPRESARIAL ---
        int numColumnas = (config.getMostrarLogoEnReportes() && config.getLogoBase64() != null) ? 3 : 2;
        PdfPTable headerTable = new PdfPTable(numColumnas);
        headerTable.setWidthPercentage(100);

        // LOGO (si está habilitado)
        if (config.getMostrarLogoEnReportes() && config.getLogoBase64() != null) {
            try {
                byte[] logoBytes = Base64.getDecoder().decode(config.getLogoBase64());
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(80, 80);
                PdfPCell cellLogo = new PdfPCell(logo);
                cellLogo.setBorder(Rectangle.NO_BORDER);
                cellLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
                headerTable.addCell(cellLogo);
            } catch (Exception e) {
                PdfPCell cellEmpty = new PdfPCell();
                cellEmpty.setBorder(Rectangle.NO_BORDER);
                headerTable.addCell(cellEmpty);
            }
        }

        // DATOS DE EMPRESA
        PdfPCell cellEmpresa = new PdfPCell();
        cellEmpresa.setBorder(Rectangle.NO_BORDER);
        cellEmpresa.addElement(new Paragraph(config.getNombreEmpresa(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        cellEmpresa.addElement(new Paragraph("RUC: " + config.getRuc(), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cellEmpresa.addElement(new Paragraph("Direccion: " + config.getDireccion(), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        if (config.getTelefono() != null) cellEmpresa.addElement(new Paragraph("Tel: " + config.getTelefono(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
        headerTable.addCell(cellEmpresa);

        // TÍTULO Y FECHA
        PdfPCell cellTitulo = new PdfPCell();
        cellTitulo.setBorder(Rectangle.NO_BORDER);
        cellTitulo.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph pTitulo = new Paragraph("REPORTE DE " + tipo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, colorPrimario));
        pTitulo.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pTitulo);

        String fechaStr = "Fecha Emision: " + LocalDate.now().format(formatter);
        fechaStr += "\nPeriodo: " + fechaInicio.format(formatter) + " al " + fechaFin.format(formatter);

        Paragraph pFecha = new Paragraph(fechaStr, FontFactory.getFont(FontFactory.HELVETICA, 10));
        pFecha.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pFecha);

        headerTable.addCell(cellTitulo);
        document.add(headerTable);
        document.add(new Paragraph(" "));

        // --- CONTENIDO ---
        if ("VENTAS".equals(tipo)) generarPdfVentas(document, fechaInicio, fechaFin, config);
        else if ("CAJA".equals(tipo)) generarPdfCaja(document, fechaInicio, fechaFin, config);
        else if ("INVENTARIO".equals(tipo)) generarPdfInventario(document, config);
        else if ("USUARIOS".equals(tipo)) generarPdfUsuarios(document, config);
        // NUEVOS REPORTES AVANZADOS
        else if ("VENTAS_POR_USUARIO".equals(tipo)) generarPdfVentasPorUsuario(document, fechaInicio, fechaFin, config);
        else if ("VENTAS_POR_PRODUCTO".equals(tipo)) generarPdfVentasPorProducto(document, fechaInicio, fechaFin, config);
        else if ("RESUMEN_FINANCIERO".equals(tipo)) generarPdfResumenFinanciero(document, fechaInicio, fechaFin, config);
        else if ("STOCK_BAJO".equals(tipo)) generarPdfStockBajo(document, config);

        // --- PIE DE PÁGINA PERSONALIZADO ---
        if (config.getPiePaginaReportes() != null && !config.getPiePaginaReportes().trim().isEmpty()) {
            document.add(new Paragraph(" "));
            Paragraph pie = new Paragraph(config.getPiePaginaReportes(), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY));
            pie.setAlignment(Element.ALIGN_CENTER);
            document.add(pie);
        }

        document.close();
    }

    private Color parseColor(String hexColor, Color defaultColor) {
        if (hexColor == null || hexColor.trim().isEmpty()) return defaultColor;
        try {
            String hex = hexColor.replace("#", "");
            return new Color(
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
            );
        } catch (Exception e) {
            return defaultColor;
        }
    }

    /**
     * OPTIMIZADO: Ahora filtra en la BD, no en Java
     */
    private void generarPdfVentas(Document document, LocalDate inicio, LocalDate fin, com.libreria.sistema.model.Configuracion config) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 2, 4, 2, 2, 2});

        agregarCabeceraPdf(table, config, "FECHA", "DOC", "CLIENTE", "METODO", "TOTAL", "ESTADO");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(config.getFormatoFechaReportes() != null ? config.getFormatoFechaReportes() : "dd/MM/yyyy");
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";

        // OPTIMIZACIÓN: Consulta filtrada en BD
        List<Venta> lista = ventaRepository.findByFechaEmisionBetween(inicio, fin);
        log.debug("Ventas obtenidas para PDF: {} registros", lista.size());

        for (Venta v : lista) {
            table.addCell(crearCeldaPdf(v.getFechaEmision().format(formatter)));
            table.addCell(crearCeldaPdf(v.getSerie() + "-" + v.getNumero()));
            table.addCell(crearCeldaPdf(v.getClienteDenominacion()));
            table.addCell(crearCeldaPdf(v.getMetodoPago() != null ? v.getMetodoPago() : "EFECTIVO"));
            table.addCell(crearCeldaPdfRight(moneda + v.getTotal()));
            table.addCell(crearCeldaPdf(v.getEstado()));
        }
        document.add(table);
    }

    /**
     * OPTIMIZADO: Ahora filtra en la BD, no en Java
     */
    private void generarPdfCaja(Document document, LocalDate inicio, LocalDate fin, com.libreria.sistema.model.Configuracion config) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 1, 4, 2, 2});
        agregarCabeceraPdf(table, config, "FECHA", "TIPO", "CONCEPTO", "MONTO", "USUARIO");

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";

        // OPTIMIZACIÓN: Consulta filtrada en BD
        List<MovimientoCaja> lista = cajaRepository.findByFechaBetweenDates(inicio, fin);
        log.debug("Movimientos de caja obtenidos para PDF: {} registros", lista.size());

        for (MovimientoCaja m : lista) {
            table.addCell(crearCeldaPdf(m.getFecha().toString().replace("T", " ")));
            table.addCell(crearCeldaPdf(m.getTipo()));
            table.addCell(crearCeldaPdf(m.getConcepto()));
            table.addCell(crearCeldaPdfRight(moneda + m.getMonto()));
            table.addCell(crearCeldaPdf(m.getUsuario() != null ? m.getUsuario().getUsername() : "-"));
        }
        document.add(table);
    }

    private void generarPdfInventario(Document document, com.libreria.sistema.model.Configuracion config) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 4, 1, 2, 2});
        agregarCabeceraPdf(table, config, "CODIGO", "PRODUCTO", "STOCK", "P. VENTA", "VALORIZADO");

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";

        List<Producto> lista = productoRepository.findByActivoTrue();
        for (Producto p : lista) {
            table.addCell(crearCeldaPdf(p.getCodigoInterno()));
            table.addCell(crearCeldaPdf(p.getNombre()));
            table.addCell(crearCeldaPdfCenter(String.valueOf(p.getStockActual())));
            table.addCell(crearCeldaPdfRight(moneda + p.getPrecioVenta()));

            double valor = p.getStockActual() * (p.getPrecioCompra()!=null?p.getPrecioCompra().doubleValue():0);
            table.addCell(crearCeldaPdfRight(moneda + String.format("%.2f", valor)));
        }
        document.add(table);
    }

    private void generarPdfUsuarios(Document document, com.libreria.sistema.model.Configuracion config) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 3, 3, 2});
        agregarCabeceraPdf(table, config, "USUARIO", "NOMBRE COMPLETO", "ROLES", "ESTADO");

        List<Usuario> lista = usuarioRepository.findAll();
        for (Usuario u : lista) {
            table.addCell(crearCeldaPdf(u.getUsername()));
            table.addCell(crearCeldaPdf(u.getNombreCompleto()));

            StringBuilder roles = new StringBuilder();
            u.getRoles().forEach(r -> roles.append(r.getNombre()).append(" "));
            table.addCell(crearCeldaPdf(roles.toString()));

            table.addCell(crearCeldaPdf(u.isActivo() ? "ACTIVO" : "INACTIVO"));
        }
        document.add(table);
    }

    private void agregarCabeceraPdf(PdfPTable table, com.libreria.sistema.model.Configuracion config, String... headers) {
        Color colorOscuro = parseColor(config.getColorOscuro(), Color.DARK_GRAY);
        for(String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
            cell.setBackgroundColor(colorOscuro);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private PdfPCell crearCeldaPdf(String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cell.setPadding(4);
        return cell;
    }

    private PdfPCell crearCeldaPdfRight(String texto) {
        PdfPCell cell = crearCeldaPdf(texto);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private PdfPCell crearCeldaPdfCenter(String texto) {
        PdfPCell cell = crearCeldaPdf(texto);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    // ==========================================
    //    NUEVOS REPORTES AVANZADOS - EXCEL
    // ==========================================

    /**
     * Reporte de ventas agrupadas por usuario/cajero
     */
    private void generarExcelVentasPorUsuario(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle, com.libreria.sistema.model.Configuracion config) {
        crearFilaCabecera(sheet, headerStyle, "USUARIO", "NOMBRE", "CANT. VENTAS", "TOTAL VENTAS", "PROMEDIO");

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";
        List<Venta> lista = ventaRepository.findByFechaEmisionBetween(inicio, fin);

        // Agrupar por usuario
        var ventasPorUsuario = lista.stream()
                .filter(v -> v.getUsuario() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        v -> v.getUsuario().getUsername(),
                        java.util.stream.Collectors.toList()
                ));

        int rowIdx = 1;
        for (var entry : ventasPorUsuario.entrySet()) {
            String username = entry.getKey();
            List<Venta> ventas = entry.getValue();
            java.math.BigDecimal total = ventas.stream()
                    .map(Venta::getTotal)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal promedio = ventas.isEmpty() ? java.math.BigDecimal.ZERO :
                    total.divide(new java.math.BigDecimal(ventas.size()), 2, java.math.RoundingMode.HALF_UP);

            String nombreCompleto = ventas.get(0).getUsuario().getNombreCompleto();

            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, username, dataStyle);
            crearCelda(row, 1, nombreCompleto, dataStyle);
            crearCelda(row, 2, String.valueOf(ventas.size()), dataStyle);
            crearCelda(row, 3, moneda + total, dataStyle);
            crearCelda(row, 4, moneda + promedio, dataStyle);
        }
    }

    /**
     * Reporte de productos más vendidos
     */
    private void generarExcelVentasPorProducto(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle, com.libreria.sistema.model.Configuracion config) {
        crearFilaCabecera(sheet, headerStyle, "CODIGO", "PRODUCTO", "CANT. VENDIDA", "TOTAL VENTAS", "% DEL TOTAL");

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";
        List<Venta> ventas = ventaRepository.findByFechaEmisionBetween(inicio, fin);

        // Extraer todos los detalles y agrupar por producto
        var productosVendidos = ventas.stream()
                .flatMap(v -> v.getItems().stream())
                .collect(java.util.stream.Collectors.groupingBy(
                        d -> d.getProducto().getId(),
                        java.util.stream.Collectors.toList()
                ));

        java.math.BigDecimal totalGeneral = ventas.stream()
                .map(Venta::getTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        int rowIdx = 1;
        for (var entry : productosVendidos.entrySet()) {
            var detalles = entry.getValue();
            if (detalles.isEmpty()) continue;

            Producto prod = detalles.get(0).getProducto();
            java.math.BigDecimal cantidadTotal = detalles.stream()
                    .map(d -> d.getCantidad())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal montoTotal = detalles.stream()
                    .map(d -> d.getSubtotal())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            double porcentaje = totalGeneral.compareTo(java.math.BigDecimal.ZERO) > 0 ?
                    montoTotal.divide(totalGeneral, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100 : 0;

            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, prod.getCodigoInterno(), dataStyle);
            crearCelda(row, 1, prod.getNombre(), dataStyle);
            crearCelda(row, 2, cantidadTotal.toString(), dataStyle);
            crearCelda(row, 3, moneda + montoTotal, dataStyle);
            crearCelda(row, 4, String.format("%.2f%%", porcentaje), dataStyle);
        }
    }

    /**
     * Resumen financiero consolidado
     */
    private void generarExcelResumenFinanciero(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle, com.libreria.sistema.model.Configuracion config) {
        crearFilaCabecera(sheet, headerStyle, "CONCEPTO", "MONTO");

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";

        List<Venta> ventas = ventaRepository.findByFechaEmisionBetween(inicio, fin);
        List<MovimientoCaja> movimientos = cajaRepository.findByFechaBetweenDates(inicio, fin);

        // Calcular totales
        java.math.BigDecimal totalVentas = ventas.stream()
                .map(Venta::getTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal totalIgv = ventas.stream()
                .map(v -> v.getTotalIgv() != null ? v.getTotalIgv() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal ventasContado = ventas.stream()
                .filter(v -> "CONTADO".equals(v.getFormaPago()))
                .map(Venta::getTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal ventasCredito = ventas.stream()
                .filter(v -> "CREDITO".equals(v.getFormaPago()))
                .map(Venta::getTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal ingresos = movimientos.stream()
                .filter(m -> "INGRESO".equals(m.getTipo()))
                .map(MovimientoCaja::getMonto)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal egresos = movimientos.stream()
                .filter(m -> "EGRESO".equals(m.getTipo()))
                .map(MovimientoCaja::getMonto)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        int rowIdx = 1;
        crearFilaResumen(sheet, rowIdx++, "Total Ventas", moneda + totalVentas, dataStyle);
        crearFilaResumen(sheet, rowIdx++, "Total IGV", moneda + totalIgv, dataStyle);
        crearFilaResumen(sheet, rowIdx++, "Ventas al Contado", moneda + ventasContado, dataStyle);
        crearFilaResumen(sheet, rowIdx++, "Ventas a Crédito", moneda + ventasCredito, dataStyle);
        crearFilaResumen(sheet, rowIdx++, "Cantidad de Ventas", String.valueOf(ventas.size()), dataStyle);
        crearFilaResumen(sheet, rowIdx++, "Ingresos Caja", moneda + ingresos, dataStyle);
        crearFilaResumen(sheet, rowIdx++, "Egresos Caja", moneda + egresos, dataStyle);
        crearFilaResumen(sheet, rowIdx++, "Balance Neto", moneda + ingresos.subtract(egresos), dataStyle);
    }

    /**
     * Productos con stock bajo (por debajo del mínimo configurado)
     */
    private void generarExcelStockBajo(Sheet sheet, CellStyle headerStyle, CellStyle dataStyle, com.libreria.sistema.model.Configuracion config) {
        crearFilaCabecera(sheet, headerStyle, "CODIGO", "PRODUCTO", "STOCK ACTUAL", "STOCK MIN", "DIFERENCIA");

        Integer stockMinimo = config.getStockMinimo() != null ? config.getStockMinimo() : 5;
        List<Producto> lista = productoRepository.findByActivoTrue();

        int rowIdx = 1;
        for (Producto p : lista) {
            if (p.getStockActual() <= stockMinimo) {
                Row row = sheet.createRow(rowIdx++);
                crearCelda(row, 0, p.getCodigoInterno(), dataStyle);
                crearCelda(row, 1, p.getNombre(), dataStyle);
                crearCelda(row, 2, String.valueOf(p.getStockActual()), dataStyle);
                crearCelda(row, 3, String.valueOf(stockMinimo), dataStyle);
                crearCelda(row, 4, String.valueOf(p.getStockActual() - stockMinimo), dataStyle);
            }
        }
    }

    private void crearFilaResumen(Sheet sheet, int rowIdx, String concepto, String valor, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        crearCelda(row, 0, concepto, style);
        crearCelda(row, 1, valor, style);
    }

    // ==========================================
    //    NUEVOS REPORTES AVANZADOS - PDF
    // ==========================================

    /**
     * PDF: Ventas por usuario/cajero
     */
    private void generarPdfVentasPorUsuario(Document document, LocalDate inicio, LocalDate fin, com.libreria.sistema.model.Configuracion config) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 3, 2, 2, 2});

        agregarCabeceraPdf(table, config, "USUARIO", "NOMBRE", "VENTAS", "TOTAL", "PROMEDIO");

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";
        List<Venta> lista = ventaRepository.findByFechaEmisionBetween(inicio, fin);

        var ventasPorUsuario = lista.stream()
                .filter(v -> v.getUsuario() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        v -> v.getUsuario().getUsername(),
                        java.util.stream.Collectors.toList()
                ));

        for (var entry : ventasPorUsuario.entrySet()) {
            String username = entry.getKey();
            List<Venta> ventas = entry.getValue();
            java.math.BigDecimal total = ventas.stream()
                    .map(Venta::getTotal)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal promedio = ventas.isEmpty() ? java.math.BigDecimal.ZERO :
                    total.divide(new java.math.BigDecimal(ventas.size()), 2, java.math.RoundingMode.HALF_UP);

            String nombreCompleto = ventas.get(0).getUsuario().getNombreCompleto();

            table.addCell(crearCeldaPdf(username));
            table.addCell(crearCeldaPdf(nombreCompleto));
            table.addCell(crearCeldaPdfCenter(String.valueOf(ventas.size())));
            table.addCell(crearCeldaPdfRight(moneda + total));
            table.addCell(crearCeldaPdfRight(moneda + promedio));
        }
        document.add(table);
    }

    /**
     * PDF: Productos más vendidos
     */
    private void generarPdfVentasPorProducto(Document document, LocalDate inicio, LocalDate fin, com.libreria.sistema.model.Configuracion config) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 4, 2, 2, 2});

        agregarCabeceraPdf(table, config, "CODIGO", "PRODUCTO", "CANTIDAD", "TOTAL", "%");

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";
        List<Venta> ventas = ventaRepository.findByFechaEmisionBetween(inicio, fin);

        var productosVendidos = ventas.stream()
                .flatMap(v -> v.getItems().stream())
                .collect(java.util.stream.Collectors.groupingBy(
                        d -> d.getProducto().getId(),
                        java.util.stream.Collectors.toList()
                ));

        java.math.BigDecimal totalGeneral = ventas.stream()
                .map(Venta::getTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        for (var entry : productosVendidos.entrySet()) {
            var detalles = entry.getValue();
            if (detalles.isEmpty()) continue;

            Producto prod = detalles.get(0).getProducto();
            java.math.BigDecimal cantidadTotal = detalles.stream()
                    .map(d -> d.getCantidad())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal montoTotal = detalles.stream()
                    .map(d -> d.getSubtotal())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            double porcentaje = totalGeneral.compareTo(java.math.BigDecimal.ZERO) > 0 ?
                    montoTotal.divide(totalGeneral, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100 : 0;

            table.addCell(crearCeldaPdf(prod.getCodigoInterno()));
            table.addCell(crearCeldaPdf(prod.getNombre()));
            table.addCell(crearCeldaPdfCenter(cantidadTotal.toString()));
            table.addCell(crearCeldaPdfRight(moneda + montoTotal));
            table.addCell(crearCeldaPdfCenter(String.format("%.2f%%", porcentaje)));
        }
        document.add(table);
    }

    /**
     * PDF: Resumen financiero consolidado
     */
    private void generarPdfResumenFinanciero(Document document, LocalDate inicio, LocalDate fin, com.libreria.sistema.model.Configuracion config) throws DocumentException {
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";

        List<Venta> ventas = ventaRepository.findByFechaEmisionBetween(inicio, fin);
        List<MovimientoCaja> movimientos = cajaRepository.findByFechaBetweenDates(inicio, fin);

        java.math.BigDecimal totalVentas = ventas.stream()
                .map(Venta::getTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal totalIgv = ventas.stream()
                .map(v -> v.getTotalIgv() != null ? v.getTotalIgv() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal ventasContado = ventas.stream()
                .filter(v -> "CONTADO".equals(v.getFormaPago()))
                .map(Venta::getTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal ventasCredito = ventas.stream()
                .filter(v -> "CREDITO".equals(v.getFormaPago()))
                .map(Venta::getTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal ingresos = movimientos.stream()
                .filter(m -> "INGRESO".equals(m.getTipo()))
                .map(MovimientoCaja::getMonto)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal egresos = movimientos.stream()
                .filter(m -> "EGRESO".equals(m.getTipo()))
                .map(MovimientoCaja::getMonto)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // Crear tabla de resumen
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(60);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.setWidths(new float[]{3, 2});

        agregarCabeceraPdf(table, config, "CONCEPTO", "MONTO");

        agregarFilaResumenPdf(table, "Total Ventas", moneda + totalVentas);
        agregarFilaResumenPdf(table, "Total IGV", moneda + totalIgv);
        agregarFilaResumenPdf(table, "Ventas al Contado", moneda + ventasContado);
        agregarFilaResumenPdf(table, "Ventas a Crédito", moneda + ventasCredito);
        agregarFilaResumenPdf(table, "Cantidad de Ventas", String.valueOf(ventas.size()));
        agregarFilaResumenPdf(table, "Ingresos Caja", moneda + ingresos);
        agregarFilaResumenPdf(table, "Egresos Caja", moneda + egresos);

        // Fila de balance con estilo destacado
        PdfPCell cellConcepto = new PdfPCell(new Phrase("BALANCE NETO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        cellConcepto.setPadding(6);
        cellConcepto.setBackgroundColor(new Color(230, 230, 230));
        table.addCell(cellConcepto);

        java.math.BigDecimal balance = ingresos.subtract(egresos);
        Color colorBalance = balance.compareTo(java.math.BigDecimal.ZERO) >= 0 ? new Color(0, 128, 0) : Color.RED;
        PdfPCell cellMonto = new PdfPCell(new Phrase(moneda + balance, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, colorBalance)));
        cellMonto.setPadding(6);
        cellMonto.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cellMonto.setBackgroundColor(new Color(230, 230, 230));
        table.addCell(cellMonto);

        document.add(table);
    }

    /**
     * PDF: Productos con stock bajo
     */
    private void generarPdfStockBajo(Document document, com.libreria.sistema.model.Configuracion config) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 4, 2, 2, 2});

        agregarCabeceraPdf(table, config, "CODIGO", "PRODUCTO", "STOCK", "MINIMO", "DIFERENCIA");

        Integer stockMinimo = config.getStockMinimo() != null ? config.getStockMinimo() : 5;
        List<Producto> lista = productoRepository.findByActivoTrue();

        for (Producto p : lista) {
            if (p.getStockActual() <= stockMinimo) {
                table.addCell(crearCeldaPdf(p.getCodigoInterno()));
                table.addCell(crearCeldaPdf(p.getNombre()));

                // Colorear stock según nivel
                int stock = p.getStockActual();
                Color colorStock = stock <= 0 ? Color.RED : (stock <= stockMinimo / 2 ? Color.ORANGE : Color.BLACK);
                PdfPCell cellStock = new PdfPCell(new Phrase(String.valueOf(stock), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, colorStock)));
                cellStock.setPadding(4);
                cellStock.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cellStock);

                table.addCell(crearCeldaPdfCenter(String.valueOf(stockMinimo)));
                table.addCell(crearCeldaPdfCenter(String.valueOf(stock - stockMinimo)));
            }
        }
        document.add(table);
    }

    private void agregarFilaResumenPdf(PdfPTable table, String concepto, String valor) {
        table.addCell(crearCeldaPdf(concepto));
        table.addCell(crearCeldaPdfRight(valor));
    }

    // ==========================================
    //    IMPRESIÓN DE VENTAS (A4 y TICKET)
    // ==========================================

    /**
     * Genera PDF en formato TICKET (80mm) para impresoras térmicas.
     * Diseño minimalista, compacto, con fuente pequeña.
     */
    public void generarPdfTicketVenta(Venta venta, com.libreria.sistema.model.Configuracion config, OutputStream outputStream) throws DocumentException {
        // Tamaño 80mm de ancho, alto automático (inicialmente 200mm, se ajusta)
        float anchoMm = config.getAnchoTicketMm() != null ? config.getAnchoTicketMm() : 80;
        float anchoPuntos = anchoMm * 2.83465f; // mm a puntos (1mm = 2.83465 puntos)

        // Calcular altura aproximada según número de items
        int numItems = venta.getItems() != null ? venta.getItems().size() : 0;
        float alturaBase = 180; // Altura base en mm
        float alturaPorItem = 8; // mm por cada item
        float alturaTotal = alturaBase + (numItems * alturaPorItem);
        float alturaPuntos = alturaTotal * 2.83465f;

        Rectangle tamanoTicket = new Rectangle(anchoPuntos, alturaPuntos);
        Document document = new Document(tamanoTicket, 5, 5, 10, 10);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        // Fuentes para ticket
        com.lowagie.text.Font fontNormal = FontFactory.getFont(FontFactory.COURIER, 8);
        com.lowagie.text.Font fontBold = FontFactory.getFont(FontFactory.COURIER_BOLD, 9);
        com.lowagie.text.Font fontTitulo = FontFactory.getFont(FontFactory.COURIER_BOLD, 10);
        com.lowagie.text.Font fontPequena = FontFactory.getFont(FontFactory.COURIER, 7);

        // === LOGO (centrado) ===
        if (config.getMostrarLogoEnTicket() != null && config.getMostrarLogoEnTicket() && config.getLogoBase64() != null) {
            try {
                byte[] logoBytes = Base64.getDecoder().decode(config.getLogoBase64());
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(anchoPuntos * 0.6f, 50);
                logo.setAlignment(Element.ALIGN_CENTER);
                document.add(logo);
            } catch (Exception e) {
                log.warn("No se pudo agregar logo al ticket: {}", e.getMessage());
            }
        }

        // === DATOS DE EMPRESA (centrado) ===
        Paragraph pEmpresa = new Paragraph(config.getNombreEmpresa(), fontTitulo);
        pEmpresa.setAlignment(Element.ALIGN_CENTER);
        document.add(pEmpresa);

        Paragraph pRuc = new Paragraph("RUC: " + config.getRuc(), fontNormal);
        pRuc.setAlignment(Element.ALIGN_CENTER);
        document.add(pRuc);

        if (config.getDireccion() != null) {
            Paragraph pDir = new Paragraph(config.getDireccion(), fontPequena);
            pDir.setAlignment(Element.ALIGN_CENTER);
            document.add(pDir);
        }

        if (config.getTelefono() != null) {
            Paragraph pTel = new Paragraph("Tel: " + config.getTelefono(), fontPequena);
            pTel.setAlignment(Element.ALIGN_CENTER);
            document.add(pTel);
        }

        document.add(new Paragraph(" ", fontPequena));

        // === TIPO DE COMPROBANTE ===
        Paragraph pTipo = new Paragraph(venta.getTipoComprobante() + " DE VENTA", fontBold);
        pTipo.setAlignment(Element.ALIGN_CENTER);
        document.add(pTipo);

        String numeroFormateado = String.format("%s-%06d", venta.getSerie(), venta.getNumero());
        Paragraph pNumero = new Paragraph(numeroFormateado, fontBold);
        pNumero.setAlignment(Element.ALIGN_CENTER);
        document.add(pNumero);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        Paragraph pFecha = new Paragraph(venta.getFechaEmision().format(formatter), fontNormal);
        pFecha.setAlignment(Element.ALIGN_CENTER);
        document.add(pFecha);

        // === SEPARADOR ===
        document.add(crearSeparadorTicket(anchoPuntos));

        // === DATOS DEL CLIENTE ===
        Paragraph pCliente = new Paragraph("CLIENTE: " + venta.getClienteDenominacion(), fontNormal);
        document.add(pCliente);

        if (venta.getClienteNumeroDocumento() != null && !venta.getClienteNumeroDocumento().isEmpty()) {
            Paragraph pDoc = new Paragraph("DOC: " + venta.getClienteNumeroDocumento(), fontNormal);
            document.add(pDoc);
        }

        // === SEPARADOR ===
        document.add(crearSeparadorTicket(anchoPuntos));

        // === TABLA DE PRODUCTOS (compacta) ===
        PdfPTable tablaItems = new PdfPTable(3);
        tablaItems.setWidthPercentage(100);
        tablaItems.setWidths(new float[]{5, 1.5f, 2});

        // Cabecera de tabla
        tablaItems.addCell(crearCeldaTicket("DESCRIPCION", fontPequena, Element.ALIGN_LEFT, true));
        tablaItems.addCell(crearCeldaTicket("CANT", fontPequena, Element.ALIGN_CENTER, true));
        tablaItems.addCell(crearCeldaTicket("IMPORTE", fontPequena, Element.ALIGN_RIGHT, true));

        // Items
        if (venta.getItems() != null) {
            for (var item : venta.getItems()) {
                String descripcion = item.getDescripcion();
                if (descripcion != null && descripcion.length() > 25) {
                    descripcion = descripcion.substring(0, 25) + "...";
                }
                tablaItems.addCell(crearCeldaTicket(descripcion, fontPequena, Element.ALIGN_LEFT, false));
                tablaItems.addCell(crearCeldaTicket(item.getCantidad().toString(), fontPequena, Element.ALIGN_CENTER, false));
                tablaItems.addCell(crearCeldaTicket(String.format("%.2f", item.getSubtotal()), fontPequena, Element.ALIGN_RIGHT, false));
            }
        }
        document.add(tablaItems);

        // === SEPARADOR ===
        document.add(crearSeparadorTicket(anchoPuntos));

        // === TOTALES ===
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";

        if (venta.getTotalGravada() != null && venta.getTotalGravada().compareTo(java.math.BigDecimal.ZERO) > 0) {
            PdfPTable tablaTotales = new PdfPTable(2);
            tablaTotales.setWidthPercentage(100);
            tablaTotales.setWidths(new float[]{3, 2});

            tablaTotales.addCell(crearCeldaTicket("Op. Gravada:", fontNormal, Element.ALIGN_LEFT, false));
            tablaTotales.addCell(crearCeldaTicket(moneda + " " + String.format("%.2f", venta.getTotalGravada()), fontNormal, Element.ALIGN_RIGHT, false));

            tablaTotales.addCell(crearCeldaTicket(configuracionService.getIgvLabel() + ":", fontNormal, Element.ALIGN_LEFT, false));
            tablaTotales.addCell(crearCeldaTicket(moneda + " " + String.format("%.2f", venta.getTotalIgv()), fontNormal, Element.ALIGN_RIGHT, false));

            document.add(tablaTotales);
        }

        Paragraph pTotal = new Paragraph("TOTAL: " + moneda + " " + String.format("%.2f", venta.getTotal()), fontBold);
        pTotal.setAlignment(Element.ALIGN_RIGHT);
        document.add(pTotal);

        // === MÉTODO DE PAGO ===
        if (venta.getMetodoPago() != null) {
            Paragraph pMetodo = new Paragraph("Pago: " + venta.getMetodoPago(), fontNormal);
            pMetodo.setAlignment(Element.ALIGN_CENTER);
            document.add(pMetodo);
        }

        // === INFORMACIÓN DE CRÉDITO (si aplica) ===
        if ("CREDITO".equals(venta.getFormaPago())) {
            document.add(new Paragraph(" ", fontPequena));
            Paragraph pCredito = new Paragraph("*** VENTA A CREDITO ***", fontBold);
            pCredito.setAlignment(Element.ALIGN_CENTER);
            document.add(pCredito);

            if (venta.getMontoPagado() != null) {
                Paragraph pAbono = new Paragraph("Abonado: " + moneda + " " + String.format("%.2f", venta.getMontoPagado()), fontNormal);
                pAbono.setAlignment(Element.ALIGN_CENTER);
                document.add(pAbono);
            }

            if (venta.getSaldoPendiente() != null) {
                Paragraph pSaldo = new Paragraph("Saldo: " + moneda + " " + String.format("%.2f", venta.getSaldoPendiente()), fontBold);
                pSaldo.setAlignment(Element.ALIGN_CENTER);
                document.add(pSaldo);
            }
        }

        document.add(new Paragraph(" ", fontPequena));

        // === CUENTAS BANCARIAS ===
        if (config.getCuentasBancarias() != null && !config.getCuentasBancarias().trim().isEmpty()) {
            document.add(crearSeparadorTicket(anchoPuntos));
            Paragraph pBancos = new Paragraph("CUENTAS BANCARIAS:", fontBold);
            pBancos.setAlignment(Element.ALIGN_CENTER);
            document.add(pBancos);

            String[] lineasBanco = config.getCuentasBancarias().split("\n");
            for (String linea : lineasBanco) {
                Paragraph pLinea = new Paragraph(linea.trim(), fontPequena);
                pLinea.setAlignment(Element.ALIGN_CENTER);
                document.add(pLinea);
            }
        }

        // === MENSAJE DE PIE ===
        document.add(new Paragraph(" ", fontPequena));
        String mensajePie = config.getMensajePieTicket() != null ? config.getMensajePieTicket() : "Gracias por su compra. Vuelva pronto!";
        Paragraph pMensaje = new Paragraph(mensajePie, fontNormal);
        pMensaje.setAlignment(Element.ALIGN_CENTER);
        document.add(pMensaje);

        // === USUARIO CAJERO ===
        if (venta.getUsuario() != null) {
            Paragraph pCajero = new Paragraph("Atendido por: " + venta.getUsuario().getNombreCompleto(), fontPequena);
            pCajero.setAlignment(Element.ALIGN_CENTER);
            document.add(pCajero);
        }

        document.close();
    }

    /**
     * Genera PDF en formato A4 para impresión estándar.
     * Diseño formal para facturas y boletas oficiales.
     */
    public void generarPdfA4Venta(Venta venta, com.libreria.sistema.model.Configuracion config, OutputStream outputStream) throws DocumentException {
        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        // Fuentes
        com.lowagie.text.Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        com.lowagie.text.Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        com.lowagie.text.Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        com.lowagie.text.Font fontEmpresa = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        com.lowagie.text.Font fontPequena = FontFactory.getFont(FontFactory.HELVETICA, 8);

        Color colorPrimario = parseColor(config.getColorPrimario(), Color.BLUE);

        // === CABECERA (3 columnas: Logo, Empresa, Documento) ===
        int numCols = (config.getMostrarLogoEnReportes() != null && config.getMostrarLogoEnReportes() && config.getLogoBase64() != null) ? 3 : 2;
        PdfPTable tablaHeader = new PdfPTable(numCols);
        tablaHeader.setWidthPercentage(100);
        if (numCols == 3) {
            tablaHeader.setWidths(new float[]{1, 3, 2});
        } else {
            tablaHeader.setWidths(new float[]{3, 2});
        }

        // Logo
        if (numCols == 3) {
            try {
                byte[] logoBytes = Base64.getDecoder().decode(config.getLogoBase64());
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(80, 80);
                PdfPCell cellLogo = new PdfPCell(logo);
                cellLogo.setBorder(Rectangle.NO_BORDER);
                cellLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
                tablaHeader.addCell(cellLogo);
            } catch (Exception e) {
                PdfPCell cellEmpty = new PdfPCell();
                cellEmpty.setBorder(Rectangle.NO_BORDER);
                tablaHeader.addCell(cellEmpty);
            }
        }

        // Datos de empresa
        PdfPCell cellEmpresa = new PdfPCell();
        cellEmpresa.setBorder(Rectangle.NO_BORDER);
        cellEmpresa.addElement(new Paragraph(config.getNombreEmpresa(), fontEmpresa));
        cellEmpresa.addElement(new Paragraph("RUC: " + config.getRuc(), fontBold));
        if (config.getDireccion() != null) {
            cellEmpresa.addElement(new Paragraph(config.getDireccion(), fontNormal));
        }
        if (config.getTelefono() != null) {
            cellEmpresa.addElement(new Paragraph("Tel: " + config.getTelefono(), fontNormal));
        }
        if (config.getEmail() != null) {
            cellEmpresa.addElement(new Paragraph("Email: " + config.getEmail(), fontPequena));
        }
        tablaHeader.addCell(cellEmpresa);

        // Recuadro del documento
        PdfPCell cellDocumento = new PdfPCell();
        cellDocumento.setBorderColor(colorPrimario);
        cellDocumento.setBorderWidth(2);
        cellDocumento.setPadding(10);
        cellDocumento.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pRuc = new Paragraph("R.U.C. " + config.getRuc(), fontBold);
        pRuc.setAlignment(Element.ALIGN_CENTER);
        cellDocumento.addElement(pRuc);

        Paragraph pTipoDoc = new Paragraph(venta.getTipoComprobante(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, colorPrimario));
        pTipoDoc.setAlignment(Element.ALIGN_CENTER);
        cellDocumento.addElement(pTipoDoc);

        String numeroDoc = venta.getSerie() + " - " + String.format("%06d", venta.getNumero());
        Paragraph pNumDoc = new Paragraph(numeroDoc, fontBold);
        pNumDoc.setAlignment(Element.ALIGN_CENTER);
        cellDocumento.addElement(pNumDoc);

        tablaHeader.addCell(cellDocumento);
        document.add(tablaHeader);

        document.add(new Paragraph(" "));

        // === DATOS DEL CLIENTE ===
        PdfPTable tablaCliente = new PdfPTable(2);
        tablaCliente.setWidthPercentage(100);
        tablaCliente.setWidths(new float[]{1, 1});

        PdfPCell cellCliente1 = new PdfPCell();
        cellCliente1.setBorder(Rectangle.BOX);
        cellCliente1.setPadding(8);
        cellCliente1.addElement(new Paragraph("Señor(es): " + venta.getClienteDenominacion(), fontNormal));
        if (venta.getClienteDireccion() != null) {
            cellCliente1.addElement(new Paragraph("Dirección: " + venta.getClienteDireccion(), fontNormal));
        }
        cellCliente1.addElement(new Paragraph("Tipo Moneda: SOLES", fontNormal));
        tablaCliente.addCell(cellCliente1);

        PdfPCell cellCliente2 = new PdfPCell();
        cellCliente2.setBorder(Rectangle.BOX);
        cellCliente2.setPadding(8);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        cellCliente2.addElement(new Paragraph("Fecha Emisión: " + venta.getFechaEmision().format(formatter), fontNormal));
        cellCliente2.addElement(new Paragraph("RUC/DNI: " + (venta.getClienteNumeroDocumento() != null ? venta.getClienteNumeroDocumento() : "-"), fontNormal));
        cellCliente2.addElement(new Paragraph("Condición: " + (venta.getFormaPago() != null ? venta.getFormaPago() : "CONTADO"), fontNormal));
        cellCliente2.addElement(new Paragraph("Método Pago: " + (venta.getMetodoPago() != null ? venta.getMetodoPago() : "EFECTIVO"), fontNormal));

        if ("CREDITO".equals(venta.getFormaPago()) && venta.getFechaVencimiento() != null) {
            Paragraph pVenc = new Paragraph("Vencimiento: " + venta.getFechaVencimiento().format(formatter), FontFactory.getFont(FontFactory.HELVETICA, 10, Color.RED));
            cellCliente2.addElement(pVenc);
        }

        tablaCliente.addCell(cellCliente2);
        document.add(tablaCliente);

        document.add(new Paragraph(" "));

        // === TABLA DE PRODUCTOS ===
        PdfPTable tablaItems = new PdfPTable(5);
        tablaItems.setWidthPercentage(100);
        tablaItems.setWidths(new float[]{1, 1, 5, 1.5f, 1.5f});

        // Cabecera
        Color colorCabecera = parseColor(config.getColorOscuro(), Color.DARK_GRAY);
        String[] headers = {"CANT.", "UNID.", "DESCRIPCIÓN", "P. UNIT", "IMPORTE"};
        for (String h : headers) {
            PdfPCell cellH = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
            cellH.setBackgroundColor(colorCabecera);
            cellH.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellH.setPadding(6);
            tablaItems.addCell(cellH);
        }

        // Items
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";
        if (venta.getItems() != null) {
            for (var item : venta.getItems()) {
                tablaItems.addCell(crearCeldaPdfCenter(item.getCantidad().toString()));
                tablaItems.addCell(crearCeldaPdfCenter("NIU"));
                tablaItems.addCell(crearCeldaPdf(item.getDescripcion()));
                tablaItems.addCell(crearCeldaPdfRight(String.format("%.2f", item.getPrecioUnitario())));
                tablaItems.addCell(crearCeldaPdfRight(String.format("%.2f", item.getSubtotal())));
            }
        }

        document.add(tablaItems);

        document.add(new Paragraph(" "));

        // === ÁREA DE TOTALES Y CRÉDITO ===
        PdfPTable tablaFinal = new PdfPTable(2);
        tablaFinal.setWidthPercentage(100);
        tablaFinal.setWidths(new float[]{3, 2});

        // Columna izquierda: Info crédito + QR
        PdfPCell cellIzq = new PdfPCell();
        cellIzq.setBorder(Rectangle.NO_BORDER);

        if ("CREDITO".equals(venta.getFormaPago())) {
            PdfPTable tablaCredito = new PdfPTable(1);
            tablaCredito.setWidthPercentage(80);

            PdfPCell cellCredito = new PdfPCell();
            cellCredito.setBorder(Rectangle.BOX);
            cellCredito.setPadding(8);
            cellCredito.setBackgroundColor(new Color(255, 250, 240));

            cellCredito.addElement(new Paragraph("INFORMACIÓN DEL CRÉDITO:", fontBold));
            if (venta.getMontoPagado() != null) {
                cellCredito.addElement(new Paragraph("Monto Abonado Inicial: " + moneda + " " + String.format("%.2f", venta.getMontoPagado()), fontNormal));
            }
            if (venta.getSaldoPendiente() != null) {
                Paragraph pSaldo = new Paragraph("Saldo Pendiente: " + moneda + " " + String.format("%.2f", venta.getSaldoPendiente()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.RED));
                cellCredito.addElement(pSaldo);
            }

            tablaCredito.addCell(cellCredito);
            cellIzq.addElement(tablaCredito);
        }

        // QR placeholder
        cellIzq.addElement(new Paragraph(" "));
        Paragraph pQR = new Paragraph("[QR CODE]", fontPequena);
        pQR.setAlignment(Element.ALIGN_CENTER);
        cellIzq.addElement(pQR);

        Paragraph pSunat = new Paragraph("Representación impresa del comprobante electrónico.\nConsulte en www.sunat.gob.pe", fontPequena);
        pSunat.setAlignment(Element.ALIGN_CENTER);
        cellIzq.addElement(pSunat);

        tablaFinal.addCell(cellIzq);

        // Columna derecha: Totales
        PdfPCell cellDer = new PdfPCell();
        cellDer.setBorder(Rectangle.NO_BORDER);

        PdfPTable tablaTotales = new PdfPTable(2);
        tablaTotales.setWidthPercentage(100);
        tablaTotales.setWidths(new float[]{3, 2});

        if (venta.getTotalGravada() != null) {
            tablaTotales.addCell(crearCeldaPdf("Op. Gravada:"));
            tablaTotales.addCell(crearCeldaPdfRight(moneda + " " + String.format("%.2f", venta.getTotalGravada())));
        }

        if (venta.getTotalIgv() != null) {
            tablaTotales.addCell(crearCeldaPdf(configuracionService.getIgvLabel() + ":"));
            tablaTotales.addCell(crearCeldaPdfRight(moneda + " " + String.format("%.2f", venta.getTotalIgv())));
        }

        // Total final
        PdfPCell cellTotalLabel = new PdfPCell(new Phrase("IMPORTE TOTAL:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        cellTotalLabel.setBorderWidthTop(2);
        cellTotalLabel.setPadding(6);
        tablaTotales.addCell(cellTotalLabel);

        PdfPCell cellTotalValor = new PdfPCell(new Phrase(moneda + " " + String.format("%.2f", venta.getTotal()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        cellTotalValor.setBorderWidthTop(2);
        cellTotalValor.setPadding(6);
        cellTotalValor.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tablaTotales.addCell(cellTotalValor);

        cellDer.addElement(tablaTotales);
        tablaFinal.addCell(cellDer);

        document.add(tablaFinal);

        // === CUENTAS BANCARIAS (si existen) ===
        if (config.getCuentasBancarias() != null && !config.getCuentasBancarias().trim().isEmpty()) {
            document.add(new Paragraph(" "));

            PdfPTable tablaBancos = new PdfPTable(1);
            tablaBancos.setWidthPercentage(60);
            tablaBancos.setHorizontalAlignment(Element.ALIGN_CENTER);

            PdfPCell cellBancos = new PdfPCell();
            cellBancos.setBorder(Rectangle.BOX);
            cellBancos.setPadding(8);
            cellBancos.setBackgroundColor(new Color(248, 249, 250));

            cellBancos.addElement(new Paragraph("CUENTAS BANCARIAS:", fontBold));
            String[] lineasBanco = config.getCuentasBancarias().split("\n");
            for (String linea : lineasBanco) {
                cellBancos.addElement(new Paragraph(linea.trim(), fontNormal));
            }

            tablaBancos.addCell(cellBancos);
            document.add(tablaBancos);
        }

        // === PIE DE PÁGINA ===
        document.add(new Paragraph(" "));
        if (config.getPiePaginaReportes() != null && !config.getPiePaginaReportes().trim().isEmpty()) {
            Paragraph pPie = new Paragraph(config.getPiePaginaReportes(), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY));
            pPie.setAlignment(Element.ALIGN_CENTER);
            document.add(pPie);
        }

        // Usuario cajero
        if (venta.getUsuario() != null) {
            Paragraph pCajero = new Paragraph("Atendido por: " + venta.getUsuario().getNombreCompleto(), fontPequena);
            pCajero.setAlignment(Element.ALIGN_CENTER);
            document.add(pCajero);
        }

        document.close();
    }

    /**
     * Crea una línea separadora punteada para tickets.
     */
    private Paragraph crearSeparadorTicket(float anchoPuntos) {
        StringBuilder sb = new StringBuilder();
        int numGuiones = (int) (anchoPuntos / 4); // Aproximación
        for (int i = 0; i < numGuiones; i++) {
            sb.append("-");
        }
        Paragraph p = new Paragraph(sb.toString(), FontFactory.getFont(FontFactory.COURIER, 6));
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    /**
     * Crea una celda para tabla de ticket.
     */
    private PdfPCell crearCeldaTicket(String texto, com.lowagie.text.Font font, int alineacion, boolean esCabecera) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", font));
        cell.setBorder(esCabecera ? Rectangle.BOTTOM : Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(alineacion);
        cell.setPadding(2);
        return cell;
    }
}
