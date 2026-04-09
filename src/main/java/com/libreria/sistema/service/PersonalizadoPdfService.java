package com.libreria.sistema.service;

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
import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.PedidoPersonalizado;
import com.libreria.sistema.model.PedidoPersonalizadoComponente;
import com.libreria.sistema.model.PedidoPersonalizadoItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.File;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
public class PersonalizadoPdfService {

    private final ConfiguracionService configuracionService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public PersonalizadoPdfService(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    public void generarPedidoPdf(PedidoPersonalizado pedido, OutputStream outputStream) throws Exception {
        Configuracion config = configuracionService.obtenerConfiguracion();
        Document document = new Document(PageSize.A4, 32, 32, 32, 32);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(35, 48, 68));
        Font fontSubtitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
        Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font fontSmall = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        Font fontWhite = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Color primary = new Color(30, 76, 122);

        Paragraph empresa = new Paragraph(valor(config.getNombreEmpresa(), "PERSONALIZADO"), fontTitle);
        empresa.setAlignment(Element.ALIGN_LEFT);
        document.add(empresa);

        Paragraph docTitle = new Paragraph("PEDIDO PERSONALIZADO " + valor(pedido.getCodigoPedido(), ""), fontSubtitle);
        docTitle.setSpacingAfter(8);
        document.add(docTitle);

        Paragraph empresaData = new Paragraph(
                "RUC: " + valor(config.getRuc(), "-")
                        + "   Tel: " + valor(config.getTelefono(), "-")
                        + "   Email: " + valor(config.getEmail(), "-"),
                fontSmall);
        empresaData.setSpacingAfter(10);
        document.add(empresaData);

        PdfPTable resumen = new PdfPTable(2);
        resumen.setWidthPercentage(100);
        resumen.setWidths(new float[]{1.2f, 1.8f});
        resumen.addCell(celdaSeccion("DATOS DEL CLIENTE", fontWhite, primary, 2));
        resumen.addCell(celdaDato("Cliente", valor(pedido.getClienteNombre(), "PUBLICO GENERAL"), fontNormal));
        resumen.addCell(celdaDato("Documento", valor(pedido.getClienteNumeroDocumento(), "-"), fontNormal));
        resumen.addCell(celdaDato("WhatsApp", valor(pedido.getClienteWhatsapp(), pedido.getClienteTelefono()), fontNormal));
        resumen.addCell(celdaDato("Destinatario", valor(pedido.getNombreDestinatario(), "-"), fontNormal));
        resumen.addCell(celdaDato("Entrega", valor(pedido.getModoEntrega(), "RECOJO_TIENDA"), fontNormal));
        resumen.addCell(celdaDato("Fecha solicitada",
                pedido.getFechaEntregaSolicitada() != null ? pedido.getFechaEntregaSolicitada().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "-",
                fontNormal));
        resumen.addCell(celdaDato("Franja", valor(pedido.getFranjaEntrega(), "-"), fontNormal));
        if (pedido.getDireccionEntrega() != null && !pedido.getDireccionEntrega().isBlank()) {
            resumen.addCell(celdaDato("Dirección",
                    pedido.getDireccionEntrega() + " / " + valor(pedido.getDistrito(), "-") + " / "
                            + valor(pedido.getProvincia(), "-") + " / " + valor(pedido.getDepartamento(), "-"),
                    fontNormal, 2));
        }
        if (pedido.getDedicatoria() != null && !pedido.getDedicatoria().isBlank()) {
            resumen.addCell(celdaDato("Dedicatoria", pedido.getDedicatoria(), fontNormal, 2));
        }
        if (pedido.getNombreEtiqueta() != null && !pedido.getNombreEtiqueta().isBlank()) {
            resumen.addCell(celdaDato("Etiqueta / Nombre", pedido.getNombreEtiqueta(), fontNormal, 2));
        }
        document.add(resumen);
        document.add(new Paragraph(" "));

        for (PedidoPersonalizadoItem item : pedido.getItems()) {
            PdfPTable bloque = new PdfPTable(2);
            bloque.setWidthPercentage(100);
            bloque.setWidths(new float[]{1.1f, 2.2f});
            bloque.setSpacingAfter(10);

            PdfPCell imagenCell = new PdfPCell();
            imagenCell.setBorder(Rectangle.BOX);
            imagenCell.setPadding(6);
            imagenCell.setMinimumHeight(140);
            imagenCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            imagenCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            agregarImagen(item.getFotoSnapshot(), imagenCell);
            bloque.addCell(imagenCell);

            PdfPCell infoCell = new PdfPCell();
            infoCell.setBorder(Rectangle.BOX);
            infoCell.setPadding(8);
            infoCell.addElement(new Paragraph(
                    valor(item.getCodigoModeloSnapshot(), "MODELO") + " - " + valor(item.getNombreComercialSnapshot(), "Personalizado"),
                    fontSubtitle));
            infoCell.addElement(new Paragraph("Categoría: " + valor(item.getCategoriaSnapshot(), "PERSONALIZADO"), fontNormal));
            infoCell.addElement(new Paragraph("Cantidad: " + formatear(item.getCantidad()), fontNormal));
            infoCell.addElement(new Paragraph("Precio final: S/ " + money(item.getPrecioFinal()), fontNormal));
            infoCell.addElement(new Paragraph("Precio sugerido: S/ " + money(item.getPrecioSugeridoSnapshot()), fontNormal));
            infoCell.addElement(new Paragraph(" ", fontSmall));
            infoCell.addElement(new Paragraph("Incluye:", fontSubtitle));

            List<PedidoPersonalizadoComponente> componentes = item.getComponentes().stream()
                    .sorted(Comparator.comparing(PedidoPersonalizadoComponente::getOrden))
                    .filter(c -> !Boolean.TRUE.equals(c.getEliminado()))
                    .toList();

            if (componentes.isEmpty()) {
                infoCell.addElement(new Paragraph("- Configuración libre", fontNormal));
            } else {
                for (PedidoPersonalizadoComponente componente : componentes) {
                    String prefijo = Boolean.TRUE.equals(componente.getIncluido()) ? "• " : "◦ ";
                    String texto = prefijo + valor(componente.getNombre(), "Componente")
                            + " x " + formatear(componente.getCantidad());
                    if (componente.getCategoria() != null && !componente.getCategoria().isBlank()) {
                        texto += " (" + componente.getCategoria() + ")";
                    }
                    infoCell.addElement(new Paragraph(texto, fontNormal));
                }
            }
            bloque.addCell(infoCell);
            document.add(bloque);
        }

        PdfPTable totales = new PdfPTable(2);
        totales.setWidthPercentage(42);
        totales.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totales.setWidths(new float[]{1.5f, 1f});
        totales.addCell(celdaSeccion("RESUMEN", fontWhite, primary, 2));
        totales.addCell(celdaTotal("Subtotal", fontNormal));
        totales.addCell(celdaTotalValor("S/ " + money(pedido.getSubtotal()), fontNormal));
        totales.addCell(celdaTotal("Descuento", fontNormal));
        totales.addCell(celdaTotalValor("S/ " + money(pedido.getDescuento()), fontNormal));
        totales.addCell(celdaTotal("Envío", fontNormal));
        totales.addCell(celdaTotalValor(Boolean.TRUE.equals(pedido.getEnvioGratis()) ? "GRATIS" : "S/ " + money(pedido.getCostoEnvio()), fontNormal));
        totales.addCell(celdaTotal("Total", fontSubtitle));
        totales.addCell(celdaTotalValor("S/ " + money(pedido.getTotal()), fontSubtitle));
        totales.addCell(celdaTotal("Adelanto", fontNormal));
        totales.addCell(celdaTotalValor("S/ " + money(pedido.getAdelanto()), fontNormal));
        totales.addCell(celdaTotal("Saldo", fontSubtitle));
        totales.addCell(celdaTotalValor("S/ " + money(pedido.getSaldo()), fontSubtitle));
        document.add(totales);

        Paragraph vigencia = new Paragraph("Vigencia del precio: sujeto a confirmación y stock de insumos al momento del cierre.", fontSmall);
        vigencia.setSpacingBefore(10);
        document.add(vigencia);

        if (pedido.getObservacionesCliente() != null && !pedido.getObservacionesCliente().isBlank()) {
            Paragraph obs = new Paragraph("Observaciones: " + pedido.getObservacionesCliente(), fontSmall);
            obs.setSpacingBefore(6);
            document.add(obs);
        }

        document.close();
    }

    private void agregarImagen(String foto, PdfPCell cell) {
        try {
            if (foto == null || foto.isBlank()) {
                cell.addElement(new Paragraph("Sin foto", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.GRAY)));
                return;
            }
            Path path = Paths.get(uploadDir).toAbsolutePath().resolve(foto);
            if (!Files.exists(path)) {
                cell.addElement(new Paragraph("Foto no disponible", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.GRAY)));
                return;
            }
            Image img = Image.getInstance(path.toFile().getAbsolutePath());
            img.scaleToFit(150, 120);
            img.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(img);
        } catch (Exception e) {
            cell.addElement(new Paragraph("No se pudo cargar la foto", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.GRAY)));
        }
    }

    private PdfPCell celdaSeccion(String texto, Font font, Color color, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(color);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(6);
        cell.setColspan(colspan);
        return cell;
    }

    private PdfPCell celdaDato(String label, String valor, Font font) {
        return celdaDato(label, valor, font, 1);
    }

    private PdfPCell celdaDato(String label, String valor, Font font, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(label + ": " + valor(valor, "-"), font));
        cell.setPadding(5);
        cell.setColspan(colspan);
        return cell;
    }

    private PdfPCell celdaTotal(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        return cell;
    }

    private PdfPCell celdaTotalValor(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private String valor(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatear(BigDecimal value) {
        return money(value);
    }

    private String money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
