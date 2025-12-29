package com.libreria.sistema.controller;

import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.VentaRepository;

// IMPORTS PDF (iText / OpenPDF) - Especificamos uno por uno para evitar conflictos con Excel
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

// IMPORTS EXCEL (Apache POI)
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.awt.Color;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/reportes")
public class ReporteController {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final CajaRepository cajaRepository;

    public ReporteController(VentaRepository ventaRepository, ProductoRepository productoRepository, CajaRepository cajaRepository) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.cajaRepository = cajaRepository;
    }

    @GetMapping
    public String index() { return "reportes/index"; }

    // ==========================================
    //              EXPORTACIÓN EXCEL
    // ==========================================
    @GetMapping("/exportar/excel")
    public void exportarExcel(@RequestParam String tipo,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
                              HttpServletResponse response) throws IOException {

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=Reporte_" + tipo + ".xlsx");

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(tipo);

        // ESTILOS PROFESIONALES
        CellStyle estiloCabecera = crearEstiloCabecera(workbook);
        CellStyle estiloDatos = crearEstiloDatos(workbook);

        if ("VENTAS".equals(tipo)) generarExcelVentas(sheet, inicio, fin, estiloCabecera, estiloDatos);
        else if ("CAJA".equals(tipo)) generarExcelCaja(sheet, inicio, fin, estiloCabecera, estiloDatos);
        else if ("INVENTARIO".equals(tipo)) generarExcelInventario(sheet, estiloCabecera, estiloDatos);

        // Auto-ajustar columnas
        for(int i=0; i<10; i++) sheet.autoSizeColumn(i);

        workbook.write(response.getOutputStream());
        workbook.close();
    }

    // --- MÉTODOS DE ESTILO EXCEL ---
    private CellStyle crearEstiloCabecera(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        // Usamos el Font de POI explícitamente
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 12);
        
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private CellStyle crearEstiloDatos(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    // --- GENERADORES EXCEL ---
    private void generarExcelVentas(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle) {
        // Usamos el Row de POI explícitamente para evitar ambigüedad
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.setHeightInPoints(25);
        String[] columns = {"ID", "FECHA EMISIÓN", "COMPROBANTE", "CLIENTE", "TOTAL (S/)", "ESTADO"};
        
        for(int i=0; i<columns.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        List<Venta> lista = ventaRepository.findAll(); 
        int rowIdx = 1;
        for (Venta v : lista) {
            if(inicio != null && v.getFechaEmision().isBefore(inicio)) continue;
            if(fin != null && v.getFechaEmision().isAfter(fin)) continue;

            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
            crearCeldaData(row, 0, v.getId().toString(), dataStyle);
            crearCeldaData(row, 1, v.getFechaEmision().toString(), dataStyle);
            crearCeldaData(row, 2, v.getTipoComprobante() + " " + v.getSerie() + "-" + v.getNumero(), dataStyle);
            crearCeldaData(row, 3, v.getClienteDenominacion(), dataStyle);
            crearCeldaData(row, 4, v.getTotal().toString(), dataStyle);
            crearCeldaData(row, 5, v.getEstado(), dataStyle);
        }
    }

    private void generarExcelCaja(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle) {
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.setHeightInPoints(25);
        String[] columns = {"ID", "FECHA HORA", "TIPO", "CONCEPTO", "MONTO (S/)"};
        
        for(int i=0; i<columns.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        List<MovimientoCaja> lista = cajaRepository.findAll();
        int rowIdx = 1;
        for (MovimientoCaja m : lista) {
            LocalDate fechaMov = m.getFecha().toLocalDate();
            if(inicio != null && fechaMov.isBefore(inicio)) continue;
            if(fin != null && fechaMov.isAfter(fin)) continue;

            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
            crearCeldaData(row, 0, m.getId().toString(), dataStyle);
            crearCeldaData(row, 1, m.getFecha().toString().replace("T", " "), dataStyle);
            crearCeldaData(row, 2, m.getTipo(), dataStyle);
            crearCeldaData(row, 3, m.getConcepto(), dataStyle);
            crearCeldaData(row, 4, m.getMonto().toString(), dataStyle);
        }
    }

    private void generarExcelInventario(Sheet sheet, CellStyle headerStyle, CellStyle dataStyle) {
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.setHeightInPoints(25);
        String[] columns = {"CÓDIGO", "PRODUCTO", "CATEGORÍA", "STOCK", "P. COMPRA", "P. VENTA", "VALORIZADO"};
        
        for(int i=0; i<columns.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        List<Producto> lista = productoRepository.findAll();
        int rowIdx = 1;
        for (Producto p : lista) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
            crearCeldaData(row, 0, p.getCodigoInterno(), dataStyle);
            crearCeldaData(row, 1, p.getNombre(), dataStyle);
            crearCeldaData(row, 2, p.getCategoria(), dataStyle);
            crearCeldaData(row, 3, p.getStockActual().toString(), dataStyle);
            crearCeldaData(row, 4, p.getPrecioCompra() != null ? p.getPrecioCompra().toString() : "0.00", dataStyle);
            crearCeldaData(row, 5, p.getPrecioVenta() != null ? p.getPrecioVenta().toString() : "0.00", dataStyle);
            
            double valor = p.getStockActual() * (p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0.0);
            crearCeldaData(row, 6, String.format("%.2f", valor), dataStyle);
        }
    }

    private void crearCeldaData(org.apache.poi.ss.usermodel.Row row, int column, String valor, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(column);
        cell.setCellValue(valor);
        cell.setCellStyle(style);
    }

    // ==========================================
    //              EXPORTACIÓN PDF (SUNAT)
    // ==========================================
    @GetMapping("/exportar/pdf")
    public void exportarPdf(@RequestParam String tipo,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
                            HttpServletResponse response) throws IOException {

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=Reporte_" + tipo + ".pdf");

        Document document = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        agregarCabeceraSunat(document, "REPORTE DE " + tipo);
        document.add(new Paragraph(" "));

        if ("VENTAS".equals(tipo)) generarPdfVentas(document, inicio, fin);
        else if ("CAJA".equals(tipo)) generarPdfCaja(document, inicio, fin);
        else if ("INVENTARIO".equals(tipo)) generarPdfInventario(document);

        document.close();
    }

    private void agregarCabeceraSunat(Document document, String tituloReporte) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{5, 1, 3});

        PdfPCell cellEmpresa = new PdfPCell();
        cellEmpresa.setBorder(Rectangle.NO_BORDER);
        // Usamos com.lowagie.text.Font explícitamente
        com.lowagie.text.Font fontEmpresa = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        cellEmpresa.addElement(new Paragraph("LIBRERÍA ERP S.A.C.", fontEmpresa));
        cellEmpresa.addElement(new Paragraph("Av. Principal 123 - Lima", FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cellEmpresa.addElement(new Paragraph("Telf: (01) 555-0909", FontFactory.getFont(FontFactory.HELVETICA, 10)));
        headerTable.addCell(cellEmpresa);

        PdfPCell cellVacia = new PdfPCell();
        cellVacia.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(cellVacia);

        PdfPCell cellRuc = new PdfPCell();
        cellRuc.setBorder(Rectangle.BOX);
        cellRuc.setBorderWidth(1.5f);
        cellRuc.setPadding(10);
        Paragraph pRuc = new Paragraph("R.U.C. 20100000001", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
        pRuc.setAlignment(Element.ALIGN_CENTER);
        cellRuc.addElement(pRuc);
        Paragraph pTitulo = new Paragraph(tituloReporte, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLUE));
        pTitulo.setAlignment(Element.ALIGN_CENTER);
        cellRuc.addElement(pTitulo);
        headerTable.addCell(cellRuc);

        document.add(headerTable);
    }

    private void generarPdfVentas(Document document, LocalDate inicio, LocalDate fin) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 2, 4, 1.5f, 1.5f});
        
        com.lowagie.text.Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        String[] headers = {"FECHA", "COMPROBANTE", "CLIENTE", "TOTAL", "ESTADO"};
        for(String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, fontHeader));
            cell.setBackgroundColor(Color.DARK_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6);
            table.addCell(cell);
        }

        List<Venta> lista = ventaRepository.findAll();
        com.lowagie.text.Font fontData = FontFactory.getFont(FontFactory.HELVETICA, 9);

        for (Venta v : lista) {
            if(inicio != null && v.getFechaEmision().isBefore(inicio)) continue;
            if(fin != null && v.getFechaEmision().isAfter(fin)) continue;

            table.addCell(new Phrase(v.getFechaEmision().toString(), fontData));
            table.addCell(new Phrase(v.getTipoComprobante() + " " + v.getSerie() + "-" + v.getNumero(), fontData));
            table.addCell(new Phrase(v.getClienteDenominacion(), fontData));
            PdfPCell cTotal = new PdfPCell(new Phrase("S/ " + v.getTotal(), fontData));
            cTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(cTotal);
            table.addCell(new Phrase(v.getEstado(), fontData));
        }
        document.add(table);
    }

    private void generarPdfInventario(Document document) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 4, 1.5f, 1.5f, 2});

        com.lowagie.text.Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        String[] headers = {"CODIGO", "PRODUCTO", "STOCK", "PRECIO", "VALORIZADO"};
        for(String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, fontHeader));
            cell.setBackgroundColor(Color.DARK_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6);
            table.addCell(cell);
        }

        List<Producto> lista = productoRepository.findAll();
        com.lowagie.text.Font fontData = FontFactory.getFont(FontFactory.HELVETICA, 9);

        for (Producto p : lista) {
            table.addCell(new Phrase(p.getCodigoInterno(), fontData));
            table.addCell(new Phrase(p.getNombre(), fontData));
            PdfPCell cStock = new PdfPCell(new Phrase(p.getStockActual().toString(), fontData));
            cStock.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cStock);
            table.addCell(new Phrase("S/ " + p.getPrecioVenta(), fontData));
            
            double valor = p.getStockActual() * (p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0);
            PdfPCell cVal = new PdfPCell(new Phrase("S/ " + String.format("%.2f", valor), fontData));
            cVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(cVal);
        }
        document.add(table);
    }

    private void generarPdfCaja(Document document, LocalDate inicio, LocalDate fin) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        com.lowagie.text.Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        String[] headers = {"FECHA", "TIPO", "CONCEPTO", "MONTO"};
        for(String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, fontHeader));
            cell.setBackgroundColor(Color.DARK_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6);
            table.addCell(cell);
        }

        List<MovimientoCaja> lista = cajaRepository.findAll();
        com.lowagie.text.Font fontData = FontFactory.getFont(FontFactory.HELVETICA, 9);

        for (MovimientoCaja m : lista) {
            LocalDate fechaMov = m.getFecha().toLocalDate();
            if(inicio != null && fechaMov.isBefore(inicio)) continue;
            if(fin != null && fechaMov.isAfter(fin)) continue;

            table.addCell(new Phrase(m.getFecha().toString().replace("T", " "), fontData));
            table.addCell(new Phrase(m.getTipo(), fontData));
            table.addCell(new Phrase(m.getConcepto(), fontData));
            PdfPCell cMonto = new PdfPCell(new Phrase("S/ " + m.getMonto(), fontData));
            cMonto.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(cMonto);
        }
        document.add(table);
    }
}