package com.libreria.sistema.service;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.Cotizacion;
import com.libreria.sistema.model.DetalleCotizacion;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Service
@Slf4j
public class CotizacionPdfService {

    private final ConfiguracionService configuracionService;

    public CotizacionPdfService(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    public void generarPdf(Cotizacion cotizacion, OutputStream outputStream) throws DocumentException {
        Configuracion config = configuracionService.obtenerConfiguracion();

        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font fontEmpresa = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font fontPequena = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font fontTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);

        Color colorPrimario = parseColor(config.getColorPrimario(), new Color(0, 123, 255));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // === CABECERA ===
        boolean tienelogo = config.getMostrarLogoEnReportes() != null && config.getMostrarLogoEnReportes() && config.getLogoBase64() != null;
        int numCols = tienelogo ? 3 : 2;
        PdfPTable tablaHeader = new PdfPTable(numCols);
        tablaHeader.setWidthPercentage(100);
        if (numCols == 3) {
            tablaHeader.setWidths(new float[]{1, 3, 2});
        } else {
            tablaHeader.setWidths(new float[]{3, 2});
        }

        if (tienelogo) {
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

        PdfPCell cellEmpresa = new PdfPCell();
        cellEmpresa.setBorder(Rectangle.NO_BORDER);
        cellEmpresa.addElement(new Paragraph(config.getNombreEmpresa(), fontEmpresa));
        cellEmpresa.addElement(new Paragraph("RUC: " + config.getRuc(), fontBold));
        if (config.getDireccion() != null) cellEmpresa.addElement(new Paragraph(config.getDireccion(), fontNormal));
        if (config.getTelefono() != null) cellEmpresa.addElement(new Paragraph("Tel: " + config.getTelefono(), fontNormal));
        if (config.getEmail() != null) cellEmpresa.addElement(new Paragraph("Email: " + config.getEmail(), fontPequena));
        tablaHeader.addCell(cellEmpresa);

        PdfPCell cellDocumento = new PdfPCell();
        cellDocumento.setBorderColor(colorPrimario);
        cellDocumento.setBorderWidth(2);
        cellDocumento.setPadding(10);
        cellDocumento.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pRuc = new Paragraph("R.U.C. " + config.getRuc(), fontBold);
        pRuc.setAlignment(Element.ALIGN_CENTER);
        cellDocumento.addElement(pRuc);

        Paragraph pTipo = new Paragraph("COTIZACIÓN", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, colorPrimario));
        pTipo.setAlignment(Element.ALIGN_CENTER);
        cellDocumento.addElement(pTipo);

        String numDoc = cotizacion.getSerie() + " - " + String.format("%06d", cotizacion.getNumero());
        Paragraph pNum = new Paragraph(numDoc, fontBold);
        pNum.setAlignment(Element.ALIGN_CENTER);
        cellDocumento.addElement(pNum);

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
        cellCliente1.addElement(new Paragraph("Señor(es): " + safe(cotizacion.getClienteNombre()), fontNormal));
        cellCliente1.addElement(new Paragraph("Documento: " + safe(cotizacion.getClienteDocumento()), fontNormal));
        if (cotizacion.getClienteTelefono() != null && !cotizacion.getClienteTelefono().isEmpty()) {
            cellCliente1.addElement(new Paragraph("Teléfono: " + cotizacion.getClienteTelefono(), fontNormal));
        }
        tablaCliente.addCell(cellCliente1);

        PdfPCell cellCliente2 = new PdfPCell();
        cellCliente2.setBorder(Rectangle.BOX);
        cellCliente2.setPadding(8);
        cellCliente2.addElement(new Paragraph("Fecha Emisión: " + (cotizacion.getFechaEmision() != null ? cotizacion.getFechaEmision().format(fmt) : "-"), fontNormal));
        cellCliente2.addElement(new Paragraph("Válido hasta: " + (cotizacion.getFechaVencimiento() != null ? cotizacion.getFechaVencimiento().format(fmt) : "-"), fontNormal));
        cellCliente2.addElement(new Paragraph("Condición: " + safe(cotizacion.getFormaPago()), fontNormal));
        if (cotizacion.getMetodoPago() != null) {
            cellCliente2.addElement(new Paragraph("Método: " + cotizacion.getMetodoPago(), fontNormal));
        }
        tablaCliente.addCell(cellCliente2);
        document.add(tablaCliente);
        document.add(new Paragraph(" "));

        // === TABLA DE ITEMS ===
        PdfPTable tablaItems = new PdfPTable(5);
        tablaItems.setWidthPercentage(100);
        tablaItems.setWidths(new float[]{0.8f, 3.5f, 1.5f, 1.2f, 1.2f});

        Color headerBg = new Color(51, 51, 51);
        Font fontHeaderBlanca = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

        String[] headers = {"CANT.", "DESCRIPCIÓN", "CATEGORÍA", "P. UNIT.", "IMPORTE"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, fontHeaderBlanca));
            cell.setBackgroundColor(headerBg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6);
            tablaItems.addCell(cell);
        }

        for (DetalleCotizacion item : cotizacion.getItems()) {
            BigDecimal cant = item.getCantidad() != null ? item.getCantidad() : BigDecimal.ONE;
            BigDecimal precio = item.getPrecioUnitario() != null ? item.getPrecioUnitario() : BigDecimal.ZERO;
            BigDecimal sub = item.getSubtotal() != null ? item.getSubtotal() : cant.multiply(precio);

            PdfPCell cellCant = new PdfPCell(new Phrase(cant.stripTrailingZeros().toPlainString(), fontNormal));
            cellCant.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellCant.setPadding(5);
            tablaItems.addCell(cellCant);

            PdfPCell cellDesc = new PdfPCell(new Phrase(safe(item.getDescripcion()), fontNormal));
            cellDesc.setPadding(5);
            tablaItems.addCell(cellDesc);

            String cat = "SERVICIO".equals(item.getTipoItem()) && item.getCategoriaServicio() != null
                    ? item.getCategoriaServicio().replace("_", " ") : "-";
            PdfPCell cellCat = new PdfPCell(new Phrase(cat, fontPequena));
            cellCat.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellCat.setPadding(5);
            tablaItems.addCell(cellCat);

            PdfPCell cellPrecio = new PdfPCell(new Phrase("S/ " + formatMoney(precio), fontNormal));
            cellPrecio.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellPrecio.setPadding(5);
            tablaItems.addCell(cellPrecio);

            PdfPCell cellImporte = new PdfPCell(new Phrase("S/ " + formatMoney(sub), fontNormal));
            cellImporte.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellImporte.setPadding(5);
            tablaItems.addCell(cellImporte);
        }

        document.add(tablaItems);
        document.add(new Paragraph(" "));

        // === TOTALES ===
        PdfPTable tablaTotales = new PdfPTable(2);
        tablaTotales.setWidthPercentage(40);
        tablaTotales.setHorizontalAlignment(Element.ALIGN_RIGHT);

        addTotalRow(tablaTotales, "Op. Gravada:", formatMoney(cotizacion.getSubtotal()), fontNormal);

        BigDecimal igvVal = cotizacion.getIgv() != null ? cotizacion.getIgv() : BigDecimal.ZERO;
        addTotalRow(tablaTotales, "IGV (18%):", formatMoney(igvVal), fontNormal);

        if (cotizacion.getDescuento() != null && cotizacion.getDescuento().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(tablaTotales, "Descuento:", "-S/ " + formatMoney(cotizacion.getDescuento()), fontNormal);
        }

        PdfPCell cellLabelTotal = new PdfPCell(new Phrase("TOTAL:", fontTotal));
        cellLabelTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cellLabelTotal.setBorder(Rectangle.TOP);
        cellLabelTotal.setPadding(6);
        tablaTotales.addCell(cellLabelTotal);

        PdfPCell cellValorTotal = new PdfPCell(new Phrase("S/ " + formatMoney(cotizacion.getTotal()), fontTotal));
        cellValorTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cellValorTotal.setBorder(Rectangle.TOP);
        cellValorTotal.setPadding(6);
        tablaTotales.addCell(cellValorTotal);

        document.add(tablaTotales);

        // === INFORMACIÓN DE CRÉDITO ===
        if ("CREDITO".equals(cotizacion.getFormaPago())) {
            document.add(new Paragraph(" "));
            PdfPTable tablaCredito = new PdfPTable(1);
            tablaCredito.setWidthPercentage(60);
            tablaCredito.setHorizontalAlignment(Element.ALIGN_RIGHT);

            Font fontRed = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.RED);
            PdfPCell cellCredito = new PdfPCell();
            cellCredito.setPadding(8);
            cellCredito.setBackgroundColor(new Color(255, 243, 243));
            cellCredito.addElement(new Paragraph("INFORMACIÓN DE CRÉDITO", fontBold));
            if (cotizacion.getMontoInicial() != null && cotizacion.getMontoInicial().compareTo(BigDecimal.ZERO) > 0) {
                cellCredito.addElement(new Paragraph("Monto Inicial: S/ " + formatMoney(cotizacion.getMontoInicial()), fontNormal));
            }
            BigDecimal saldo = cotizacion.getSaldoPendiente() != null ? cotizacion.getSaldoPendiente() : BigDecimal.ZERO;
            cellCredito.addElement(new Paragraph("Saldo Pendiente: S/ " + formatMoney(saldo), fontRed));
            if (cotizacion.getDiasCredito() != null) {
                cellCredito.addElement(new Paragraph("Plazo: " + cotizacion.getDiasCredito() + " días", fontNormal));
            }
            tablaCredito.addCell(cellCredito);
            document.add(tablaCredito);
        }

        // === CONDICIONES COMERCIALES ===
        if (cotizacion.getCondiciones() != null && !cotizacion.getCondiciones().isEmpty()) {
            document.add(new Paragraph(" "));
            Paragraph pCondTitulo = new Paragraph("CONDICIONES COMERCIALES", fontBold);
            document.add(pCondTitulo);
            Paragraph pCond = new Paragraph(cotizacion.getCondiciones(), fontNormal);
            document.add(pCond);
        }

        // === OBSERVACIONES ===
        if (cotizacion.getObservaciones() != null && !cotizacion.getObservaciones().isEmpty()) {
            document.add(new Paragraph(" "));
            Paragraph pObsTitulo = new Paragraph("OBSERVACIONES", fontBold);
            document.add(pObsTitulo);
            Paragraph pObs = new Paragraph(cotizacion.getObservaciones(), fontNormal);
            document.add(pObs);
        }

        // === PIE ===
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));
        Paragraph pie1 = new Paragraph("Este documento es una cotización preliminar y no representa un comprobante de pago.", fontPequena);
        pie1.setAlignment(Element.ALIGN_CENTER);
        document.add(pie1);
        Paragraph pie2 = new Paragraph("Los precios pueden variar después de la fecha de validez.", fontPequena);
        pie2.setAlignment(Element.ALIGN_CENTER);
        document.add(pie2);
        Paragraph pie3 = new Paragraph("Gracias por su preferencia.", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9));
        pie3.setAlignment(Element.ALIGN_CENTER);
        document.add(pie3);

        document.close();
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell cellLabel = new PdfPCell(new Phrase(label, font));
        cellLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cellLabel.setBorder(Rectangle.NO_BORDER);
        cellLabel.setPadding(4);
        table.addCell(cellLabel);

        PdfPCell cellValue = new PdfPCell(new Phrase("S/ " + value, font));
        cellValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cellValue.setBorder(Rectangle.NO_BORDER);
        cellValue.setPadding(4);
        table.addCell(cellValue);
    }

    private String safe(String s) {
        return s != null ? s : "-";
    }

    private String formatMoney(BigDecimal val) {
        if (val == null) return "0.00";
        return String.format("%,.2f", val);
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
