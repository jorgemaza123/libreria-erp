package com.libreria.sistema.controller;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.OrdenItem;
import com.libreria.sistema.model.OrdenServicio;
import com.libreria.sistema.model.ServicioCategoria;
import com.libreria.sistema.model.dto.OrdenDTO;
import com.libreria.sistema.model.dto.OrdenServicioPagoDTO;
import com.libreria.sistema.model.dto.ServicioCategoriaDTO;
import com.libreria.sistema.repository.OrdenServicioRepository;
import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.service.OrdenServicioVentaService;
import com.libreria.sistema.service.ServicioCategoriaService;
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
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ordenes")
@Slf4j
@PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_VER')")
public class OrdenServicioController {

    private final OrdenServicioRepository ordenRepository;
    private final ConfiguracionService configuracionService;
    private final OrdenServicioVentaService ordenServicioVentaService;
    private final ServicioCategoriaService servicioCategoriaService;

    public OrdenServicioController(OrdenServicioRepository ordenRepository,
                                   ConfiguracionService configuracionService,
                                   OrdenServicioVentaService ordenServicioVentaService,
                                   ServicioCategoriaService servicioCategoriaService) {
        this.ordenRepository = ordenRepository;
        this.configuracionService = configuracionService;
        this.ordenServicioVentaService = ordenServicioVentaService;
        this.servicioCategoriaService = servicioCategoriaService;
    }

    @GetMapping
    public String index() {
        return "redirect:/ordenes/lista";
    }

    @GetMapping("/nueva")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_CREAR')")
    public String nuevaOrden(Model model) {
        cargarTiposTrabajo(model);
        return "ordenes/formulario";
    }

    @GetMapping("/lista")
    public String listaOrdenes(Model model) {
        List<OrdenServicio> ordenes = ordenRepository.findAllOrdenadasPorMasReciente();
        LocalDate hoy = LocalDate.now();

        long pendientes = ordenes.stream().filter(this::esTrabajoActivo).count();
        long listos = ordenes.stream().filter(o -> "LISTO".equalsIgnoreCase(valor(o.getEstado(), ""))).count();
        long vencidos = ordenes.stream()
                .filter(this::esTrabajoActivo)
                .filter(o -> o.getFechaEntregaEstimada() != null && o.getFechaEntregaEstimada().isBefore(hoy))
                .count();
        long recordatorios = ordenes.stream()
                .filter(o -> esRecordatorioPendiente(o, hoy))
                .count();
        BigDecimal saldoPendiente = ordenes.stream()
                .filter(this::esTrabajoActivo)
                .map(o -> valorMonetario(o.getSaldo()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("ordenes", ordenes);
        cargarTiposTrabajo(model);
        model.addAttribute("hoy", hoy);
        model.addAttribute("totalPendientes", pendientes);
        model.addAttribute("totalListos", listos);
        model.addAttribute("totalVencidos", vencidos);
        model.addAttribute("totalRecordatorios", recordatorios);
        model.addAttribute("saldoPendiente", saldoPendiente);
        return "ordenes/lista";
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_EDITAR')")
    public String editarOrden(@PathVariable Long id, Model model) {
        OrdenServicio orden = ordenRepository.findDetalleCompletoById(id).orElse(null);
        if (orden == null) {
            return "redirect:/ordenes/lista";
        }

        model.addAttribute("ordenEdicion", orden);
        cargarTiposTrabajo(model);
        return "ordenes/formulario";
    }

    @GetMapping("/api/tipos")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_VER')")
    public ResponseEntity<?> listarTipos() {
        return ResponseEntity.ok(servicioCategoriaService.listarTodasOrdenadas().stream()
                .map(this::categoriaPayload)
                .toList());
    }

    @PostMapping("/api/tipos")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_EDITAR')")
    @Transactional
    public ResponseEntity<?> guardarTipo(@RequestBody ServicioCategoriaDTO dto) {
        try {
            ServicioCategoria categoria = servicioCategoriaService.guardarTipo(dto);
            return ResponseEntity.ok(categoriaPayload(categoria));
        } catch (Exception e) {
            log.error("Error al guardar tipo de trabajo", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/tipos/{id}/activo")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_EDITAR')")
    @Transactional
    public ResponseEntity<?> cambiarActivoTipo(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        try {
            boolean activo = Boolean.TRUE.equals(body.get("activo"));
            ServicioCategoria categoria = servicioCategoriaService.cambiarActivo(id, activo);
            return ResponseEntity.ok(categoriaPayload(categoria));
        } catch (Exception e) {
            log.error("Error al cambiar visibilidad del tipo de trabajo {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/guardar")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_CREAR') or hasPermission(null, 'ORDENES_SERVICIO_EDITAR')")
    @Transactional
    public ResponseEntity<?> guardarOrden(@RequestBody OrdenDTO dto) {
        try {
            boolean esEdicion = dto.getId() != null;
            OrdenServicio guardada = ordenServicioVentaService.guardarOrden(dto);
            return ResponseEntity.ok(Map.of(
                    "message", esEdicion ? "Orden actualizada" : "Orden registrada",
                    "id", guardada.getId(),
                    "estado", guardada.getEstado(),
                    "prioridad", guardada.getPrioridad(),
                    "total", valorMonetario(guardada.getTotal()),
                    "abonado", valorMonetario(guardada.getACuenta()),
                    "saldo", valorMonetario(guardada.getSaldo())
            ));
        } catch (Exception e) {
            log.error("Error al guardar orden de servicio", e);
            return ResponseEntity.badRequest().body("Error al procesar la orden: " + e.getMessage());
        }
    }

    @PostMapping("/api/estado/{id}")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_EDITAR')")
    @Transactional
    public ResponseEntity<?> actualizarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            OrdenServicio orden = ordenServicioVentaService.actualizarEstadoTrabajo(id, body.get("estado"));
            return ResponseEntity.ok(Map.of(
                    "message", "Estado actualizado",
                    "id", orden.getId(),
                    "estado", orden.getEstado()
            ));
        } catch (Exception e) {
            log.error("Error al actualizar estado de orden {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/prioridad/{id}")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_EDITAR')")
    @Transactional
    public ResponseEntity<?> actualizarPrioridad(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            OrdenServicio orden = ordenServicioVentaService.actualizarPrioridadTrabajo(id, body.get("prioridad"));
            return ResponseEntity.ok(Map.of(
                    "message", "Prioridad actualizada",
                    "id", orden.getId(),
                    "prioridad", orden.getPrioridad()
            ));
        } catch (Exception e) {
            log.error("Error al actualizar prioridad de orden {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/abonos/{id}")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_EDITAR')")
    @Transactional
    public ResponseEntity<?> registrarAbono(@PathVariable Long id, @RequestBody OrdenServicioPagoDTO dto) {
        try {
            var pago = ordenServicioVentaService.registrarAbono(id, dto);
            OrdenServicio orden = ordenRepository.findDetalleCompletoById(id)
                    .orElseThrow(() -> new RuntimeException("Orden no encontrada"));
            return ResponseEntity.ok(Map.of(
                    "message", "Abono registrado correctamente",
                    "pagoId", pago.getId(),
                    "monto", valorMonetario(pago.getMonto()),
                    "abonado", valorMonetario(orden.getACuenta()),
                    "saldo", valorMonetario(orden.getSaldo())
            ));
        } catch (Exception e) {
            log.error("Error al registrar abono de orden {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/finalizar/{id}")
    @PreAuthorize("hasPermission(null, 'ORDENES_SERVICIO_EDITAR')")
    @Transactional
    public ResponseEntity<?> finalizarOrden(@PathVariable Long id,
                                            @RequestParam(defaultValue = "false") boolean cobrarSaldo,
                                            @RequestParam(defaultValue = "EFECTIVO") String metodoPagoSaldo) {
        try {
            return ResponseEntity.ok(ordenServicioVentaService.finalizarOrden(id, cobrarSaldo, metodoPagoSaldo));
        } catch (Exception e) {
            log.error("Error al procesar finalizacion de orden {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pdf/{id}")
    public void descargarPdf(@PathVariable Long id, HttpServletResponse response) throws IOException, DocumentException {
        OrdenServicio orden = ordenRepository.findDetalleCompletoById(id).orElse(null);
        if (orden == null) {
            response.sendError(404, "Orden no encontrada");
            return;
        }

        Configuracion config = configuracionService.obtenerConfiguracion();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=Contrato_" + id + ".pdf");

        Document document = new Document(PageSize.A4, 25, 25, 25, 25);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        Color colorPrimario = parseColor(config.getColorPrimario(), new Color(7, 77, 140));
        Color colorBanner = new Color(0, 51, 102);
        Color colorZebra = new Color(225, 240, 255);
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
        BigDecimal abonado = resolverAbonadoOrden(orden);

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

        PdfPTable mainData = new PdfPTable(2);
        mainData.setWidthPercentage(100);
        mainData.setWidths(new float[]{1f, 1f});

        PdfPCell leftCol = new PdfPCell();
        leftCol.setBorderColor(colorZebra);
        leftCol.setBorderWidth(1f);
        leftCol.setPadding(12f);
        agregarSeccionInfo(leftCol, "CLIENTE / CONTRATANTE", colorPrimario, fontHeaderTable);
        agregarDato(leftCol, "TITULAR:", valor(orden.getClienteNombre(), "-"), fontLabelBlue, fontValue);
        agregarDato(leftCol, "IDENTIFICACION:", valor(orden.getClienteDocumento(), "-"), fontLabelBlue, fontValue);
        agregarDato(leftCol, "UBICACION:", valor(orden.getClienteDireccion(), "-"), fontLabelBlue, fontValue);
        mainData.addCell(leftCol);

        PdfPCell rightCol = new PdfPCell();
        rightCol.setBorderColor(colorZebra);
        rightCol.setBorderWidth(1f);
        rightCol.setPadding(12f);
        agregarSeccionInfo(rightCol, "ALCANCE DEL PROYECTO", colorPrimario, fontHeaderTable);
        agregarDato(rightCol, "TIPO:", nombreTipoOrden(orden), fontLabelBlue, fontValue);
        agregarDato(rightCol, "TRABAJO:", valor(orden.getTituloTrabajo(), "-").toUpperCase(), fontLabelBlue, fontValue);
        agregarDato(rightCol, "FECHA INICIO:", (orden.getFechaRecepcion() != null ? orden.getFechaRecepcion().format(fmtDateTime) : "-"), fontLabelBlue, fontValue);
        agregarDato(rightCol, "FECHA ENTREGA:", formatearFecha(orden.getFechaEntregaEstimada(), fmt), fontLabelBlue, fontValue);
        if (Boolean.TRUE.equals(orden.getRecordatorioActivo()) && orden.getFechaRecordatorio() != null) {
            agregarDato(rightCol, "RECORDATORIO:", orden.getFechaRecordatorio().format(fmt), fontLabelBlue, fontValue);
        }
        mainData.addCell(rightCol);

        document.add(mainData);
        document.add(new Paragraph(" ", fontSmallBlue));

        PdfPTable tableItems = new PdfPTable(3);
        tableItems.setWidthPercentage(100);
        tableItems.setWidths(new float[]{0.5f, 4.5f, 1.2f});

        String[] headers = {"#", "REQUERIMIENTO TECNICO / SERVICIO", "PRECIO ACORDADO"};
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, fontHeaderTable));
            c.setBackgroundColor(colorPrimario);
            c.setPadding(8f);
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(h.contains("PRECIO") ? Element.ALIGN_RIGHT : (h.equals("#") ? Element.ALIGN_CENTER : Element.ALIGN_LEFT));
            tableItems.addCell(c);
        }

        int index = 1;
        for (OrdenItem item : orden.getItems()) {
            Color rowColor = (index % 2 == 0) ? colorZebra : colorWhite;
            tableItems.addCell(celdaModerna(String.valueOf(index++), fontValue, Element.ALIGN_CENTER, rowColor));
            tableItems.addCell(celdaModerna(valor(item.getDescripcion(), "-"), fontValue, Element.ALIGN_LEFT, rowColor));
            tableItems.addCell(celdaModerna("S/ " + formatMoney(item.getCosto()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, colorPrimario), Element.ALIGN_RIGHT, rowColor));
        }
        document.add(tableItems);
        document.add(new Paragraph(" ", fontSmallBlue));

        PdfPTable finalBlock = new PdfPTable(2);
        finalBlock.setWidthPercentage(100);
        finalBlock.setWidths(new float[]{2.3f, 1.7f});

        PdfPCell termsCell = new PdfPCell();
        termsCell.setBorder(Rectangle.NO_BORDER);
        termsCell.setPaddingRight(20f);
        Paragraph termTitle = new Paragraph("ACUERDO DE CONFORMIDAD Y SERVICIO:", fontLabelBlue);
        termTitle.setSpacingAfter(5f);
        termsCell.addElement(termTitle);
        termsCell.addElement(new Paragraph(
                "1. El presente documento confirma la recepcion y el inicio de los trabajos detallados.\n" +
                        "2. El cliente se compromete a cancelar el saldo pendiente el dia de la entrega final del proyecto.\n" +
                        "3. Cualquier modificacion en el alcance puede variar el presupuesto final.\n" +
                        "4. CHROMA MULTISERVICIOS garantiza la calidad y compromiso en los tiempos establecidos.",
                fontTerms));

        if (orden.getObservaciones() != null && !orden.getObservaciones().isBlank()) {
            termsCell.addElement(new Paragraph("\nNOTAS TECNICAS:", fontLabelBlue));
            termsCell.addElement(new Paragraph(orden.getObservaciones(), fontTerms));
        }
        finalBlock.addCell(termsCell);

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

        document.add(new Paragraph("\n\n", fontSmallBlue));
        Paragraph finalMsg = new Paragraph("PROYECTO CONFIRMADO DIGITALMENTE - CHROMA MULTISERVICIOS\n" +
                "WhatsApp: " + valor(config.getTelefono(), "902843481") + " | " + valor(config.getDireccion(), "Calle Real 123"), fontSmallBlue);
        finalMsg.setAlignment(Element.ALIGN_CENTER);
        document.add(finalMsg);

        document.close();
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
        if (highlight) lCell.setBackgroundColor(new Color(245, 245, 245));
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value, fValue));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setPadding(6f);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (highlight) vCell.setBackgroundColor(new Color(245, 245, 245));
        table.addCell(vCell);
    }

    private String valor(String valor, String fallback) {
        return valor != null && !valor.isBlank() ? valor : fallback;
    }

    private boolean esTrabajoActivo(OrdenServicio orden) {
        String estado = valor(orden.getEstado(), "PENDIENTE");
        return !"ENTREGADO".equalsIgnoreCase(estado) && !"ANULADO".equalsIgnoreCase(estado);
    }

    private boolean esRecordatorioPendiente(OrdenServicio orden, LocalDate hoy) {
        return esTrabajoActivo(orden)
                && Boolean.TRUE.equals(orden.getRecordatorioActivo())
                && orden.getFechaRecordatorio() != null
                && !orden.getFechaRecordatorio().isAfter(hoy);
    }

    private void cargarTiposTrabajo(Model model) {
        List<String> tiposUsados = ordenRepository.findTiposServicio();
        model.addAttribute("categoriasServicio", servicioCategoriaService.listarActivas());
        model.addAttribute("tipoNombres", servicioCategoriaService.construirMapaNombres(tiposUsados));
        model.addAttribute("tipos", tiposUsados);
    }

    private Map<String, Object> categoriaPayload(ServicioCategoria categoria) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", categoria.getId());
        payload.put("codigo", categoria.getCodigo());
        payload.put("nombre", categoria.getNombre());
        payload.put("descripcion", categoria.getDescripcion());
        payload.put("icono", categoria.getIcono());
        payload.put("activa", Boolean.TRUE.equals(categoria.getActiva()));
        payload.put("orden", categoria.getOrden());
        return payload;
    }

    private String nombreTipoOrden(OrdenServicio orden) {
        if (orden == null || orden.getTipoServicio() == null) {
            return "Trabajo";
        }
        return servicioCategoriaService.construirMapaNombres(List.of(orden.getTipoServicio()))
                .getOrDefault(orden.getTipoServicio(), orden.getTipoServicio());
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
            BigDecimal abonado = resolverAbonadoOrden(orden);
            saldo = total.subtract(abonado);
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
        if (orden.getPagos() != null && !orden.getPagos().isEmpty()) {
            BigDecimal totalPagado = orden.getPagos().stream()
                    .map(p -> valorMonetario(p.getMonto()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalPagado.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : totalPagado;
        }
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
