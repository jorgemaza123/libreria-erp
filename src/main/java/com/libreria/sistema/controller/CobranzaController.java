package com.libreria.sistema.controller;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.Amortizacion;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.repository.AmortizacionRepository;
import com.libreria.sistema.repository.VentaRepository;
import com.libreria.sistema.service.CajaService;
import com.libreria.sistema.service.ClienteService;
import com.libreria.sistema.service.ConfiguracionService;
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
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/cobranzas")
@Slf4j
public class CobranzaController {

    private final VentaRepository ventaRepository;
    private final AmortizacionRepository amortizacionRepository;
    private final CajaService cajaService;
    private final ConfiguracionService configuracionService;
    private final ClienteService clienteService;

    public CobranzaController(VentaRepository ventaRepository, AmortizacionRepository amortizacionRepository,
            CajaService cajaService, ConfiguracionService configuracionService, ClienteService clienteService) {
        this.ventaRepository = ventaRepository;
        this.amortizacionRepository = amortizacionRepository;
        this.cajaService = cajaService;
        this.configuracionService = configuracionService;
        this.clienteService = clienteService;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'COBRANZAS_VER')")
    public String index(Model model) {
        cargarFiltrosBase(model, null, null, null);
        model.addAttribute("reporteClienteDisponible", false);
        return "cobranzas/index";
    }

    // U-5: acepta nombre O documento; M-1: incluye historial de amortizaciones por
    // deuda
    @GetMapping("/buscar")
    @PreAuthorize("hasPermission(null, 'COBRANZAS_VER')")
    public String buscarDeudas(@RequestParam String termino, Model model) {
        String terminoNormalizado = termino != null ? termino.trim() : "";
        List<Venta> deudas = ventaRepository.findDeudasPorTermino(terminoNormalizado);
        // M-1: construir mapa ventaId -> lista de pagos anteriores
        Map<Long, List<Amortizacion>> amortizacionesPorVenta = deudas.stream()
                .collect(Collectors.toMap(
                        Venta::getId,
                        amortizacionRepository::findByVentaOrderByFechaPagoDesc));
        cargarFiltrosBase(model, null, null, terminoNormalizado);
        configurarClienteReporte(model, deudas, terminoNormalizado);
        model.addAttribute("deudas", deudas);
        model.addAttribute("terminoBusqueda", terminoNormalizado);
        model.addAttribute("amortizacionesPorVenta", amortizacionesPorVenta);
        return "cobranzas/index";
    }

    @PostMapping("/pagar")
    @ResponseBody
    @Transactional
    @PreAuthorize("hasPermission(null, 'COBRANZAS_CREAR')")
    public ResponseEntity<?> registrarPago(@RequestParam Long ventaId,
            @RequestParam BigDecimal montoPago,
            @RequestParam(defaultValue = "EFECTIVO") String metodoPago) {
        try {
            Venta venta = ventaRepository.findById(ventaId)
                    .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

            // CRÍTICO-4 FIX: null-safe en saldoPendiente para evitar NullPointerException
            BigDecimal saldo = venta.getSaldoPendiente() != null ? venta.getSaldoPendiente() : BigDecimal.ZERO;
            BigDecimal pagado = venta.getMontoPagado() != null ? venta.getMontoPagado() : BigDecimal.ZERO;

            if (montoPago.compareTo(saldo) > 0) {
                return ResponseEntity.badRequest().body("El monto excede la deuda pendiente.");
            }

            Amortizacion pago = new Amortizacion();
            pago.setVenta(venta);
            pago.setMonto(montoPago);
            pago.setMetodoPago(metodoPago);
            pago.setObservacion("PAGO A CUENTA / CUOTA - " + metodoPago);
            Amortizacion pagoGuardado = amortizacionRepository.save(pago);

            venta.setMontoPagado(pagado.add(montoPago));
            venta.setSaldoPendiente(saldo.subtract(montoPago));

            if (venta.getSaldoPendiente().compareTo(BigDecimal.ZERO) == 0) {
                venta.setEstado(Boolean.TRUE.equals(venta.getEntregaPendiente()) ? "LISTO_ENTREGA" : "PAGADO_TOTAL");
            }
            ventaRepository.save(venta);

            if (venta.getClienteEntity() != null && venta.getClienteEntity().getId() != null) {
                clienteService.recalcularSaldoDeudor(venta.getClienteEntity().getId());
            }

            // Registrar movimiento en caja - OBLIGATORIO: Si falla, debe abortar la
            // transacción (ahora con @Transactional)
            String concepto = Boolean.TRUE.equals(venta.getEntregaPendiente())
                    ? "CUOTA APARTADO " + venta.getSerie() + "-" + venta.getNumero() + " (" + metodoPago + ")"
                    : "COBRO CUOTA " + venta.getSerie() + "-" + venta.getNumero() + " (" + metodoPago + ")";
            cajaService.registrarMovimiento("INGRESO",
                    concepto, montoPago,
                    com.libreria.sistema.model.CategoriaMovimiento.COBRANZA);

            // Devolvemos el ID del pago para que el JS abra el ticket
            return ResponseEntity.ok(Map.of(
                    "id", pagoGuardado.getId(),
                    "saldoRestante", venta.getSaldoPendiente(),
                    "formatoImpresion", obtenerFormatoImpresionPredeterminado()));

        } catch (Exception e) {
            log.error("Error al registrar pago de cobranza", e);
            return ResponseEntity.badRequest().body("Error al procesar el pago. Por favor intente nuevamente.");
        }
    }

    @GetMapping("/imprimir/{idAmortizacion}")
    @PreAuthorize("hasPermission(null, 'COBRANZAS_VER')")
    public String imprimirPago(@PathVariable Long idAmortizacion, Model model) {
        cargarModeloComprobante(idAmortizacion, model);
        return "A4".equalsIgnoreCase(obtenerFormatoImpresionPredeterminado())
                ? "cobranzas/impresion_a4"
                : "cobranzas/ticket_pago";
    }

    @GetMapping("/ticket/{idAmortizacion}")
    @PreAuthorize("hasPermission(null, 'COBRANZAS_VER')")
    public String ticketPago(@PathVariable Long idAmortizacion, Model model) {
        cargarModeloComprobante(idAmortizacion, model);
        return "cobranzas/ticket_pago";
    }

    @GetMapping("/impresion-a4/{idAmortizacion}")
    @PreAuthorize("hasPermission(null, 'COBRANZAS_VER')")
    public String impresionA4Pago(@PathVariable Long idAmortizacion, Model model) {
        cargarModeloComprobante(idAmortizacion, model);
        return "cobranzas/impresion_a4";
    }

    @GetMapping("/pdf-pagos")
    @PreAuthorize("hasPermission(null, 'COBRANZAS_VER')")
    public void descargarPdfPagos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) String termino,
            @RequestParam(required = false) String clienteDocumento,
            HttpServletResponse response) {
        try {
            LocalDate inicio = fechaInicio != null ? fechaInicio : LocalDate.now().withDayOfMonth(1);
            LocalDate fin = fechaFin != null ? fechaFin : LocalDate.now();
            String terminoNormalizado = termino != null ? termino.trim() : "";
            String documentoNormalizado = clienteDocumento != null ? clienteDocumento.trim() : "";

            if (inicio.isAfter(fin)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "La fecha inicial no puede ser mayor que la fecha final.");
                return;
            }

            if (documentoNormalizado.isBlank() && terminoNormalizado.isBlank()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Primero busque un cliente para generar su reporte de pagos.");
                return;
            }

            Configuracion config = configuracionService.obtenerConfiguracion();
            LocalDateTime inicioRango = inicio.atStartOfDay();
            LocalDateTime finRango = fin.plusDays(1).atStartOfDay().minusNanos(1);
            List<Amortizacion> pagos;
            String referenciaCliente;

            if (!documentoNormalizado.isBlank()) {
                pagos = amortizacionRepository.findByFechaPagoBetweenAndClienteDocumentoWithVenta(
                        inicioRango, finRango, documentoNormalizado);
                referenciaCliente = "Documento: " + documentoNormalizado;
            } else {
                pagos = amortizacionRepository.findByFechaPagoBetweenAndClienteTerminoWithVenta(
                        inicioRango, finRango, terminoNormalizado);
                referenciaCliente = "Cliente buscado: " + terminoNormalizado;
            }

            BigDecimal totalCobrado = pagos.stream()
                    .map(Amortizacion::getMonto)
                    .filter(monto -> monto != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String nombreArchivo = "pagos-cobranzas-" + sanitizarNombreArchivo(
                    !documentoNormalizado.isBlank() ? documentoNormalizado : terminoNormalizado)
                    + "-" + inicio + "-a-" + fin + ".pdf";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=\"" + nombreArchivo + "\"");

            generarPdfPagos(response, config, pagos, inicio, fin, totalCobrado, referenciaCliente);
        } catch (Exception e) {
            log.error("Error al generar PDF de pagos de cobranza", e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "No se pudo generar el PDF de pagos.");
            } catch (IOException ioException) {
                log.error("No se pudo devolver el error HTTP del PDF de pagos", ioException);
            }
        }
    }

    private void cargarModeloComprobante(Long idAmortizacion, Model model) {
        Amortizacion pago = amortizacionRepository.findById(idAmortizacion)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));
        Venta venta = pago.getVenta();
        Configuracion config = configuracionService.obtenerConfiguracion();

        model.addAttribute("pago", pago);
        model.addAttribute("venta", venta);
        model.addAttribute("config", config);
        model.addAttribute("tipoCobranza", Boolean.TRUE.equals(venta.getEntregaPendiente()) ? "APARTADO" : "CREDITO");
    }

    private void cargarFiltrosBase(Model model, LocalDate fechaInicio, LocalDate fechaFin, String terminoBusqueda) {
        LocalDate inicio = fechaInicio != null ? fechaInicio : LocalDate.now().withDayOfMonth(1);
        LocalDate fin = fechaFin != null ? fechaFin : LocalDate.now();
        Configuracion config = configuracionService.obtenerConfiguracion();

        model.addAttribute("fechaInicioReporte", inicio);
        model.addAttribute("fechaFinReporte", fin);
        model.addAttribute("terminoBusqueda", terminoBusqueda);
        model.addAttribute("formatoImpresion", obtenerFormatoImpresionPredeterminado());
        model.addAttribute("simboloMoneda",
                config.getFormatoMoneda() != null && !config.getFormatoMoneda().isBlank() ? config.getFormatoMoneda()
                        : "S/");
    }

    private void configurarClienteReporte(Model model, List<Venta> deudas, String terminoBusqueda) {
        List<String> documentos = deudas.stream()
                .map(Venta::getClienteNumeroDocumento)
                .filter(doc -> doc != null && !doc.isBlank())
                .distinct()
                .limit(2)
                .toList();

        List<String> nombres = deudas.stream()
                .map(Venta::getClienteDenominacion)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .distinct()
                .limit(2)
                .toList();

        String documentoCliente = documentos.size() == 1 ? documentos.get(0) : null;
        String nombreCliente = nombres.size() == 1 ? nombres.get(0) : null;

        boolean reporteDisponible = documentoCliente != null || (nombreCliente != null && documentos.size() <= 1);
        model.addAttribute("reporteClienteDisponible", reporteDisponible);
        model.addAttribute("clienteReporteDocumento", documentoCliente);
        model.addAttribute("clienteReporteNombre",
                nombreCliente != null ? nombreCliente : (terminoBusqueda != null ? terminoBusqueda : ""));
    }

    private String obtenerFormatoImpresionPredeterminado() {
        Configuracion config = configuracionService.obtenerConfiguracion();
        return "A4".equalsIgnoreCase(config.getFormatoImpresion()) ? "A4" : "TICKET";
    }

    private void generarPdfPagos(HttpServletResponse response,
            Configuracion config,
            List<Amortizacion> pagos,
            LocalDate inicio,
            LocalDate fin,
            BigDecimal totalCobrado,
            String referenciaCliente) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4.rotate(), 28, 28, 24, 24);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        Color colorPrimario = parseColor(config.getColorPrimario(), new Color(23, 96, 171));
        Color colorCabecera = parseColor(config.getColorOscuro(), new Color(52, 58, 64));
        String moneda = config.getFormatoMoneda() != null && !config.getFormatoMoneda().isBlank()
                ? config.getFormatoMoneda() + " "
                : "S/ ";
        DateTimeFormatter fechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        DateTimeFormatter fechaCorta = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[] { 3.5f, 2f });

        PdfPCell cellEmpresa = new PdfPCell();
        cellEmpresa.setBorder(Rectangle.NO_BORDER);
        cellEmpresa.setPadding(0);
        cellEmpresa.addElement(new Paragraph(
                valorOpcion(config.getNombreEmpresa(), "EMPRESA"),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, colorPrimario)));
        cellEmpresa.addElement(new Paragraph("RUC: " + valorOpcion(config.getRuc(), "-"),
                FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cellEmpresa.addElement(new Paragraph(valorOpcion(config.getDireccion(), "-"),
                FontFactory.getFont(FontFactory.HELVETICA, 9)));
        if (config.getTelefono() != null && !config.getTelefono().isBlank()) {
            cellEmpresa.addElement(new Paragraph("Telefono: " + config.getTelefono(),
                    FontFactory.getFont(FontFactory.HELVETICA, 9)));
        }
        header.addCell(cellEmpresa);

        PdfPCell cellReporte = new PdfPCell();
        cellReporte.setBorderColor(colorPrimario);
        cellReporte.setBorderWidth(1.5f);
        cellReporte.setPadding(10);
        cellReporte.addElement(new Paragraph("REPORTE DE PAGOS",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, colorPrimario)));
        cellReporte.addElement(new Paragraph("Cuentas por cobrar",
                FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cellReporte.addElement(new Paragraph(referenciaCliente,
                FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cellReporte.addElement(new Paragraph("Periodo: " + inicio.format(fechaCorta) + " al " + fin.format(fechaCorta),
                FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cellReporte.addElement(new Paragraph("Generado: " + LocalDateTime.now().format(fechaHora),
                FontFactory.getFont(FontFactory.HELVETICA, 9)));
        header.addCell(cellReporte);
        document.add(header);

        document.add(new Paragraph(" "));

        PdfPTable resumen = new PdfPTable(3);
        resumen.setWidthPercentage(100);
        resumen.setWidths(new float[] { 3f, 1.4f, 1.6f });
        agregarCabeceraResumen(resumen, colorCabecera, "Periodo Analizado", "Cantidad de Pagos", "Total Cobrado");
        resumen.addCell(crearCeldaResumen(inicio.format(fechaCorta) + " al " + fin.format(fechaCorta), Element.ALIGN_LEFT));
        resumen.addCell(crearCeldaResumen(String.valueOf(pagos.size()), Element.ALIGN_CENTER));
        resumen.addCell(crearCeldaResumen(moneda + formatearMonto(totalCobrado), Element.ALIGN_RIGHT));
        document.add(resumen);

        document.add(new Paragraph(" "));

        if (pagos.isEmpty()) {
            Paragraph vacio = new Paragraph("No se registraron pagos en el rango solicitado.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, Color.DARK_GRAY));
            vacio.setAlignment(Element.ALIGN_CENTER);
            document.add(vacio);
            document.close();
            return;
        }

        PdfPTable tabla = new PdfPTable(7);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[] { 1.7f, 2.2f, 2.9f, 1.7f, 1.9f, 1.4f, 1.6f });
        agregarCabeceraTabla(tabla, colorCabecera,
                "Fecha", "Cliente", "Venta", "Tipo", "Metodo", "Operacion", "Monto");

        for (Amortizacion pago : pagos) {
            Venta venta = pago.getVenta();
            String referenciaVenta = valorOpcion(venta.getTipoComprobante(), "VENTA") + " "
                    + valorOpcion(venta.getSerie(), "-") + "-" + valorOpcion(venta.getNumero(), 0);
            String operacion = pago.getObservacion() != null && !pago.getObservacion().isBlank()
                    ? pago.getObservacion()
                    : "PAGO DE CUOTA";
            String tipoCobranza = Boolean.TRUE.equals(venta.getEntregaPendiente()) ? "APARTADO" : "CREDITO";

            tabla.addCell(crearCeldaDetalle(pago.getFechaPago() != null ? pago.getFechaPago().format(fechaHora) : "-", Element.ALIGN_LEFT));
            tabla.addCell(crearCeldaDetalle(valorOpcion(venta.getClienteDenominacion(), "CLIENTE VARIOS"), Element.ALIGN_LEFT));
            tabla.addCell(crearCeldaDetalle(referenciaVenta, Element.ALIGN_LEFT));
            tabla.addCell(crearCeldaDetalle(tipoCobranza, Element.ALIGN_CENTER));
            tabla.addCell(crearCeldaDetalle(valorOpcion(pago.getMetodoPago(), "EFECTIVO"), Element.ALIGN_CENTER));
            tabla.addCell(crearCeldaDetalle(operacion, Element.ALIGN_LEFT));
            tabla.addCell(crearCeldaDetalle(moneda + formatearMonto(pago.getMonto()), Element.ALIGN_RIGHT));
        }

        PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL COBRADO",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        totalLabel.setColspan(6);
        totalLabel.setPadding(6);
        totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.addCell(totalLabel);

        PdfPCell totalValue = new PdfPCell(new Phrase(moneda + formatearMonto(totalCobrado),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, colorPrimario)));
        totalValue.setPadding(6);
        totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.addCell(totalValue);

        document.add(tabla);

        if (config.getPiePaginaReportes() != null && !config.getPiePaginaReportes().isBlank()) {
            document.add(new Paragraph(" "));
            Paragraph pie = new Paragraph(config.getPiePaginaReportes(),
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY));
            pie.setAlignment(Element.ALIGN_CENTER);
            document.add(pie);
        }

        document.close();
    }

    private void agregarCabeceraResumen(PdfPTable tabla, Color colorCabecera, String... columnas) {
        for (String columna : columnas) {
            PdfPCell cell = new PdfPCell(new Phrase(columna,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
            cell.setPadding(6);
            cell.setBackgroundColor(colorCabecera);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabla.addCell(cell);
        }
    }

    private void agregarCabeceraTabla(PdfPTable tabla, Color colorCabecera, String... columnas) {
        agregarCabeceraResumen(tabla, colorCabecera, columnas);
    }

    private PdfPCell crearCeldaResumen(String texto, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cell.setPadding(7);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private PdfPCell crearCeldaDetalle(String texto, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA, 8)));
        cell.setPadding(5);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private String formatearMonto(BigDecimal monto) {
        BigDecimal valor = monto != null ? monto : BigDecimal.ZERO;
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String valorOpcion(String texto, String fallback) {
        return texto != null && !texto.isBlank() ? texto : fallback;
    }

    private int valorOpcion(Integer numero, int fallback) {
        return numero != null ? numero : fallback;
    }

    private String sanitizarNombreArchivo(String texto) {
        if (texto == null || texto.isBlank()) {
            return "cliente";
        }
        return texto.replaceAll("[^a-zA-Z0-9-_]", "_");
    }

    private Color parseColor(String hex, Color fallback) {
        try {
            return hex != null && !hex.isBlank() ? Color.decode(hex) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
