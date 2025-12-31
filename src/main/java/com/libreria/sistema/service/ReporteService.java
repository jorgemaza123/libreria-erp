package com.libreria.sistema.service;

import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.VentaRepository;

// --- IMPORTS PDF (iText/OpenPDF) ---
// Importamos UNO POR UNO para evitar traer 'Cell' o 'Row' de iText
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
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
import java.util.List;

@Service
public class ReporteService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final CajaRepository cajaRepository;

    public ReporteService(VentaRepository ventaRepository, ProductoRepository productoRepository, CajaRepository cajaRepository) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.cajaRepository = cajaRepository;
    }

    // ==========================================
    //              LÓGICA EXCEL
    // ==========================================
    public void generarExcel(String tipo, LocalDate inicio, LocalDate fin, OutputStream outputStream) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(tipo);

        CellStyle headerStyle = crearEstiloCabecera(workbook);
        CellStyle dataStyle = crearEstiloDatos(workbook);

        if ("VENTAS".equals(tipo)) generarExcelVentas(sheet, inicio, fin, headerStyle, dataStyle);
        else if ("CAJA".equals(tipo)) generarExcelCaja(sheet, inicio, fin, headerStyle, dataStyle);
        else if ("INVENTARIO".equals(tipo)) generarExcelInventario(sheet, headerStyle, dataStyle);

        // Autoajustar columnas (0 a 7)
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

    private void generarExcelVentas(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle) {
        Row header = sheet.createRow(0);
        String[] columns = {"ID", "FECHA", "COMPROBANTE", "CLIENTE", "TOTAL", "ESTADO"};
        
        for(int i=0; i<columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        List<Venta> lista = ventaRepository.findAll();
        int rowIdx = 1;
        for (Venta v : lista) {
            if(inicio != null && v.getFechaEmision().isBefore(inicio)) continue;
            if(fin != null && v.getFechaEmision().isAfter(fin)) continue;

            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, v.getId().toString(), dataStyle);
            crearCelda(row, 1, v.getFechaEmision().toString(), dataStyle);
            crearCelda(row, 2, v.getTipoComprobante() + " " + v.getSerie() + "-" + v.getNumero(), dataStyle);
            crearCelda(row, 3, v.getClienteDenominacion(), dataStyle);
            crearCelda(row, 4, "S/ " + v.getTotal(), dataStyle);
            crearCelda(row, 5, v.getEstado(), dataStyle);
        }
    }

    private void generarExcelCaja(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle) {
        Row header = sheet.createRow(0);
        String[] columns = {"FECHA", "TIPO", "CONCEPTO", "MONTO"};
        
        for(int i=0; i<columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        List<MovimientoCaja> lista = cajaRepository.findAll();
        int rowIdx = 1;
        for (MovimientoCaja m : lista) {
            LocalDate fechaMov = m.getFecha().toLocalDate();
            if(inicio != null && fechaMov.isBefore(inicio)) continue;
            if(fin != null && fechaMov.isAfter(fin)) continue;

            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, m.getFecha().toString().replace("T", " "), dataStyle);
            crearCelda(row, 1, m.getTipo(), dataStyle);
            crearCelda(row, 2, m.getConcepto(), dataStyle);
            crearCelda(row, 3, "S/ " + m.getMonto(), dataStyle);
        }
    }

    private void generarExcelInventario(Sheet sheet, CellStyle headerStyle, CellStyle dataStyle) {
        Row header = sheet.createRow(0);
        String[] columns = {"CODIGO", "PRODUCTO", "STOCK", "P. COMPRA", "P. VENTA", "VALORIZADO"};
        
        for(int i=0; i<columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        List<Producto> lista = productoRepository.findAll();
        int rowIdx = 1;
        for (Producto p : lista) {
            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, p.getCodigoInterno(), dataStyle);
            crearCelda(row, 1, p.getNombre(), dataStyle);
            crearCelda(row, 2, String.valueOf(p.getStockActual()), dataStyle);
            crearCelda(row, 3, "S/ " + (p.getPrecioCompra()!=null?p.getPrecioCompra():0), dataStyle);
            crearCelda(row, 4, "S/ " + p.getPrecioVenta(), dataStyle);
            
            double valor = p.getStockActual() * (p.getPrecioCompra()!=null?p.getPrecioCompra().doubleValue():0);
            crearCelda(row, 5, "S/ " + String.format("%.2f", valor), dataStyle);
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

        // Cabecera Simple
        Paragraph titulo = new Paragraph("REPORTE DE " + tipo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLUE));
        titulo.setAlignment(Element.ALIGN_CENTER);
        document.add(titulo);
        
        Paragraph subtitulo = new Paragraph("Generado el: " + LocalDate.now(), FontFactory.getFont(FontFactory.HELVETICA, 10));
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        document.add(subtitulo);
        document.add(new Paragraph(" ")); // Espacio

        if ("VENTAS".equals(tipo)) generarPdfVentas(document, inicio, fin);
        else if ("CAJA".equals(tipo)) generarPdfCaja(document, inicio, fin);
        else if ("INVENTARIO".equals(tipo)) generarPdfInventario(document);

        document.close();
    }

    private void generarPdfVentas(Document document, LocalDate inicio, LocalDate fin) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 3, 4, 2, 2});
        
        agregarCabeceraPdf(table, new String[]{"FECHA", "DOC", "CLIENTE", "TOTAL", "ESTADO"});

        List<Venta> lista = ventaRepository.findAll();
        for (Venta v : lista) {
            if(inicio != null && v.getFechaEmision().isBefore(inicio)) continue;
            if(fin != null && v.getFechaEmision().isAfter(fin)) continue;

            table.addCell(crearCeldaPdf(v.getFechaEmision().toString()));
            table.addCell(crearCeldaPdf(v.getSerie() + "-" + v.getNumero()));
            table.addCell(crearCeldaPdf(v.getClienteDenominacion()));
            table.addCell(crearCeldaPdf("S/ " + v.getTotal()));
            table.addCell(crearCeldaPdf(v.getEstado()));
        }
        document.add(table);
    }

    private void generarPdfCaja(Document document, LocalDate inicio, LocalDate fin) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        agregarCabeceraPdf(table, new String[]{"FECHA", "TIPO", "CONCEPTO", "MONTO"});

        List<MovimientoCaja> lista = cajaRepository.findAll();
        for (MovimientoCaja m : lista) {
            LocalDate fechaMov = m.getFecha().toLocalDate();
            if(inicio != null && fechaMov.isBefore(inicio)) continue;
            if(fin != null && fechaMov.isAfter(fin)) continue;

            table.addCell(crearCeldaPdf(m.getFecha().toString().replace("T", " ")));
            table.addCell(crearCeldaPdf(m.getTipo()));
            table.addCell(crearCeldaPdf(m.getConcepto()));
            table.addCell(crearCeldaPdf("S/ " + m.getMonto()));
        }
        document.add(table);
    }

    private void generarPdfInventario(Document document) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        agregarCabeceraPdf(table, new String[]{"CODIGO", "PRODUCTO", "STOCK", "P. VENTA", "VALORIZADO"});

        List<Producto> lista = productoRepository.findAll();
        for (Producto p : lista) {
            table.addCell(crearCeldaPdf(p.getCodigoInterno()));
            table.addCell(crearCeldaPdf(p.getNombre()));
            table.addCell(crearCeldaPdf(String.valueOf(p.getStockActual())));
            table.addCell(crearCeldaPdf("S/ " + p.getPrecioVenta()));
            
            double valor = p.getStockActual() * (p.getPrecioCompra()!=null?p.getPrecioCompra().doubleValue():0);
            table.addCell(crearCeldaPdf("S/ " + String.format("%.2f", valor)));
        }
        document.add(table);
    }

    private void agregarCabeceraPdf(PdfPTable table, String[] headers) {
        for(String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
            cell.setBackgroundColor(Color.DARK_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private PdfPCell crearCeldaPdf(String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cell.setPadding(4);
        return cell;
    }
}