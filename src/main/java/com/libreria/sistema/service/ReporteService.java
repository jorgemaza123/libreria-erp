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
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(tipo);

        CellStyle headerStyle = crearEstiloCabecera(workbook);
        CellStyle dataStyle = crearEstiloDatos(workbook);

        if ("VENTAS".equals(tipo)) generarExcelVentas(sheet, inicio, fin, headerStyle, dataStyle);
        else if ("CAJA".equals(tipo)) generarExcelCaja(sheet, inicio, fin, headerStyle, dataStyle);
        else if ("INVENTARIO".equals(tipo)) generarExcelInventario(sheet, headerStyle, dataStyle);
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

    private void generarExcelVentas(Sheet sheet, LocalDate inicio, LocalDate fin, CellStyle headerStyle, CellStyle dataStyle) {
        crearFilaCabecera(sheet, headerStyle, "ID", "FECHA", "COMPROBANTE", "CLIENTE", "TOTAL", "ESTADO");
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
        crearFilaCabecera(sheet, headerStyle, "FECHA", "TIPO", "CONCEPTO", "MONTO", "USUARIO");
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
            crearCelda(row, 4, m.getUsuario() != null ? m.getUsuario().getUsername() : "-", dataStyle);
        }
    }

    private void generarExcelInventario(Sheet sheet, CellStyle headerStyle, CellStyle dataStyle) {
        crearFilaCabecera(sheet, headerStyle, "CODIGO", "PRODUCTO", "STOCK", "P. COMPRA", "P. VENTA", "VALORIZADO");
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

        // --- CABECERA EMPRESARIAL ---
        var config = configuracionService.obtenerConfiguracion();
        
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        
        PdfPCell cellEmpresa = new PdfPCell();
        cellEmpresa.setBorder(Rectangle.NO_BORDER);
        cellEmpresa.addElement(new Paragraph(config.getNombreEmpresa(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        cellEmpresa.addElement(new Paragraph("RUC: " + config.getRuc(), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cellEmpresa.addElement(new Paragraph("Dirección: " + config.getDireccion(), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        headerTable.addCell(cellEmpresa);

        PdfPCell cellTitulo = new PdfPCell();
        cellTitulo.setBorder(Rectangle.NO_BORDER);
        cellTitulo.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph pTitulo = new Paragraph("REPORTE DE " + tipo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLUE));
        pTitulo.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pTitulo);
        
        String fechaStr = "Fecha Emisión: " + LocalDate.now();
        if(inicio != null && fin != null) fechaStr += "\nPeríodo: " + inicio + " al " + fin;
        
        Paragraph pFecha = new Paragraph(fechaStr, FontFactory.getFont(FontFactory.HELVETICA, 10));
        pFecha.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pFecha);
        
        headerTable.addCell(cellTitulo);
        document.add(headerTable);
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" ")); 

        if ("VENTAS".equals(tipo)) generarPdfVentas(document, inicio, fin);
        else if ("CAJA".equals(tipo)) generarPdfCaja(document, inicio, fin);
        else if ("INVENTARIO".equals(tipo)) generarPdfInventario(document);
        else if ("USUARIOS".equals(tipo)) generarPdfUsuarios(document);

        document.close();
    }

    private void generarPdfVentas(Document document, LocalDate inicio, LocalDate fin) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 2, 4, 2, 2});
        
        agregarCabeceraPdf(table, "FECHA", "DOC", "CLIENTE", "TOTAL", "ESTADO");

        List<Venta> lista = ventaRepository.findAll();
        for (Venta v : lista) {
            if(inicio != null && v.getFechaEmision().isBefore(inicio)) continue;
            if(fin != null && v.getFechaEmision().isAfter(fin)) continue;

            table.addCell(crearCeldaPdf(v.getFechaEmision().toString()));
            table.addCell(crearCeldaPdf(v.getSerie() + "-" + v.getNumero()));
            table.addCell(crearCeldaPdf(v.getClienteDenominacion()));
            table.addCell(crearCeldaPdfRight("S/ " + v.getTotal()));
            table.addCell(crearCeldaPdf(v.getEstado()));
        }
        document.add(table);
    }

    private void generarPdfCaja(Document document, LocalDate inicio, LocalDate fin) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 1, 4, 2, 2});
        agregarCabeceraPdf(table, "FECHA", "TIPO", "CONCEPTO", "MONTO", "USUARIO");

        List<MovimientoCaja> lista = cajaRepository.findAll();
        for (MovimientoCaja m : lista) {
            LocalDate fechaMov = m.getFecha().toLocalDate();
            if(inicio != null && fechaMov.isBefore(inicio)) continue;
            if(fin != null && fechaMov.isAfter(fin)) continue;

            table.addCell(crearCeldaPdf(m.getFecha().toString().replace("T", " ")));
            table.addCell(crearCeldaPdf(m.getTipo()));
            table.addCell(crearCeldaPdf(m.getConcepto()));
            table.addCell(crearCeldaPdfRight("S/ " + m.getMonto()));
            table.addCell(crearCeldaPdf(m.getUsuario() != null ? m.getUsuario().getUsername() : "-"));
        }
        document.add(table);
    }

    private void generarPdfInventario(Document document) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 4, 1, 2, 2});
        agregarCabeceraPdf(table, "CODIGO", "PRODUCTO", "STOCK", "P. VENTA", "VALORIZADO");

        List<Producto> lista = productoRepository.findAll();
        for (Producto p : lista) {
            table.addCell(crearCeldaPdf(p.getCodigoInterno()));
            table.addCell(crearCeldaPdf(p.getNombre()));
            table.addCell(crearCeldaPdfCenter(String.valueOf(p.getStockActual())));
            table.addCell(crearCeldaPdfRight("S/ " + p.getPrecioVenta()));
            
            double valor = p.getStockActual() * (p.getPrecioCompra()!=null?p.getPrecioCompra().doubleValue():0);
            table.addCell(crearCeldaPdfRight("S/ " + String.format("%.2f", valor)));
        }
        document.add(table);
    }

    private void generarPdfUsuarios(Document document) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 3, 3, 2});
        agregarCabeceraPdf(table, "USUARIO", "NOMBRE COMPLETO", "ROLES", "ESTADO");

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

    private void agregarCabeceraPdf(PdfPTable table, String... headers) {
        for(String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
            cell.setBackgroundColor(Color.DARK_GRAY);
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