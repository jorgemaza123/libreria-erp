package com.libreria.sistema.service;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.Cotizacion;
import com.libreria.sistema.model.DetalleCotizacion;
import com.libreria.sistema.model.OrdenServicio;
import com.libreria.sistema.repository.OrdenServicioRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;

@Service
@Slf4j
public class CotizacionContratoPdfService {

    private final ConfiguracionService configuracionService;
    private final OrdenServicioRepository ordenServicioRepository;

    public CotizacionContratoPdfService(ConfiguracionService configuracionService,
                                        OrdenServicioRepository ordenServicioRepository) {
        this.configuracionService = configuracionService;
        this.ordenServicioRepository = ordenServicioRepository;
    }

    public void generarPdf(Cotizacion cotizacion, OutputStream outputStream) throws DocumentException {
        Configuracion config = configuracionService.obtenerConfiguracion();
        Optional<OrdenServicio> ordenOpt = cotizacion.getId() != null
                ? ordenServicioRepository.findByCotizacionId(cotizacion.getId())
                : Optional.empty();

        Document document = new Document(PageSize.A4, 30, 30, 26, 28);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        Color colorPrimario = parseColor(config.getColorPrimario(), new Color(7, 77, 140));
        Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font fontSmall = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, colorPrimario);
        Font fontCompany = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        Font fontHeaderWhite = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font fontEmphasis = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

        boolean aprobada = "APROBADA".equalsIgnoreCase(cotizacion.getEstado());
        boolean tieneServicios = cotizacion.getItems() != null && cotizacion.getItems().stream()
                .anyMatch(item -> "SERVICIO".equalsIgnoreCase(item.getTipoItem()));
        String tituloDocumento = resolverTituloDocumento(aprobada, tieneServicios);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        PdfPTable tablaHeader = new PdfPTable(3);
        tablaHeader.setWidthPercentage(100);
        tablaHeader.setWidths(new float[]{1.2f, 3.1f, 2.1f});

        tablaHeader.addCell(crearCeldaLogo(config));

        PdfPCell empresa = new PdfPCell();
        empresa.setBorder(Rectangle.NO_BORDER);
        empresa.addElement(new Paragraph(valor(config.getNombreEmpresa(), "NEGOCIO"), fontCompany));
        empresa.addElement(new Paragraph("RUC: " + valor(config.getRuc(), "-"), fontBold));
        empresa.addElement(new Paragraph(valor(config.getDireccion(), "-"), fontNormal));
        empresa.addElement(new Paragraph("Tel: " + valor(config.getTelefono(), "-"), fontNormal));
        empresa.addElement(new Paragraph("Email: " + valor(config.getEmail(), "-"), fontSmall));
        tablaHeader.addCell(empresa);

        PdfPCell documentoBox = new PdfPCell();
        documentoBox.setBorderColor(colorPrimario);
        documentoBox.setBorderWidth(1.8f);
        documentoBox.setPadding(8);
        documentoBox.setHorizontalAlignment(Element.ALIGN_CENTER);
        documentoBox.addElement(parrafoCentrado("R.U.C. " + valor(config.getRuc(), "-"), fontBold));
        documentoBox.addElement(parrafoCentrado(tituloDocumento, fontTitle));
        documentoBox.addElement(parrafoCentrado(cotizacion.getSerie() + " - " + String.format("%06d", cotizacion.getNumero()), fontBold));
        documentoBox.addElement(parrafoCentrado("ESTADO: " + valor(cotizacion.getEstado(), "EMITIDA"), fontSmall));
        tablaHeader.addCell(documentoBox);
        document.add(tablaHeader);

        document.add(new Paragraph(" "));

        PdfPTable resumen = new PdfPTable(2);
        resumen.setWidthPercentage(100);
        resumen.setWidths(new float[]{1.5f, 1.5f});
        resumen.addCell(celdaBloque(
                "DATOS DEL CLIENTE",
                new String[]{
                        "Cliente: " + valor(cotizacion.getClienteNombre(), "-"),
                        "Documento: " + valor(cotizacion.getClienteDocumento(), "-"),
                        "Teléfono: " + valor(cotizacion.getClienteTelefono(), "-"),
                        "Email: " + valor(cotizacion.getClienteEmail(), "-"),
                        "Dirección: " + valor(cotizacion.getClienteDireccion(), "-")
                },
                fontBold, fontNormal, colorPrimario));
        resumen.addCell(celdaBloque(
                "DATOS DEL SERVICIO",
                new String[]{
                        "Tipo: " + valor(cotizacion.getTipoServicioContrato(), tieneServicios ? "SERVICIO" : "PROPUESTA"),
                        "Proyecto / trabajo: " + resolverTituloProyecto(cotizacion),
                        "Fecha emisión: " + formatearFecha(cotizacion.getFechaEmision(), fmt),
                        "Fecha entrega: " + formatearFecha(cotizacion.getFechaEntregaComprometida(), fmt),
                        "Orden vinculada: " + ordenOpt.map(orden -> "OS-" + String.format("%06d", orden.getId())).orElse("Pendiente")
                },
                fontBold, fontNormal, colorPrimario));
        document.add(resumen);

        document.add(new Paragraph(" "));

        Paragraph intro = new Paragraph(
                "Por la presente se hace entrega de la presente cotización en fecha "
                        + formatearFecha(cotizacion.getFechaEmision(), fmt)
                        + ", correspondiente al cliente " + valor(cotizacion.getClienteNombre(), "-")
                        + ", identificado con " + valor(cotizacion.getClienteDocumento(), "-") + ".",
                fontNormal);
        intro.setLeading(14f);
        document.add(intro);

        Paragraph segundo = new Paragraph(
                "Se deja constancia de que el servicio o proyecto denominado "
                        + resolverTituloProyecto(cotizacion)
                        + " tiene un importe total de S/ " + formatMoney(cotizacion.getTotal()) + ". "
                        + "A la fecha se registra un abono de S/ " + formatMoney(cotizacion.getMontoInicial())
                        + ", quedando un saldo pendiente de S/ " + formatMoney(resolverSaldo(cotizacion)) + ".",
                fontNormal);
        segundo.setLeading(14f);
        segundo.setSpacingBefore(6f);
        document.add(segundo);

        Paragraph tercero = new Paragraph(
                "La fecha estimada de entrega o culminación del servicio será "
                        + formatearFecha(cotizacion.getFechaEntregaComprometida(), fmt)
                        + ", salvo coordinación posterior entre las partes. "
                        + "El presente documento constituye constancia comercial de aceptación y seguimiento interno del servicio.",
                fontNormal);
        tercero.setLeading(14f);
        tercero.setSpacingBefore(6f);
        document.add(tercero);

        document.add(new Paragraph(" "));

        PdfPTable tablaItems = new PdfPTable(5);
        tablaItems.setWidthPercentage(100);
        tablaItems.setWidths(new float[]{0.8f, 3.6f, 1.4f, 1.2f, 1.3f});
        agregarHeader(tablaItems, new String[]{"CANT.", "DESCRIPCIÓN", "TIPO", "P. UNIT.", "IMPORTE"}, fontHeaderWhite);
        for (DetalleCotizacion item : cotizacion.getItems()) {
            BigDecimal cantidad = item.getCantidad() != null ? item.getCantidad() : BigDecimal.ONE;
            BigDecimal precio = item.getPrecioUnitario() != null ? item.getPrecioUnitario() : BigDecimal.ZERO;
            BigDecimal subtotal = item.getSubtotal() != null ? item.getSubtotal() : cantidad.multiply(precio);
            tablaItems.addCell(celdaTabla(cantidad.stripTrailingZeros().toPlainString(), fontNormal, Element.ALIGN_CENTER));
            tablaItems.addCell(celdaTabla(valor(item.getDescripcion(), "-"), fontNormal, Element.ALIGN_LEFT));
            tablaItems.addCell(celdaTabla("SERVICIO".equalsIgnoreCase(item.getTipoItem()) ? "SERVICIO" : "ITEM", fontSmall, Element.ALIGN_CENTER));
            tablaItems.addCell(celdaTabla("S/ " + formatMoney(precio), fontNormal, Element.ALIGN_RIGHT));
            tablaItems.addCell(celdaTabla("S/ " + formatMoney(subtotal), fontNormal, Element.ALIGN_RIGHT));
        }
        document.add(tablaItems);

        document.add(new Paragraph(" "));

        PdfPTable tablaMontos = new PdfPTable(2);
        tablaMontos.setWidthPercentage(42);
        tablaMontos.setHorizontalAlignment(Element.ALIGN_RIGHT);
        addMontoRow(tablaMontos, "TOTAL:", "S/ " + formatMoney(cotizacion.getTotal()), fontBold);
        addMontoRow(tablaMontos, "ABONADO:", "S/ " + formatMoney(cotizacion.getMontoInicial()), fontNormal);
        addMontoRow(tablaMontos, "SALDO PENDIENTE:", "S/ " + formatMoney(resolverSaldo(cotizacion)), fontEmphasis);
        document.add(tablaMontos);

        document.add(new Paragraph(" "));

        document.add(bloqueTexto(
                "ALCANCE GENERAL",
                "La presente cotización resume el trabajo ofrecido, los bienes o servicios incluidos y el compromiso comercial asumido con el cliente conforme a las descripciones consignadas en el detalle precedente.",
                fontBold, fontNormal));
        document.add(bloqueTexto(
                "FORMA DE PAGO",
                "La modalidad pactada es " + valor(cotizacion.getFormaPago(), "CONTADO")
                        + (cotizacion.getMetodoPago() != null ? ", mediante " + cotizacion.getMetodoPago().toLowerCase() : "")
                        + ". El adelanto registrado asciende a S/ " + formatMoney(cotizacion.getMontoInicial())
                        + " y el saldo pendiente asciende a S/ " + formatMoney(resolverSaldo(cotizacion)) + ".",
                fontBold, fontNormal));
        document.add(bloqueTexto(
                "PLAZO ESTIMADO",
                "El plazo estimado para ejecución o entrega es hasta el "
                        + formatearFecha(cotizacion.getFechaEntregaComprometida(), fmt)
                        + ", salvo coordinación posterior entre las partes o ajustes derivados del alcance del servicio.",
                fontBold, fontNormal));
        document.add(bloqueTexto(
                "CONFORMIDAD COMERCIAL",
                "El presente documento se emite como constancia formal del servicio cotizado y de sus condiciones económicas. "
                        + "No constituye factura, boleta ni comprobante fiscal SUNAT; su finalidad es comercial y de seguimiento interno del proyecto.",
                fontBold, fontNormal));

        if (cotizacion.getCondiciones() != null && !cotizacion.getCondiciones().isBlank()) {
            document.add(bloqueTexto("CONDICIONES", cotizacion.getCondiciones(), fontBold, fontNormal));
        }
        if (cotizacion.getObservaciones() != null && !cotizacion.getObservaciones().isBlank()) {
            document.add(bloqueTexto("OBSERVACIONES", cotizacion.getObservaciones(), fontBold, fontNormal));
        }

        document.add(new Paragraph(" "));
        document.add(parrafoCentrado("Documento interno no fiscal. No sustituye comprobante de pago SUNAT.", fontSmall));
        document.add(parrafoCentrado("Gracias por su confianza. Este documento respalda el seguimiento comercial y operativo del servicio.", fontSmall));
        document.close();
    }

    private PdfPCell crearCeldaLogo(Configuracion config) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (Boolean.TRUE.equals(config.getMostrarLogoEnReportes()) && config.getLogoBase64() != null && !config.getLogoBase64().isBlank()) {
            try {
                byte[] logoBytes = Base64.getDecoder().decode(config.getLogoBase64());
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(78, 78);
                cell.addElement(logo);
                return cell;
            } catch (Exception e) {
                log.warn("No se pudo cargar el logo para contrato PDF", e);
            }
        }
        cell.addElement(new Paragraph(" "));
        return cell;
    }

    private PdfPCell celdaBloque(String titulo, String[] lineas, Font tituloFont, Font textoFont, Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(color);
        cell.setPadding(7);
        Paragraph pTitulo = new Paragraph(titulo, tituloFont);
        pTitulo.setSpacingAfter(4f);
        cell.addElement(pTitulo);
        for (String linea : lineas) {
            cell.addElement(new Paragraph(linea, textoFont));
        }
        return cell;
    }

    private Paragraph bloqueTexto(String titulo, String contenido, Font tituloFont, Font contenidoFont) {
        Paragraph bloque = new Paragraph();
        bloque.setSpacingBefore(6f);
        bloque.add(new Phrase(titulo + "\n", tituloFont));
        bloque.add(new Phrase(contenido + "\n", contenidoFont));
        bloque.setLeading(14f);
        return bloque;
    }

    private void agregarHeader(PdfPTable table, String[] textos, Font font) {
        for (String texto : textos) {
            PdfPCell cell = new PdfPCell(new Phrase(texto, font));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBackgroundColor(new Color(45, 45, 45));
            cell.setPadding(6f);
            table.addCell(cell);
        }
    }

    private PdfPCell celdaTabla(String texto, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(5f);
        return cell;
    }

    private void addMontoRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(5f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(5f);
        table.addCell(valueCell);
    }

    private Paragraph parrafoCentrado(String texto, Font font) {
        Paragraph paragraph = new Paragraph(texto, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
    }

    private String resolverTituloDocumento(boolean aprobada, boolean tieneServicios) {
        if (tieneServicios) {
            return aprobada ? "CONTRATO / CONSTANCIA DE SERVICIO" : "PROFORMA DE SERVICIO";
        }
        return aprobada ? "CONSTANCIA COMERCIAL" : "PROFORMA COMERCIAL";
    }

    private String resolverTituloProyecto(Cotizacion cotizacion) {
        if (cotizacion.getTituloProyectoServicio() != null && !cotizacion.getTituloProyectoServicio().isBlank()) {
            return cotizacion.getTituloProyectoServicio().trim();
        }
        return cotizacion.getItems().stream()
                .map(DetalleCotizacion::getDescripcion)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst()
                .orElse("Servicio / proyecto comercial");
    }

    private String formatearFecha(LocalDate fecha, DateTimeFormatter formatter) {
        return fecha != null ? fecha.format(formatter) : "POR DEFINIR";
    }

    private BigDecimal resolverSaldo(Cotizacion cotizacion) {
        if (cotizacion.getSaldoPendiente() != null) {
            return cotizacion.getSaldoPendiente();
        }
        BigDecimal total = cotizacion.getTotal() != null ? cotizacion.getTotal() : BigDecimal.ZERO;
        BigDecimal abonado = cotizacion.getMontoInicial() != null ? cotizacion.getMontoInicial() : BigDecimal.ZERO;
        BigDecimal saldo = total.subtract(abonado);
        return saldo.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : saldo;
    }

    private String valor(String valor, String fallback) {
        return valor != null && !valor.isBlank() ? valor : fallback;
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal safe = value != null ? value : BigDecimal.ZERO;
        return String.format("%,.2f", safe);
    }

    private Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            return new Color(Integer.parseInt(clean, 16));
        } catch (Exception e) {
            return fallback;
        }
    }
}
