package com.libreria.sistema.controller;

import com.libreria.sistema.model.CategoriaMovimiento;
import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.OrdenItem;
import com.libreria.sistema.model.OrdenServicio;
import com.libreria.sistema.model.dto.OrdenDTO;
import com.libreria.sistema.repository.OrdenServicioRepository;
import com.libreria.sistema.service.CajaService;
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
import org.springframework.web.bind.annotation.RequestParam;

import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

@Controller
@RequestMapping("/ordenes")
@Slf4j
@PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_VER')")
public class OrdenServicioController {

    private final OrdenServicioRepository ordenRepository;
    private final CajaService cajaService;
    private final ConfiguracionService configuracionService;

    public OrdenServicioController(OrdenServicioRepository ordenRepository,
                                   CajaService cajaService,
                                   ConfiguracionService configuracionService) {
        this.ordenRepository = ordenRepository;
        this.cajaService = cajaService;
        this.configuracionService = configuracionService;
    }

    @GetMapping("/nueva")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_CREAR')")
    public String nuevaOrden(Model model) {
        model.addAttribute("tipos", ordenRepository.findTiposServicio());
        return "ordenes/formulario";
    }

    @GetMapping("/lista")
    public String listaOrdenes(Model model) {
        model.addAttribute("ordenes", ordenRepository.findAll());
        return "ordenes/lista";
    }

    @PostMapping("/api/guardar")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_CREAR') or hasPermission(null, 'ORDENES_SERVICIO_EDITAR')")
    @Transactional
    public ResponseEntity<?> guardarOrden(@RequestBody OrdenDTO dto) {
        try {
            boolean esEdicion = dto.getId() != null;
            OrdenServicio orden;
            if (dto.getId() != null) {
                orden = ordenRepository.findById(dto.getId()).orElse(new OrdenServicio());
                // Si es edición, limpiamos los items actuales para reemplazarlos con los nuevos
                if (orden.getId() != null) {
                    orden.getItems().clear();
                }
            } else {
                orden = new OrdenServicio();
            }

            orden.setTipoServicio(dto.getTipoServicio());
            orden.setTituloTrabajo(dto.getTituloTrabajo());
            orden.setClienteNombre(dto.getClienteNombre());
            orden.setClienteTelefono(dto.getClienteTelefono());
            orden.setClienteDocumento(dto.getClienteDocumento());
            orden.setClienteEmail(dto.getClienteEmail());
            orden.setClienteDireccion(dto.getClienteDireccion());
            orden.setFechaEntregaEstimada(dto.getFechaEntrega());
            orden.setObservaciones(dto.getObservaciones());
            
            // Aseguramos que aCuenta no sea null
            BigDecimal aCuentaDTO = dto.getACuenta() != null ? dto.getACuenta() : BigDecimal.ZERO;
            orden.setACuenta(aCuentaDTO);
            
            if (orden.getEstado() == null) {
                orden.setEstado("PENDIENTE");
            }

            BigDecimal total = BigDecimal.ZERO;
            if (dto.getItems() != null) {
                for (OrdenDTO.ItemDTO itemDto : dto.getItems()) {
                    OrdenItem item = new OrdenItem();
                    item.setDescripcion(itemDto.getDescripcion());
                    item.setCosto(itemDto.getCosto() != null ? itemDto.getCosto() : BigDecimal.ZERO);
                    item.setOrden(orden);
                    orden.getItems().add(item);
                    total = total.add(item.getCosto());
                }
            }
            orden.setTotal(total);

            // Validar que el adelanto no supere al total
            BigDecimal adelanto = orden.getACuenta();
            if (adelanto.compareTo(total) > 0) {
                adelanto = total;
                orden.setACuenta(total);
            }
            orden.setSaldo(total.subtract(adelanto));

            OrdenServicio guardada = ordenRepository.save(orden);
            log.info("Orden servicio {} guardada. total={}, adelanto={}, saldo={}, edicion={}",
                    guardada.getId(), guardada.getTotal(), guardada.getACuenta(), guardada.getSaldo(), esEdicion);

            if (!esEdicion && adelanto.compareTo(BigDecimal.ZERO) > 0) {
                cajaService.registrarMovimiento(
                        "INGRESO",
                        "ADELANTO SERV #" + guardada.getId(),
                        adelanto,
                        CategoriaMovimiento.VENTA
                );
            }

            return ResponseEntity.ok(Map.of(
                    "message", esEdicion ? "Orden actualizada" : "Orden registrada",
                    "id", guardada.getId(),
                    "total", guardada.getTotal(),
                    "abonado", guardada.getACuenta(),
                    "saldo", guardada.getSaldo()
            ));
        } catch (Exception e) {
            log.error("Error al guardar orden de servicio", e);
            return ResponseEntity.badRequest().body("Error al procesar la orden. Por favor intente nuevamente.");
        }
    }

    @PostMapping("/api/finalizar/{id}")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_EDITAR')")
    @Transactional
    public ResponseEntity<?> finalizarOrden(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean cobrarSaldo) {
        try {
            return ordenRepository.findById(id).map(orden -> {
                try {
                    if (cobrarSaldo && valorMonetario(orden.getSaldo()).compareTo(BigDecimal.ZERO) > 0) {
                        cajaService.registrarMovimiento(
                                "INGRESO",
                                "SALDO FINAL SERV #" + orden.getId(),
                                orden.getSaldo(),
                                CategoriaMovimiento.VENTA
                        );
                        orden.setACuenta(valorMonetario(orden.getTotal()));
                        orden.setSaldo(BigDecimal.ZERO);
                    }
                    orden.setEstado("ENTREGADO");
                    ordenRepository.save(orden);
                    return ResponseEntity.ok("Orden finalizada");
                } catch (Exception e) {
                    log.error("Error al finalizar orden", e);
                    return ResponseEntity.badRequest().body("Error al finalizar: " + e.getMessage());
                }
            }).orElse(ResponseEntity.badRequest().body("Orden no encontrada"));
        } catch (Exception e) {
            log.error("Error al procesar finalizacion de orden", e);
            return ResponseEntity.badRequest().body("Error al procesar la solicitud");
        }
    }

    @GetMapping("/pdf/{id}")
    public void descargarPdf(@PathVariable Long id, HttpServletResponse response) throws IOException, DocumentException {
        OrdenServicio orden = ordenRepository.findById(id).orElse(null);
        if (orden == null) {
            response.sendError(404, "Orden no encontrada");
            return;
        }

        Configuracion config = configuracionService.obtenerConfiguracion();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=Contrato_" + id + ".pdf");

        // Documento con márgenes reducidos
        Document document = new Document(PageSize.A4, 25, 25, 25, 25);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        // COLORES CHROMA BLUE
        Color colorPrimario = parseColor(config.getColorPrimario(), new Color(7, 77, 140));
        Color colorBanner = new Color(0, 51, 102); // Azul más profundo para contraste
        Color colorZebra = new Color(225, 240, 255); // Celeste muy claro para Zebra
        Color colorWhite = Color.WHITE;
        
        Font fontCompany = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, colorPrimario);
        Font fontContractTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, colorWhite);
        Font fontLabelBlue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, colorPrimario);
        Font fontValue = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font fontSmallBlue = FontFactory.getFont(FontFactory.HELVETICA, 8, colorPrimario);
        Font fontHeaderTable = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, colorWhite);
        Font fontTerms = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(100, 100, 100));
        
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter fmtDateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        
        BigDecimal totalOrden = valorMonetario(orden.getTotal());
        BigDecimal saldoPendiente = resolverSaldoOrden(orden);
        BigDecimal abonado = totalOrden.subtract(saldoPendiente);
        if (abonado.compareTo(BigDecimal.ZERO) < 0) abonado = BigDecimal.ZERO;

        // BANNER CABECERA (TÍTULO CONTRACTUAL)
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        PdfPCell bannerCell = new PdfPCell(new Phrase("CONTRATO DE SERVICIO - PROYECTO CONFIRMADO", fontContractTitle));
        bannerCell.setBackgroundColor(colorBanner);
        bannerCell.setPadding(10f);
        bannerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        bannerCell.setBorder(Rectangle.NO_BORDER);
        banner.addCell(bannerCell);
        document.add(banner);
        document.add(new Paragraph(" ", fontSmallBlue));

        // CABECERA EMPRESA
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{1.5f, 3.5f});
        
        headerTable.addCell(crearCeldaLogo(config));
        
        PdfPCell infoEmpresa = new PdfPCell();
        infoEmpresa.setBorder(Rectangle.NO_BORDER);
        infoEmpresa.setPaddingLeft(15f);
        infoEmpresa.addElement(new Paragraph("CHROMA MULTISERVICIOS", fontCompany));
        infoEmpresa.addElement(new Paragraph("Soluciones Profesionales | RUC: " + valor(config.getRuc(), "20000000001"), fontSmallBlue));
        infoEmpresa.addElement(new Paragraph("OS Ref: - " + String.format("%06d", orden.getId()), fontLabelBlue));
        headerTable.addCell(infoEmpresa);
        document.add(headerTable);
        
        document.add(new Paragraph(" ", fontSmallBlue));

        // BLOQUE DE DATOS AZUL (CLIENTE Y PROYECTO)
        PdfPTable mainData = new PdfPTable(2);
        mainData.setWidthPercentage(100);
        mainData.setWidths(new float[]{1f, 1f});

        // Lado Cliente
        PdfPCell leftCol = new PdfPCell();
        leftCol.setBorderColor(colorZebra);
        leftCol.setBorderWidth(1f);
        leftCol.setPadding(12f);
        agregarSeccionInfo(leftCol, "CLIENTE / CONTRATANTE", colorPrimario, fontHeaderTable);
        agregarDato(leftCol, "TITULAR:", valor(orden.getClienteNombre(), "-"), fontLabelBlue, fontValue);
        agregarDato(leftCol, "IDENTIFICACIÓN:", valor(orden.getClienteDocumento(), "-"), fontLabelBlue, fontValue);
        agregarDato(leftCol, "UBICACIÓN:", valor(orden.getClienteDireccion(), "-"), fontLabelBlue, fontValue);
        mainData.addCell(leftCol);

        // Lado Proyecto
        PdfPCell rightCol = new PdfPCell();
        rightCol.setBorderColor(colorZebra);
        rightCol.setBorderWidth(1f);
        rightCol.setPadding(12f);
        agregarSeccionInfo(rightCol, "ALCANCE DEL PROYECTO", colorPrimario, fontHeaderTable);
        agregarDato(rightCol, "TRABAJO:", valor(orden.getTituloTrabajo(), "-").toUpperCase(), fontLabelBlue, fontValue);
        agregarDato(rightCol, "FECHA INICIO:", (orden.getFechaRecepcion() != null ? orden.getFechaRecepcion().format(fmtDateTime) : "-"), fontLabelBlue, fontValue);
        agregarDato(rightCol, "FECHA ENTREGA:", formatearFecha(orden.getFechaEntregaEstimada(), fmt), fontLabelBlue, fontValue);
        mainData.addCell(rightCol);

        document.add(mainData);
        document.add(new Paragraph(" ", fontSmallBlue));

        // DETALLE DE REQUERIMIENTOS (ZEBRA BLUE)
        PdfPTable tableItems = new PdfPTable(3);
        tableItems.setWidthPercentage(100);
        tableItems.setWidths(new float[]{0.5f, 4.5f, 1.2f});
        
        String[] headers = {"#", "REQUERIMIENTO TÉCNICO / SERVICIO", "PRECIO ACORDADO"};
        for(String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, fontHeaderTable));
            c.setBackgroundColor(colorPrimario);
            c.setPadding(8f);
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(h.contains("PRECIO") ? Element.ALIGN_RIGHT : (h.equals("#") ? Element.ALIGN_CENTER : Element.ALIGN_LEFT));
            tableItems.addCell(c);
        }

        int i = 1;
        for (OrdenItem item : orden.getItems()) {
            Color rowColor = (i % 2 == 0) ? colorZebra : colorWhite;
            tableItems.addCell(celdaModerna(String.valueOf(i++), fontValue, Element.ALIGN_CENTER, rowColor));
            tableItems.addCell(celdaModerna(valor(item.getDescripcion(), "-"), fontValue, Element.ALIGN_LEFT, rowColor));
            tableItems.addCell(celdaModerna("S/ " + formatMoney(item.getCosto()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, colorPrimario), Element.ALIGN_RIGHT, rowColor));
        }
        document.add(tableItems);

        document.add(new Paragraph(" ", fontSmallBlue));

        // TÉRMINOS Y TOTALES
        PdfPTable finalBlock = new PdfPTable(2);
        finalBlock.setWidthPercentage(100);
        finalBlock.setWidths(new float[]{2.3f, 1.7f});

        // Wording Contractual
        PdfPCell termsCell = new PdfPCell();
        termsCell.setBorder(Rectangle.NO_BORDER);
        termsCell.setPaddingRight(20f);
        
        Paragraph termTitle = new Paragraph("ACUERDO DE CONFORMIDAD Y SERVICIO:", fontLabelBlue);
        termTitle.setSpacingAfter(5f);
        termsCell.addElement(termTitle);
        
        termsCell.addElement(new Paragraph("1. El presente documento confirma la recepción y el inicio de los trabajos detallados.\n" +
                "2. El cliente se compromete a cancelar el saldo pendiente el día de la entrega final del proyecto.\n" +
                "3. Cualquier modificación en el alcance puede variar el presupuesto final.\n" +
                "4. CHROMA MULTISERVICIOS garantiza la calidad y compromiso en los tiempos establecidos.", fontTerms));
        
        if (orden.getObservaciones() != null && !orden.getObservaciones().isBlank()) {
            termsCell.addElement(new Paragraph("\nNOTAS TÉCNICAS:", fontLabelBlue));
            termsCell.addElement(new Paragraph(orden.getObservaciones(), fontTerms));
        }
        finalBlock.addCell(termsCell);

        // Totales Azulados
        PdfPCell totalsCell = new PdfPCell();
        totalsCell.setBorder(Rectangle.NO_BORDER);
        PdfPTable subtotTable = new PdfPTable(2);
        subtotTable.setWidthPercentage(100);
        
        addTotalRow(subtotTable, "PRECIO TOTAL PROYECTO:", "S/ " + formatMoney(totalOrden), fontLabelBlue, fontValue, false);
        addTotalRow(subtotTable, "ANTICIPO / ADELANTO:", "S/ " + formatMoney(abonado), fontLabelBlue, fontValue, false);
        
        PdfPCell labelSaldo = new PdfPCell(new Phrase("NETO A CANCELAR:", fontHeaderTable));
        labelSaldo.setBackgroundColor(colorPrimario);
        labelSaldo.setPadding(8f);
        labelSaldo.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelSaldo.setBorder(Rectangle.NO_BORDER);
        subtotTable.addCell(labelSaldo);

        PdfPCell valSaldo = new PdfPCell(new Phrase("S/ " + formatMoney(saldoPendiente), fontHeaderTable));
        valSaldo.setBackgroundColor(colorPrimario);
        valSaldo.setPadding(8f);
        valSaldo.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valSaldo.setBorder(Rectangle.NO_BORDER);
        subtotTable.addCell(valSaldo);
        
        totalsCell.addElement(subtotTable);
        finalBlock.addCell(totalsCell);
        document.add(finalBlock);

        // PIE DE PÁGINA PROFESIONAL
        document.add(new Paragraph("\n\n", fontSmallBlue));
        Paragraph finalMsg = new Paragraph("PROYECTO CONFIRMADO DIGITALMENTE - CHROMA MULTISERVICIOS\n" +
                "WhatsApp: " + valor(config.getTelefono(), "902843481") + " | " + valor(config.getDireccion(), "Calle Real 123"), fontSmallBlue);
        finalMsg.setAlignment(Element.ALIGN_CENTER);
        document.add(finalMsg);

        document.close();
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_EDITAR')")
    public String editarOrden(@PathVariable Long id, Model model) {
        OrdenServicio orden = ordenRepository.findById(id).orElse(null);
        if (orden == null) {
            return "redirect:/ordenes/lista";
        }

        model.addAttribute("ordenEdicion", orden);
        model.addAttribute("tipos", ordenRepository.findTiposServicio());
        return "ordenes/formulario";
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
                log.warn("No se pudo cargar el logo para orden PDF", e);
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

    private void agregarHeader(PdfPTable table, String[] textos, Font font) {
        for (String texto : textos) {
            PdfPCell cell = new PdfPCell(new Phrase(texto, font));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBackgroundColor(new Color(45, 45, 45));
            cell.setPadding(6f);
            table.addCell(cell);
        }
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

    private void addTotalRow(PdfPTable table, String label, String value, Font fLabel, Font fValue, boolean highlight) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, fLabel));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setPadding(6f);
        lCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if(highlight) lCell.setBackgroundColor(new Color(245, 245, 245));
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value, fValue));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setPadding(6f);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if(highlight) vCell.setBackgroundColor(new Color(245, 245, 245));
        table.addCell(vCell);
    }

    private Paragraph parrafoCentrado(String texto, Font font) {
        Paragraph paragraph = new Paragraph(texto, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
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

    private BigDecimal resolverSaldoOrden(OrdenServicio orden) {
        BigDecimal total = valorMonetario(orden.getTotal());
        BigDecimal saldo = orden.getSaldo();
        if (saldo == null) {
            BigDecimal adelanto = valorMonetario(orden.getACuenta());
            saldo = total.subtract(adelanto);
        }
        if (saldo.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (saldo.compareTo(total) > 0) {
            return total;
        }
        return saldo;
    }

    private BigDecimal resolverAbonadoOrden(OrdenServicio orden) {
        BigDecimal abonado = valorMonetario(orden.getACuenta());
        if (abonado.compareTo(BigDecimal.ZERO) > 0) {
            return abonado;
        }
        BigDecimal total = valorMonetario(orden.getTotal());
        BigDecimal saldo = resolverSaldoOrden(orden);
        BigDecimal calculado = total.subtract(saldo);
        return calculado.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : calculado;
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
