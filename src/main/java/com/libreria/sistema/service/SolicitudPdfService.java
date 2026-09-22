package com.libreria.sistema.service;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.SolicitudProducto;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class SolicitudPdfService {

    private final ConfiguracionService configuracionService;

    public SolicitudPdfService(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    public void generarPdf(List<SolicitudProducto> solicitudes, OutputStream outputStream) throws DocumentException {
        Configuracion config = configuracionService.obtenerConfiguracion();

        // Configuración de márgenes y tamaño A4
        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        // Fuentes estándar
        Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font fontEmpresa = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font fontPequena = FontFactory.getFont(FontFactory.HELVETICA, 8);

        Color colorPrimario = parseColor(config.getColorPrimario(), new Color(0, 123, 255));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        // === CABECERA ===
        boolean tienelogo = config.getMostrarLogoEnReportes() != null && config.getMostrarLogoEnReportes() && config.getLogoBase64() != null;
        int numCols = tienelogo ? 3 : 2;
        PdfPTable tablaHeader = new PdfPTable(numCols);
        tablaHeader.setWidthPercentage(100);
        if (numCols == 3) {
            tablaHeader.setWidths(new float[]{1.2f, 2.8f, 2.0f});
        } else {
            tablaHeader.setWidths(new float[]{3.5f, 2.5f});
        }

        // Agregar Logo si existe
        if (tienelogo) {
            try {
                byte[] logoBytes = Base64.getDecoder().decode(config.getLogoBase64());
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(70, 70);
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

        // Datos de la Empresa
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
        tablaHeader.addCell(cellEmpresa);

        // Bloque del Tipo de Documento
        PdfPCell cellDocumento = new PdfPCell();
        cellDocumento.setBorderColor(colorPrimario);
        cellDocumento.setBorderWidth(2);
        cellDocumento.setPadding(8);
        cellDocumento.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pRuc = new Paragraph("R.U.C. " + config.getRuc(), fontBold);
        pRuc.setAlignment(Element.ALIGN_CENTER);
        cellDocumento.addElement(pRuc);

        Paragraph pTipo = new Paragraph("DEMANDA INSATISFECHA", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, colorPrimario));
        pTipo.setAlignment(Element.ALIGN_CENTER);
        cellDocumento.addElement(pTipo);

        Paragraph pSub = new Paragraph("PEDIDOS DE CLIENTES", fontBold);
        pSub.setAlignment(Element.ALIGN_CENTER);
        cellDocumento.addElement(pSub);

        tablaHeader.addCell(cellDocumento);
        document.add(tablaHeader);
        document.add(new Paragraph(" "));

        // Título del Reporte
        Paragraph pTitulo = new Paragraph("REPORTE DE PRODUCTOS SOLICITADOS (PENDIENTES DE COMPRA)", fontTitulo);
        pTitulo.setAlignment(Element.ALIGN_CENTER);
        document.add(pTitulo);
        document.add(new Paragraph(" "));

        // === TABLA DE SOLICITUDES ===
        PdfPTable tablaItems = new PdfPTable(3);
        tablaItems.setWidthPercentage(100);
        tablaItems.setWidths(new float[]{4.0f, 1.0f, 2.0f});

        Color headerBg = new Color(51, 51, 51);
        Font fontHeaderBlanca = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

        String[] headers = {"PRODUCTO / DESCRIPCIÓN SOLICITADA", "VECES PEDIDO", "ÚLTIMA SOLICITUD"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, fontHeaderBlanca));
            cell.setBackgroundColor(headerBg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6);
            tablaItems.addCell(cell);
        }

        if (solicitudes.isEmpty()) {
            PdfPCell cellVacia = new PdfPCell(new Phrase("No hay solicitudes pendientes en este momento.", fontNormal));
            cellVacia.setColspan(3);
            cellVacia.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellVacia.setPadding(12);
            tablaItems.addCell(cellVacia);
        } else {
            for (SolicitudProducto s : solicitudes) {
                PdfPCell cellNombre = new PdfPCell(new Phrase(s.getNombreProducto().toUpperCase(), fontNormal));
                cellNombre.setPadding(5);
                tablaItems.addCell(cellNombre);

                PdfPCell cellContador = new PdfPCell(new Phrase(String.valueOf(s.getContador()), fontBold));
                cellContador.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellContador.setPadding(5);
                tablaItems.addCell(cellContador);

                String fecha = s.getUltimaSolicitud() != null ? s.getUltimaSolicitud().format(fmt) : "-";
                PdfPCell cellFecha = new PdfPCell(new Phrase(fecha, fontNormal));
                cellFecha.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellFecha.setPadding(5);
                tablaItems.addCell(cellFecha);
            }
        }

        document.add(tablaItems);
        document.add(new Paragraph(" "));

        // === PIE DE PÁGINA ===
        Paragraph pie = new Paragraph("Este documento contiene la recopilación de productos demandados por clientes y no disponibles en almacén.", fontPequena);
        pie.setAlignment(Element.ALIGN_CENTER);
        document.add(pie);

        document.close();
    }

    public byte[] generarExcel(List<SolicitudProducto> solicitudes) throws IOException {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Pedidos vendedores");

            CellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle centeredStyle = workbook.createCellStyle();
            centeredStyle.setAlignment(HorizontalAlignment.CENTER);

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Demanda insatisfecha - pedidos de vendedores");
            title.getCell(0).setCellStyle(titleStyle);

            Row headers = sheet.createRow(2);
            String[] columnas = {"Producto solicitado", "Veces pedido", "Ultima solicitud", "Estado"};
            for (int i = 0; i < columnas.length; i++) {
                headers.createCell(i).setCellValue(columnas[i]);
                headers.getCell(i).setCellStyle(headerStyle);
            }

            int rowIndex = 3;
            for (SolicitudProducto solicitud : solicitudes) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(solicitud.getNombreProducto() != null
                        ? solicitud.getNombreProducto().toUpperCase()
                        : "");
                row.createCell(1).setCellValue(solicitud.getContador());
                row.getCell(1).setCellStyle(centeredStyle);
                row.createCell(2).setCellValue(solicitud.getUltimaSolicitud() != null
                        ? solicitud.getUltimaSolicitud().format(fmt)
                        : "-");
                row.getCell(2).setCellStyle(centeredStyle);
                row.createCell(3).setCellValue(solicitud.getEstado() != null ? solicitud.getEstado() : "PENDIENTE");
                row.getCell(3).setCellStyle(centeredStyle);
            }

            if (solicitudes.isEmpty()) {
                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue("No hay solicitudes pendientes.");
            }

            for (int i = 0; i < columnas.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private Color parseColor(String hex, Color defaultColor) {
        if (hex == null || hex.isEmpty()) return defaultColor;
        try {
            hex = hex.startsWith("#") ? hex.substring(1) : hex;
            return new Color(Integer.parseInt(hex, 16));
        } catch (Exception e) {
            return defaultColor;
        }
    }
}
