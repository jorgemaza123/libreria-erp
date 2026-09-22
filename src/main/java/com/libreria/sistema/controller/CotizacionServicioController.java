package com.libreria.sistema.controller;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.CotizacionServicio;
import com.libreria.sistema.model.CotizacionServicioItem;
import com.libreria.sistema.model.CotizacionServicioSeccion;
import com.libreria.sistema.model.dto.CotizacionServicioDTO;
import com.libreria.sistema.repository.CotizacionServicioRepository;
import com.libreria.sistema.service.ConfiguracionService;
import com.lowagie.text.Chunk;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cotizaciones-servicio")
@Slf4j
@PreAuthorize("isAuthenticated()")
public class CotizacionServicioController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODIGO_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final CotizacionServicioRepository cotizacionRepository;
    private final ConfiguracionService configuracionService;

    public CotizacionServicioController(CotizacionServicioRepository cotizacionRepository,
                                        ConfiguracionService configuracionService) {
        this.cotizacionRepository = cotizacionRepository;
        this.configuracionService = configuracionService;
    }

    @GetMapping("/nueva")
    @PreAuthorize("isAuthenticated()")
    public String nueva(Model model) {
        return "cotizaciones-servicio/formulario";
    }

    @GetMapping("/lista")
    public String lista(Model model) {
        List<CotizacionServicio> cotizaciones = cotizacionRepository.findAll().stream()
                .sorted(Comparator.comparing(CotizacionServicio::getId, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
        model.addAttribute("cotizaciones", cotizaciones);
        return "cotizaciones-servicio/lista";
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("isAuthenticated()")
    public String editar(@PathVariable Long id, Model model) {
        CotizacionServicio cotizacion = cotizacionRepository.findDetalleById(id).orElse(null);
        if (cotizacion == null) {
            return "redirect:/cotizaciones-servicio/lista";
        }
        model.addAttribute("cotizacionEdicion", cotizacion);
        return "cotizaciones-servicio/formulario";
    }

    @PostMapping("/api/guardar")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<?> guardar(@RequestBody CotizacionServicioDTO dto) {
        try {
            CotizacionServicio cotizacion = dto.getId() != null
                    ? cotizacionRepository.findDetalleById(dto.getId()).orElse(new CotizacionServicio())
                    : new CotizacionServicio();

            if (cotizacion.getCodigoPublico() == null || cotizacion.getCodigoPublico().isBlank()) {
                cotizacion.setCodigoPublico(generarCodigoPublico());
            }

            cotizacion.setTipoServicio(dto.getTipoServicio());
            cotizacion.setTituloTrabajo(dto.getTituloTrabajo());
            cotizacion.setClienteNombre(dto.getClienteNombre());
            cotizacion.setClienteTelefono(dto.getClienteTelefono());
            cotizacion.setClienteDocumento(dto.getClienteDocumento());
            cotizacion.setClienteEmail(dto.getClienteEmail());
            cotizacion.setClienteDireccion(dto.getClienteDireccion());
            cotizacion.setFechaEntregaEstimada(dto.getFechaEntrega());
            cotizacion.setFechaValidez(dto.getFechaValidez());
            cotizacion.setObservaciones(dto.getObservaciones());
            cotizacion.setEstado("EMITIDA");

            cotizacion.getSecciones().clear();
            BigDecimal totalGeneral = BigDecimal.ZERO;

            if (dto.getSecciones() != null) {
                int orden = 1;
                for (CotizacionServicioDTO.SeccionDTO seccionDto : dto.getSecciones()) {
                    if (seccionDto == null || seccionDto.getItems() == null || seccionDto.getItems().isEmpty()) {
                        continue;
                    }

                    CotizacionServicioSeccion seccion = new CotizacionServicioSeccion();
                    seccion.setTitulo(valor(seccionDto.getTitulo(), "Alternativa " + orden));
                    seccion.setOrden(orden++);
                    seccion.setCotizacion(cotizacion);

                    BigDecimal totalSeccion = BigDecimal.ZERO;
                    for (CotizacionServicioDTO.ItemDTO itemDto : seccionDto.getItems()) {
                        if (itemDto == null || itemDto.getDescripcion() == null || itemDto.getDescripcion().isBlank()) {
                            continue;
                        }
                        CotizacionServicioItem item = new CotizacionServicioItem();
                        item.setDescripcion(itemDto.getDescripcion().trim());
                        item.setCosto(valorMonetario(itemDto.getCosto()));
                        item.setSeccion(seccion);
                        seccion.getItems().add(item);
                        totalSeccion = totalSeccion.add(item.getCosto());
                    }

                    if (!seccion.getItems().isEmpty()) {
                        seccion.setTotal(totalSeccion);
                        cotizacion.getSecciones().add(seccion);
                        totalGeneral = totalGeneral.add(totalSeccion);
                    }
                }
            }

            if (cotizacion.getSecciones().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Debe agregar al menos una sección con ítems."));
            }

            cotizacion.setTotal(totalGeneral);
            CotizacionServicio guardada = cotizacionRepository.save(cotizacion);

            return ResponseEntity.ok(Map.of(
                    "message", "Cotización de servicio guardada",
                    "id", guardada.getId(),
                    "codigoPublico", guardada.getCodigoPublico(),
                    "total", valorMonetario(guardada.getTotal())
            ));
        } catch (Exception e) {
            log.error("Error al guardar cotizacion de servicio", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error al procesar la cotización: " + e.getMessage()));
        }
    }

    @GetMapping("/pdf/{id}")
    public void descargarPdf(@PathVariable Long id, HttpServletResponse response) throws IOException, DocumentException {
        CotizacionServicio cotizacion = cotizacionRepository.findDetalleById(id).orElse(null);
        if (cotizacion == null) {
            response.sendError(404, "Cotización no encontrada");
            return;
        }

        Configuracion config = configuracionService.obtenerConfiguracion();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=Propuesta_" + cotizacion.getCodigoPublico() + ".pdf");

        Document document = new Document(PageSize.A4, 25, 25, 25, 25);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        Color colorPrimario = parseColor(config.getColorPrimario(), new Color(7, 77, 140));
        Color colorBanner = new Color(0, 51, 102);
        Color colorZebra = new Color(225, 240, 255);
        Color colorSuave = new Color(247, 250, 252);
        Color colorWhite = Color.WHITE;

        Font fontCompany = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, colorPrimario);
        Font fontContractTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, colorWhite);
        Font fontLabelBlue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, colorPrimario);
        Font fontValue = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font fontSmallBlue = FontFactory.getFont(FontFactory.HELVETICA, 8, colorPrimario);
        Font fontHeaderTable = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, colorWhite);
        Font fontSection = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, colorPrimario);
        Font fontTerms = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(100, 100, 100));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter fmtDateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        PdfPCell bannerCell = new PdfPCell(new Phrase("PROPUESTA COMERCIAL DE SERVICIO", fontContractTitle));
        bannerCell.setBackgroundColor(colorBanner);
        bannerCell.setPadding(10f);
        bannerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        bannerCell.setBorder(Rectangle.NO_BORDER);
        banner.addCell(bannerCell);
        document.add(banner);
        document.add(new Paragraph(" ", fontSmallBlue));

        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{1.5f, 3.5f});
        headerTable.addCell(crearCeldaLogo(config));

        PdfPCell infoEmpresa = new PdfPCell();
        infoEmpresa.setBorder(Rectangle.NO_BORDER);
        infoEmpresa.setPaddingLeft(15f);
        infoEmpresa.addElement(new Paragraph("CHROMA", fontCompany));
        infoEmpresa.addElement(new Paragraph("Soluciones Profesionales | RUC: " + valor(config.getRuc(), "20000000001"), fontSmallBlue));
        infoEmpresa.addElement(new Paragraph("Código de propuesta: " + valor(cotizacion.getCodigoPublico(), "-"), fontLabelBlue));
        headerTable.addCell(infoEmpresa);
        document.add(headerTable);
        document.add(new Paragraph(" ", fontSmallBlue));

        PdfPTable mainData = new PdfPTable(2);
        mainData.setWidthPercentage(100);
        mainData.setWidths(new float[]{1f, 1f});

        PdfPCell leftCol = new PdfPCell();
        leftCol.setBorderColor(colorZebra);
        leftCol.setBorderWidth(1f);
        leftCol.setPadding(12f);
        agregarSeccionInfo(leftCol, "CLIENTE / EMPRESA SOLICITANTE", colorPrimario, fontHeaderTable);
        agregarDato(leftCol, "RAZÓN / NOMBRE:", valor(cotizacion.getClienteNombre(), "-"), fontLabelBlue, fontValue);
        agregarDato(leftCol, "IDENTIFICACIÓN:", valor(cotizacion.getClienteDocumento(), "-"), fontLabelBlue, fontValue);
        agregarDato(leftCol, "CONTACTO:", valor(cotizacion.getClienteTelefono(), "-"), fontLabelBlue, fontValue);
        agregarDato(leftCol, "UBICACIÓN:", valor(cotizacion.getClienteDireccion(), "-"), fontLabelBlue, fontValue);
        mainData.addCell(leftCol);

        PdfPCell rightCol = new PdfPCell();
        rightCol.setBorderColor(colorZebra);
        rightCol.setBorderWidth(1f);
        rightCol.setPadding(12f);
        agregarSeccionInfo(rightCol, "ALCANCE DE LA PROPUESTA", colorPrimario, fontHeaderTable);
        agregarDato(rightCol, "SERVICIO:", valor(cotizacion.getTipoServicio(), "SERVICIO").toUpperCase(), fontLabelBlue, fontValue);
        agregarDato(rightCol, "TRABAJO:", valor(cotizacion.getTituloTrabajo(), "-").toUpperCase(), fontLabelBlue, fontValue);
        agregarDato(rightCol, "EMISIÓN:", cotizacion.getFechaCreacion() != null ? cotizacion.getFechaCreacion().format(fmtDateTime) : "-", fontLabelBlue, fontValue);
        agregarDato(rightCol, "VALIDEZ:", formatearFecha(cotizacion.getFechaValidez(), fmt), fontLabelBlue, fontValue);
        mainData.addCell(rightCol);

        document.add(mainData);
        document.add(new Paragraph(" ", fontSmallBlue));

        List<CotizacionServicioSeccion> secciones = cotizacion.getSecciones().stream()
                .sorted(Comparator.comparing(CotizacionServicioSeccion::getOrden, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        for (CotizacionServicioSeccion seccion : secciones) {
            PdfPTable sectionHeader = new PdfPTable(2);
            sectionHeader.setWidthPercentage(100);
            sectionHeader.setWidths(new float[]{4f, 1.2f});

            PdfPCell sectionTitle = new PdfPCell(new Phrase(valor(seccion.getTitulo(), "Alternativa"), fontSection));
            sectionTitle.setBackgroundColor(colorSuave);
            sectionTitle.setBorderColor(colorZebra);
            sectionTitle.setPadding(8f);
            sectionHeader.addCell(sectionTitle);

            PdfPCell sectionTotal = new PdfPCell(new Phrase("Total: S/ " + formatMoney(seccion.getTotal()), fontSection));
            sectionTotal.setBackgroundColor(colorSuave);
            sectionTotal.setBorderColor(colorZebra);
            sectionTotal.setPadding(8f);
            sectionTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sectionHeader.addCell(sectionTotal);
            document.add(sectionHeader);

            PdfPTable tableItems = new PdfPTable(3);
            tableItems.setWidthPercentage(100);
            tableItems.setWidths(new float[]{0.5f, 4.5f, 1.2f});

            String[] headers = {"#", "DESCRIPCIÓN DEL SERVICIO / ENTREGABLE", "IMPORTE"};
            for (String h : headers) {
                PdfPCell c = new PdfPCell(new Phrase(h, fontHeaderTable));
                c.setBackgroundColor(colorPrimario);
                c.setPadding(8f);
                c.setBorder(Rectangle.NO_BORDER);
                c.setHorizontalAlignment(h.contains("IMPORTE") ? Element.ALIGN_RIGHT : (h.equals("#") ? Element.ALIGN_CENTER : Element.ALIGN_LEFT));
                tableItems.addCell(c);
            }

            int index = 1;
            for (CotizacionServicioItem item : seccion.getItems()) {
                Color rowColor = (index % 2 == 0) ? colorZebra : colorWhite;
                tableItems.addCell(celdaModerna(String.valueOf(index++), fontValue, Element.ALIGN_CENTER, rowColor));
                tableItems.addCell(celdaModerna(valor(item.getDescripcion(), "-"), fontValue, Element.ALIGN_LEFT, rowColor));
                tableItems.addCell(celdaModerna("S/ " + formatMoney(item.getCosto()),
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, colorPrimario),
                        Element.ALIGN_RIGHT, rowColor));
            }
            document.add(tableItems);
            document.add(new Paragraph(" ", fontSmallBlue));
        }

        PdfPTable finalBlock = new PdfPTable(2);
        finalBlock.setWidthPercentage(100);
        finalBlock.setWidths(new float[]{2.4f, 1.6f});

        PdfPCell termsCell = new PdfPCell();
        termsCell.setBorder(Rectangle.NO_BORDER);
        termsCell.setPaddingRight(18f);
        termsCell.addElement(new Paragraph("CONDICIONES COMERCIALES:", fontLabelBlue));
        termsCell.addElement(new Paragraph(
                "1. Esta propuesta presenta valores estimados segun el alcance solicitado.\n" +
                        "2. La ejecución inicia solo después de la confirmación formal del cliente.\n" +
                        "3. Cambios de alcance, cantidades o materiales pueden modificar los importes.\n" +
                        "4. CHROMA acompaña cada proyecto con criterio técnico, orden y cuidado visual.",
                fontTerms));
        if (cotizacion.getObservaciones() != null && !cotizacion.getObservaciones().isBlank()) {
            termsCell.addElement(new Paragraph("\nNOTAS DE LA PROPUESTA:", fontLabelBlue));
            termsCell.addElement(new Paragraph(cotizacion.getObservaciones(), fontTerms));
        }
        finalBlock.addCell(termsCell);

        PdfPCell totalsCell = new PdfPCell();
        totalsCell.setBorder(Rectangle.NO_BORDER);
        PdfPTable subtotTable = new PdfPTable(2);
        subtotTable.setWidthPercentage(100);

        PdfPCell labelTotal = new PdfPCell(new Phrase("TOTAL REFERENCIAL:", fontHeaderTable));
        labelTotal.setBackgroundColor(colorPrimario);
        labelTotal.setPadding(8f);
        labelTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelTotal.setBorder(Rectangle.NO_BORDER);
        subtotTable.addCell(labelTotal);

        PdfPCell valTotal = new PdfPCell(new Phrase("S/ " + formatMoney(cotizacion.getTotal()), fontHeaderTable));
        valTotal.setBackgroundColor(colorPrimario);
        valTotal.setPadding(8f);
        valTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valTotal.setBorder(Rectangle.NO_BORDER);
        subtotTable.addCell(valTotal);

        totalsCell.addElement(subtotTable);
        finalBlock.addCell(totalsCell);
        document.add(finalBlock);

        document.add(new Paragraph("\n\n", fontSmallBlue));
        Paragraph finalMsg = new Paragraph("PROPUESTA EMITIDA POR CHROMA\n" +
                "WhatsApp: " + valor(config.getTelefono(), "902843481") + " | " + valor(config.getDireccion(), "Calle Real 123"), fontSmallBlue);
        finalMsg.setAlignment(Element.ALIGN_CENTER);
        document.add(finalMsg);

        document.close();
    }

    private String generarCodigoPublico() {
        String codigo;
        do {
            StringBuilder builder = new StringBuilder("COT-SV-");
            for (int i = 0; i < 6; i++) {
                builder.append(CODIGO_CHARS.charAt(RANDOM.nextInt(CODIGO_CHARS.length())));
            }
            codigo = builder.toString();
        } while (cotizacionRepository.existsByCodigoPublico(codigo));
        return codigo;
    }

    private PdfPCell crearCeldaLogo(Configuracion config) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (config.getLogoBase64() != null && !config.getLogoBase64().isBlank()) {
            try {
                byte[] logoBytes = Base64.getDecoder().decode(config.getLogoBase64());
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(70, 70);
                cell.addElement(logo);
                return cell;
            } catch (Exception e) {
                log.warn("No se pudo cargar el logo para cotización de servicio PDF", e);
            }
        }
        cell.addElement(new Paragraph(" "));
        return cell;
    }

    private void agregarSeccionInfo(PdfPCell container, String titulo, Color color, Font font) {
        PdfPCell titleCell = new PdfPCell(new Phrase(titulo, font));
        titleCell.setBackgroundColor(color);
        titleCell.setPadding(4f);
        titleCell.setBorder(Rectangle.NO_BORDER);

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(6f);
        table.addCell(titleCell);
        container.addElement(table);
    }

    private void agregarDato(PdfPCell container, String label, String value, Font fLabel, Font fValue) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + " ", fLabel));
        p.add(new Chunk(value, fValue));
        p.setSpacingAfter(3f);
        container.addElement(p);
    }

    private PdfPCell celdaModerna(String texto, Font font, int align, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6f);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(new Color(230, 230, 230));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private String valor(String valor, String fallback) {
        return valor != null && !valor.isBlank() ? valor : fallback;
    }

    private String formatearFecha(LocalDate fecha, DateTimeFormatter formatter) {
        return fecha != null ? fecha.format(formatter) : "POR DEFINIR";
    }

    private BigDecimal valorMonetario(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private String formatMoney(BigDecimal value) {
        return String.format("%,.2f", valorMonetario(value));
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
