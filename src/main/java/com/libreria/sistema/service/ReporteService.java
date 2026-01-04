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
// Importamos UNO POR UNO para evitar traer 'Cell' o 'Row' de iText
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
// Aquí sí podemos usar '*' porque ya no hay conflicto
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
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

        if ("VENTAS".equals(tipo)) generarExcelVentas(sheet, inicio, fin, headerStyle, dataStyle, config);
        else if ("CAJA".equals(tipo)) generarExcelCaja(sheet, inicio, fin, headerStyle, dataStyle, config);
        else if ("INVENTARIO".equals(tipo)) generarExcelInventario(sheet, headerStyle, dataStyle, config);
        else if ("USUARIOS".equals(tipo)) generarExcelUsuarios(sheet, headerStyle, dataStyle);

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

    private void generarExcelVentas(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle, com.libreria.sistema.model.Configuracion config) {
        crearFilaCabecera(sheet, headerStyle, "ID", "FECHA", "COMPROBANTE", "CLIENTE", "TOTAL", "ESTADO");
        List<Venta> lista = ventaRepository.findAll();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(config.getFormatoFechaReportes() != null ? config.getFormatoFechaReportes() : "dd/MM/yyyy");
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";
        int rowIdx = 1;
        for (Venta v : lista) {
            if(inicio != null && v.getFechaEmision().isBefore(inicio)) continue;
            if(fin != null && v.getFechaEmision().isAfter(fin)) continue;

            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, v.getId().toString(), dataStyle);
            crearCelda(row, 1, v.getFechaEmision().format(formatter), dataStyle);
            crearCelda(row, 2, v.getTipoComprobante() + " " + v.getSerie() + "-" + v.getNumero(), dataStyle);
            crearCelda(row, 3, v.getClienteDenominacion(), dataStyle);
            crearCelda(row, 4, moneda + v.getTotal(), dataStyle);
            crearCelda(row, 5, v.getEstado(), dataStyle);
        }
    }

    private void generarExcelCaja(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle, com.libreria.sistema.model.Configuracion config) {
        crearFilaCabecera(sheet, headerStyle, "FECHA", "TIPO", "CONCEPTO", "MONTO", "USUARIO");
        List<MovimientoCaja> lista = cajaRepository.findAll();
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";
        int rowIdx = 1;
        for (MovimientoCaja m : lista) {
            LocalDate fechaMov = m.getFecha().toLocalDate();
            if(inicio != null && fechaMov.isBefore(inicio)) continue;
            if(fin != null && fechaMov.isAfter(fin)) continue;

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
        List<Producto> lista = productoRepository.findAll();
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
                // Si falla, agregar celda vacía
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
        cellEmpresa.addElement(new Paragraph("Dirección: " + config.getDireccion(), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        if (config.getTelefono() != null) cellEmpresa.addElement(new Paragraph("Tel: " + config.getTelefono(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
        headerTable.addCell(cellEmpresa);

        // TÍTULO Y FECHA
        PdfPCell cellTitulo = new PdfPCell();
        cellTitulo.setBorder(Rectangle.NO_BORDER);
        cellTitulo.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph pTitulo = new Paragraph("REPORTE DE " + tipo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, colorPrimario));
        pTitulo.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pTitulo);

        String fechaStr = "Fecha Emisión: " + LocalDate.now().format(formatter);
        if(inicio != null && fin != null) fechaStr += "\nPeríodo: " + inicio.format(formatter) + " al " + fin.format(formatter);

        Paragraph pFecha = new Paragraph(fechaStr, FontFactory.getFont(FontFactory.HELVETICA, 10));
        pFecha.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pFecha);

        headerTable.addCell(cellTitulo);
        document.add(headerTable);
        document.add(new Paragraph(" "));

        // --- CONTENIDO ---
        if ("VENTAS".equals(tipo)) generarPdfVentas(document, inicio, fin, config);
        else if ("CAJA".equals(tipo)) generarPdfCaja(document, inicio, fin, config);
        else if ("INVENTARIO".equals(tipo)) generarPdfInventario(document, config);
        else if ("USUARIOS".equals(tipo)) generarPdfUsuarios(document, config);

        // --- PIE DE PÁGINA PERSONALIZADO ---
        if (config.getPiePaginaReportes() != null && !config.getPiePaginaReportes().trim().isEmpty()) {
            document.add(new Paragraph(" "));
            Paragraph pie = new Paragraph(config.getPiePaginaReportes(), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY));
            pie.setAlignment(Element.ALIGN_CENTER);
            document.add(pie);
        }

        document.close();
    }

    /**
     * Helper para convertir color hex a java.awt.Color
     */
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

    private void generarPdfVentas(Document document, LocalDate inicio, LocalDate fin, com.libreria.sistema.model.Configuracion config) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 2, 4, 2, 2});

        agregarCabeceraPdf(table, config, "FECHA", "DOC", "CLIENTE", "TOTAL", "ESTADO");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(config.getFormatoFechaReportes() != null ? config.getFormatoFechaReportes() : "dd/MM/yyyy");
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";

        List<Venta> lista = ventaRepository.findAll();
        for (Venta v : lista) {
            if(inicio != null && v.getFechaEmision().isBefore(inicio)) continue;
            if(fin != null && v.getFechaEmision().isAfter(fin)) continue;

            table.addCell(crearCeldaPdf(v.getFechaEmision().format(formatter)));
            table.addCell(crearCeldaPdf(v.getSerie() + "-" + v.getNumero()));
            table.addCell(crearCeldaPdf(v.getClienteDenominacion()));
            table.addCell(crearCeldaPdfRight(moneda + v.getTotal()));
            table.addCell(crearCeldaPdf(v.getEstado()));
        }
        document.add(table);
    }

    private void generarPdfCaja(Document document, LocalDate inicio, LocalDate fin, com.libreria.sistema.model.Configuracion config) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 1, 4, 2, 2});
        agregarCabeceraPdf(table, config, "FECHA", "TIPO", "CONCEPTO", "MONTO", "USUARIO");

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";

        List<MovimientoCaja> lista = cajaRepository.findAll();
        for (MovimientoCaja m : lista) {
            LocalDate fechaMov = m.getFecha().toLocalDate();
            if(inicio != null && fechaMov.isBefore(inicio)) continue;
            if(fin != null && fechaMov.isAfter(fin)) continue;

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

        List<Producto> lista = productoRepository.findAll();
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
}