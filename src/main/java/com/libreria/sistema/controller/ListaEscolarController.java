package com.libreria.sistema.controller;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.*;
import com.libreria.sistema.repository.ColegioRepository;
import com.libreria.sistema.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

// Imports para PDF (OpenPDF) - imports específicos para evitar conflicto con java.util.List
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

import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controlador para el módulo de Listas Escolares.
 * Maneja tanto las vistas HTML como los endpoints REST.
 */
@Controller
@RequestMapping("/listas-escolares")
@RequiredArgsConstructor
@Slf4j
public class ListaEscolarController {

    private final ListaEscolarService listaService;
    private final MatcherProductosService matcherService;
    private final ProductoBusquedaService busquedaService;
    private final ColegioRepository colegioRepository;
    private final CajaService cajaService;
    private final ConfiguracionService configuracionService;

    // =========================================================
    //  VISTAS HTML
    // =========================================================

    /**
     * Página principal - Listado de listas escolares
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    public String index(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String colegio,
            @RequestParam(required = false) String grado,
            @RequestParam(required = false) Integer anio,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Page<ListaEscolar> listas = listaService.buscar(
            estado, colegio, grado, anio,
            PageRequest.of(page, size, Sort.by("fechaCreacion").descending())
        );

        model.addAttribute("listas", listas);
        model.addAttribute("estadoFiltro", estado);
        model.addAttribute("colegioFiltro", colegio);
        model.addAttribute("gradoFiltro", grado);
        model.addAttribute("anioFiltro", anio);
        model.addAttribute("estadisticas", listaService.obtenerEstadisticas(anio));

        return "lista-escolar/index";
    }

    /**
     * Formulario para nueva lista
     */
    @GetMapping("/nueva")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    public String nueva(Model model) {
        model.addAttribute("lista", new ListaEscolarDTO());
        model.addAttribute("colegios", colegioRepository.findByActivoTrueOrderByNombreAsc());
        model.addAttribute("grados", getGradosDisponibles());
        return "lista-escolar/formulario";
    }

    /**
     * Ver/Editar una lista existente
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    public String ver(@PathVariable Long id, Model model) {
        ListaEscolar lista = listaService.obtenerConDetalles(id)
            .orElseThrow(() -> new RuntimeException("Lista no encontrada"));

        model.addAttribute("lista", lista);
        model.addAttribute("detallesVendibles", listaService.obtenerVendibles(id));
        model.addAttribute("detallesVendidos", listaService.obtenerVendidos(id));
        model.addAttribute("historialVentas", listaService.obtenerHistorialVentas(id));

        // Verificar si la caja está abierta
        try {
            cajaService.obtenerSesionActiva();
            model.addAttribute("cajaAbierta", true);
        } catch (Exception e) {
            model.addAttribute("cajaAbierta", false);
        }

        return "lista-escolar/detalle";
    }

    /**
     * Página de edición de cotización
     */
    @GetMapping("/{id}/editar")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    public String editar(@PathVariable Long id, Model model) {
        ListaEscolar lista = listaService.obtenerConDetalles(id)
            .orElseThrow(() -> new RuntimeException("Lista no encontrada"));

        if ("COMPLETADA".equals(lista.getEstado()) || "CANCELADA".equals(lista.getEstado())) {
            return "redirect:/listas-escolares/" + id;
        }

        model.addAttribute("lista", lista);
        return "lista-escolar/editar";
    }

    // =========================================================
    //  API REST - CRUD LISTA
    // =========================================================

    /**
     * Crear nueva lista (POST)
     */
    @PostMapping("/api/crear")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> crear(@Valid @RequestBody ListaEscolarDTO dto) {
        try {
            ListaEscolar lista = listaService.crearLista(dto);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "id", lista.getId(),
                "codigo", lista.getCodigoCompleto(),
                "itemsTotal", lista.getItemsTotal(),
                "mensaje", "Lista creada exitosamente"
            ));
        } catch (Exception e) {
            log.error("Error al crear lista: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Generar cotizaciones automáticas
     */
    @PostMapping("/api/{id}/cotizar")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> generarCotizaciones(@PathVariable Long id) {
        try {
            ListaEscolar lista = listaService.generarCotizaciones(id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "id", lista.getId(),
                "estado", lista.getEstado(),
                "totalEconomico", lista.getTotalEconomico(),
                "totalMedio", lista.getTotalMedio(),
                "totalPremium", lista.getTotalPremium(),
                "itemsCotizados", lista.getDetalles().stream()
                    .filter(d -> "COTIZADO".equals(d.getEstado())).count()
            ));
        } catch (Exception e) {
            log.error("Error al generar cotizaciones: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Obtener lista completa (JSON)
     */
    @GetMapping("/api/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return listaService.obtenerConDetalles(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Obtener detalles para POS modal
     */
    @GetMapping("/api/{id}/detalles-pos")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> obtenerDetallesPOS(@PathVariable Long id) {
        try {
            ListaEscolar lista = listaService.obtenerConDetalles(id)
                .orElseThrow(() -> new RuntimeException("Lista no encontrada"));

            List<DetalleListaEscolar> vendibles = listaService.obtenerVendibles(id);

            Map<String, Object> response = new HashMap<>();
            response.put("lista", Map.of(
                "id", lista.getId(),
                "codigo", lista.getCodigoCompleto(),
                "nombreAlumno", lista.getNombreAlumno(),
                "grado", lista.getGrado(),
                "colegio", lista.getColegio(),
                "contactoNombre", lista.getContactoNombre(),
                "contactoTelefono", lista.getContactoTelefono()
            ));

            response.put("items", vendibles.stream().map(d -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", d.getId());
                item.put("textoOriginal", d.getTextoOriginal());
                item.put("cantidad", d.getCantidadSolicitada());
                item.put("estado", d.getEstado());
                item.put("nivelSeleccionado", d.getNivelSeleccionado());
                item.put("precioFinal", d.getPrecioFinal());
                item.put("subtotal", d.getSubtotal());

                Producto p = d.getProductoFinal();
                if (p != null) {
                    item.put("productoId", p.getId());
                    item.put("productoNombre", p.getNombre());
                    item.put("productoStock", p.getStockActual());
                    item.put("tieneStock", d.tieneStockDisponible());
                }

                // Opciones de niveles
                item.put("precioEconomico", d.getPrecioEconomico());
                item.put("precioMedio", d.getPrecioMedio());
                item.put("precioPremium", d.getPrecioPremium());

                return item;
            }).toList());

            response.put("totales", Map.of(
                "economico", lista.getTotalEconomico(),
                "medio", lista.getTotalMedio(),
                "premium", lista.getTotalPremium()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    //  API REST - DETALLES
    // =========================================================

    /**
     * Actualizar un detalle
     */
    @PutMapping("/api/detalle/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> actualizarDetalle(
            @PathVariable Long id,
            @RequestBody DetalleListaEscolarDTO dto) {
        try {
            DetalleListaEscolar detalle = listaService.actualizarDetalle(id, dto);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "id", detalle.getId(),
                "estado", detalle.getEstado()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Seleccionar nivel masivo
     */
    @PostMapping("/api/{id}/seleccionar-nivel")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> seleccionarNivel(
            @PathVariable Long id,
            @RequestParam String nivel) {
        try {
            listaService.seleccionarNivelMasivo(id, nivel);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "nivel", nivel
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Buscar productos para asignar
     */
    @GetMapping("/api/buscar-productos")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> buscarProductos(@RequestParam String termino) {
        List<Map<String, Object>> productos = busquedaService.buscarParaSelect2(termino);
        return ResponseEntity.ok(productos);
    }

    /**
     * Buscar match automático para un texto
     */
    @GetMapping("/api/buscar-match")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> buscarMatch(
            @RequestParam String texto,
            @RequestParam(defaultValue = "1") int cantidad) {
        MatcherProductosService.ResultadoMatch match = matcherService.buscarMatch(texto, cantidad);
        return ResponseEntity.ok(match);
    }

    /**
     * Obtener detalles para edición por niveles (JSON estructurado)
     */
    @GetMapping("/api/{id}/detalles-edicion")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> obtenerDetallesEdicion(@PathVariable Long id) {
        try {
            ListaEscolar lista = listaService.obtenerConDetalles(id)
                .orElseThrow(() -> new RuntimeException("Lista no encontrada"));

            Map<String, Object> response = new HashMap<>();
            response.put("id", lista.getId());
            response.put("codigo", lista.getCodigoCompleto());
            response.put("nombreAlumno", lista.getNombreAlumno());
            response.put("grado", lista.getGrado());
            response.put("colegio", lista.getColegio() != null ? lista.getColegio() : "");
            response.put("totalEconomico", lista.getTotalEconomico());
            response.put("totalMedio", lista.getTotalMedio());
            response.put("totalPremium", lista.getTotalPremium());

            // Mapear detalles con toda la información por nivel
            response.put("detalles", lista.getDetalles().stream().map(d -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", d.getId());
                item.put("orden", d.getOrden());
                item.put("textoOriginal", d.getTextoOriginal());
                item.put("cantidad", d.getCantidadSolicitada());
                item.put("estado", d.getEstado());
                item.put("esRegalo", d.esItemRegalo());
                item.put("nivelRegalo", d.getNivelRegalo());

                // Nivel Económico
                item.put("productoEconomicoId", d.getProductoEconomico() != null ? d.getProductoEconomico().getId() : null);
                item.put("productoEconomicoNombre", d.getProductoEconomico() != null ? d.getProductoEconomico().getNombre() : null);
                item.put("precioEconomico", d.getPrecioEconomico());

                // Nivel Medio
                item.put("productoMedioId", d.getProductoMedio() != null ? d.getProductoMedio().getId() : null);
                item.put("productoMedioNombre", d.getProductoMedio() != null ? d.getProductoMedio().getNombre() : null);
                item.put("precioMedio", d.getPrecioMedio());

                // Nivel Premium
                item.put("productoPremiumId", d.getProductoPremium() != null ? d.getProductoPremium().getId() : null);
                item.put("productoPremiumNombre", d.getProductoPremium() != null ? d.getProductoPremium().getNombre() : null);
                item.put("precioPremium", d.getPrecioPremium());

                // Cotización de proveedor (con manejo seguro de nulos)
                item.put("cotizadoProveedorEconomico", Boolean.TRUE.equals(d.getCotizadoProveedorEconomico()));
                item.put("cotizadoProveedorMedio", Boolean.TRUE.equals(d.getCotizadoProveedorMedio()));
                item.put("cotizadoProveedorPremium", Boolean.TRUE.equals(d.getCotizadoProveedorPremium()));
                item.put("descripcionManualEconomico", d.getDescripcionManualEconomico() != null ? d.getDescripcionManualEconomico() : "");
                item.put("descripcionManualMedio", d.getDescripcionManualMedio() != null ? d.getDescripcionManualMedio() : "");
                item.put("descripcionManualPremium", d.getDescripcionManualPremium() != null ? d.getDescripcionManualPremium() : "");
                item.put("nombreProveedor", d.getNombreProveedor() != null ? d.getNombreProveedor() : "");
                item.put("productoComprado", Boolean.TRUE.equals(d.getProductoComprado()));

                return item;
            }).toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error al obtener detalles: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "Error desconocido");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Actualizar producto de un nivel específico
     */
    @PostMapping(value = "/api/detalle/{detalleId}/nivel/{nivel}", consumes = "application/json")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> actualizarProductoNivel(
            @PathVariable Long detalleId,
            @PathVariable String nivel,
            @RequestBody Map<String, Object> datos) {
        try {
            Long productoId = datos.get("productoId") != null && !datos.get("productoId").toString().isEmpty() ?
                Long.valueOf(datos.get("productoId").toString()) : null;

            DetalleListaEscolar detalle = listaService.actualizarProductoPorNivel(detalleId, nivel, productoId);

            // Construir respuesta con valores no nulos
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", detalle.getId());
            response.put("precioEconomico", detalle.getPrecioEconomico() != null ? detalle.getPrecioEconomico() : 0);
            response.put("precioMedio", detalle.getPrecioMedio() != null ? detalle.getPrecioMedio() : 0);
            response.put("precioPremium", detalle.getPrecioPremium() != null ? detalle.getPrecioPremium() : 0);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error al actualizar producto nivel: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Agregar regalo/oferta a un nivel
     */
    @PostMapping("/api/{listaId}/regalo")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> agregarRegalo(
            @PathVariable Long listaId,
            @RequestBody Map<String, String> datos) {
        try {
            String texto = datos.get("texto");
            String nivel = datos.get("nivel");

            if (texto == null || texto.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El texto del regalo es requerido"));
            }

            DetalleListaEscolar regalo = listaService.agregarRegalo(listaId, texto, nivel);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "id", regalo.getId(),
                "texto", regalo.getTextoOriginal(),
                "nivel", regalo.getNivelRegalo()
            ));
        } catch (Exception e) {
            log.error("Error al agregar regalo: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Eliminar regalo
     */
    @DeleteMapping("/api/regalo/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> eliminarRegalo(@PathVariable Long id) {
        try {
            listaService.eliminarRegalo(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Copiar productos de un nivel a otro
     */
    @PostMapping("/api/{listaId}/copiar-nivel")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> copiarNivel(
            @PathVariable Long listaId,
            @RequestBody Map<String, String> datos) {
        try {
            String origen = datos.get("origen");
            String destino = datos.get("destino");

            listaService.copiarNivel(listaId, origen, destino);

            return ResponseEntity.ok(Map.of("success", true, "mensaje",
                "Productos copiados de " + origen + " a " + destino));
        } catch (Exception e) {
            log.error("Error al copiar nivel: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================
    //  API REST - VENTAS
    // =========================================================

    /**
     * Procesar venta (parcial o total)
     */
    @PostMapping("/api/venta")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> procesarVenta(@Valid @RequestBody VentaListaEscolarDTO dto) {
        try {
            Map<String, Object> resultado = listaService.procesarVenta(dto);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            log.error("Error al procesar venta: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Marcar todos como pendientes
     */
    @PostMapping("/api/{id}/marcar-pendiente")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> marcarPendiente(@PathVariable Long id) {
        try {
            listaService.marcarTodoPendiente(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Cancelar lista
     */
    @PostMapping("/api/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> cancelar(@PathVariable Long id) {
        try {
            listaService.cancelarLista(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    // =========================================================
    //  API REST - COLEGIOS
    // =========================================================

    /**
     * Listar colegios para autocompletado
     */
    @GetMapping("/api/colegios")
    @ResponseBody
    public ResponseEntity<?> listarColegios(@RequestParam(required = false) String termino) {
        List<Colegio> colegios = termino != null && !termino.isBlank() ?
            colegioRepository.buscarPorNombre(termino) :
            colegioRepository.findByActivoTrueOrderByNombreAsc();
        return ResponseEntity.ok(colegios);
    }

    /**
     * Crear nuevo colegio
     */
    @PostMapping("/api/colegios")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> crearColegio(@RequestBody Colegio colegio) {
        try {
            if (colegioRepository.existsByNombre(colegio.getNombre())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "El colegio ya existe"
                ));
            }
            colegio.setActivo(true);
            Colegio saved = colegioRepository.save(colegio);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    //  PDF EXPORT
    // =========================================================

    /**
     * Genera PDF de cotización con los niveles seleccionados.
     * Separa productos COTIZADOS de NO DISPONIBLES/PENDIENTES.
     */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    public void generarPDF(
            @PathVariable Long id,
            @RequestParam(defaultValue = "ECONOMICO") String niveles,
            jakarta.servlet.http.HttpServletResponse response) {
        try {
            ListaEscolar lista = listaService.obtenerConDetalles(id)
                .orElseThrow(() -> new RuntimeException("Lista no encontrada"));

            Configuracion config = configuracionService.obtenerConfiguracion();

            // Parsear niveles seleccionados
            Set<String> nivelesSet = new HashSet<>(Arrays.asList(niveles.toUpperCase().split(",")));

            // Configurar respuesta HTTP
            response.setContentType("application/pdf");
            String filename = "Cotizacion_" + lista.getCodigoCompleto().replace("-", "_") + ".pdf";
            response.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");

            // Crear documento PDF
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, response.getOutputStream());
            document.open();

            // Agregar cabecera con datos de empresa
            agregarCabeceraPDF(document, config, lista);

            // Agregar datos del alumno
            agregarDatosAlumno(document, lista);

            // Agregar tablas por cada nivel seleccionado
            for (String nivel : List.of("ECONOMICO", "MEDIO", "PREMIUM")) {
                if (nivelesSet.contains(nivel)) {
                    agregarSeccionNivelMejorada(document, lista, nivel, config);
                }
            }

            // Leyenda de estados
            agregarLeyendaEstados(document);

            // Pie de página
            if (config.getPiePaginaReportes() != null && !config.getPiePaginaReportes().trim().isEmpty()) {
                document.add(new Paragraph(" "));
                Paragraph pie = new Paragraph(config.getPiePaginaReportes(),
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY));
                pie.setAlignment(Element.ALIGN_CENTER);
                document.add(pie);
            }

            document.close();

        } catch (Exception e) {
            log.error("Error al generar PDF: {}", e.getMessage(), e);
            try {
                response.sendError(500, "Error al generar PDF: " + e.getMessage());
            } catch (IOException ignored) {}
        }
    }

    /**
     * Nueva sección mejorada: Separa COTIZADOS de NO DISPONIBLES
     */
    private void agregarSeccionNivelMejorada(Document document, ListaEscolar lista, String nivel, Configuracion config) throws DocumentException {
        Color colorNivel = switch (nivel) {
            case "ECONOMICO" -> new Color(108, 117, 125);
            case "MEDIO" -> new Color(23, 162, 184);
            case "PREMIUM" -> new Color(255, 193, 7);
            default -> Color.GRAY;
        };

        String tituloNivel = switch (nivel) {
            case "ECONOMICO" -> "OPCIÓN ECONÓMICA";
            case "MEDIO" -> "OPCIÓN MEDIA";
            case "PREMIUM" -> "OPCIÓN PREMIUM";
            default -> nivel;
        };

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";

        // Separar items: COTIZADOS vs NO DISPONIBLES
        List<DetalleListaEscolar> itemsCotizados = new java.util.ArrayList<>();
        List<DetalleListaEscolar> itemsNoDisponibles = new java.util.ArrayList<>();

        for (DetalleListaEscolar detalle : lista.getDetalles()) {
            // Filtrar regalos de otros niveles
            if (detalle.esItemRegalo() && detalle.getNivelRegalo() != null && !detalle.getNivelRegalo().equals(nivel)) {
                continue;
            }

            BigDecimal precio = detalle.getPrecioPorNivel(nivel);
            boolean tieneProducto = detalle.getNombreProductoPorNivel(nivel) != null;
            boolean tienePrecio = precio != null && precio.compareTo(BigDecimal.ZERO) > 0;
            boolean esCotizacionProveedor = detalle.esCotizacionProveedorPorNivel(nivel);

            // Si es regalo, va a cotizados
            if (detalle.esItemRegalo()) {
                itemsCotizados.add(detalle);
            } else if (tieneProducto && tienePrecio) {
                // Producto de inventario con precio
                itemsCotizados.add(detalle);
            } else if (esCotizacionProveedor && tienePrecio) {
                // Cotización manual de proveedor con precio
                itemsCotizados.add(detalle);
            } else {
                itemsNoDisponibles.add(detalle);
            }
        }

        // === TITULO DEL NIVEL ===
        Paragraph pTitulo = new Paragraph(tituloNivel, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE));
        PdfPCell cellTitulo = new PdfPCell(pTitulo);
        cellTitulo.setBackgroundColor(colorNivel);
        cellTitulo.setPadding(8);
        cellTitulo.setHorizontalAlignment(Element.ALIGN_CENTER);

        // === SECCION 1: PRODUCTOS COTIZADOS ===
        if (!itemsCotizados.isEmpty()) {
            PdfPTable tableCotizados = new PdfPTable(6);
            tableCotizados.setWidthPercentage(100);
            tableCotizados.setWidths(new float[]{0.4f, 0.5f, 3.5f, 0.7f, 1.2f, 1.2f});
            tableCotizados.setSpacingBefore(10);

            // Titulo de sección
            cellTitulo.setColspan(6);
            tableCotizados.addCell(cellTitulo);

            // Cabecera
            agregarCabeceraTablaPDFMejorada(tableCotizados, colorNivel);

            BigDecimal totalCotizados = BigDecimal.ZERO;
            int num = 0;

            for (DetalleListaEscolar detalle : itemsCotizados) {
                num++;
                BigDecimal precio = detalle.getPrecioPorNivel(nivel);
                int cantidad = detalle.getCantidadSolicitada() != null ? detalle.getCantidadSolicitada() : 1;
                BigDecimal subtotal = (precio != null ? precio : BigDecimal.ZERO).multiply(BigDecimal.valueOf(cantidad));

                // Icono de estado
                String iconoEstado = detalle.estaVendido() ? "[V]" : "[  ]";
                Color colorEstado = detalle.estaVendido() ? new Color(40, 167, 69) : Color.BLACK;

                tableCotizados.addCell(crearCeldaCentrada(String.valueOf(num)));
                tableCotizados.addCell(crearCeldaConColor(iconoEstado, colorEstado));

                // Descripción
                String descripcion;
                boolean esCotizacionProv = detalle.esCotizacionProveedorPorNivel(nivel) &&
                                           detalle.getNombreProductoPorNivel(nivel) == null;
                if (detalle.esItemRegalo()) {
                    descripcion = "* REGALO: " + detalle.getTextoOriginal();
                } else if (esCotizacionProv) {
                    // Cotización de proveedor (sin producto de inventario)
                    String descManual = detalle.getDescripcionManualPorNivel(nivel);
                    String nombreMostrar = descManual != null && !descManual.isBlank() ? descManual : detalle.getTextoOriginal();
                    descripcion = "** " + nombreMostrar;
                    if (detalle.getNombreProveedor() != null && !detalle.getNombreProveedor().isBlank()) {
                        descripcion += " (" + detalle.getNombreProveedor() + ")";
                    }
                } else {
                    String nombreProducto = detalle.getNombreProductoPorNivel(nivel);
                    descripcion = nombreProducto != null ? nombreProducto : detalle.getTextoOriginal();
                }
                tableCotizados.addCell(crearCelda(descripcion));

                tableCotizados.addCell(crearCeldaCentrada(String.valueOf(cantidad)));

                if (detalle.esItemRegalo()) {
                    tableCotizados.addCell(crearCeldaDerecha("GRATIS"));
                    tableCotizados.addCell(crearCeldaDerecha("-"));
                } else {
                    tableCotizados.addCell(crearCeldaDerecha(moneda + String.format("%.2f", precio)));
                    tableCotizados.addCell(crearCeldaDerecha(moneda + String.format("%.2f", subtotal)));
                    totalCotizados = totalCotizados.add(subtotal);
                }
            }

            // Fila TOTAL
            PdfPCell cellTotalLabel = new PdfPCell(new Phrase("SUBTOTAL " + tituloNivel,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            cellTotalLabel.setColspan(5);
            cellTotalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellTotalLabel.setPadding(6);
            cellTotalLabel.setBackgroundColor(new Color(248, 249, 250));
            tableCotizados.addCell(cellTotalLabel);

            PdfPCell cellTotalValor = new PdfPCell(new Phrase(moneda + String.format("%.2f", totalCotizados),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE)));
            cellTotalValor.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellTotalValor.setPadding(6);
            cellTotalValor.setBackgroundColor(colorNivel);
            tableCotizados.addCell(cellTotalValor);

            document.add(tableCotizados);
        }

        // === SECCION 2: NO DISPONIBLES / PENDIENTES ===
        if (!itemsNoDisponibles.isEmpty()) {
            document.add(new Paragraph(" "));

            Paragraph pPendientes = new Paragraph("PENDIENTES / NO DISPONIBLE (" + tituloNivel + ")",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(220, 53, 69)));
            pPendientes.setSpacingBefore(5);
            document.add(pPendientes);

            PdfPTable tablePendientes = new PdfPTable(4);
            tablePendientes.setWidthPercentage(100);
            tablePendientes.setWidths(new float[]{0.5f, 0.5f, 4f, 1f});
            tablePendientes.setSpacingBefore(5);

            // Cabecera roja
            Color colorRojo = new Color(220, 53, 69);
            for (String h : new String[]{"#", "Estado", "Item Solicitado", "Cant"}) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
                cell.setBackgroundColor(colorRojo);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(5);
                tablePendientes.addCell(cell);
            }

            int num = 0;
            for (DetalleListaEscolar detalle : itemsNoDisponibles) {
                num++;
                int cantidad = detalle.getCantidadSolicitada() != null ? detalle.getCantidadSolicitada() : 1;

                tablePendientes.addCell(crearCeldaCentrada(String.valueOf(num)));
                tablePendientes.addCell(crearCeldaConColor("[X]", colorRojo));
                tablePendientes.addCell(crearCelda(detalle.getTextoOriginal()));
                tablePendientes.addCell(crearCeldaCentrada(String.valueOf(cantidad)));
            }

            document.add(tablePendientes);

            // Nota explicativa
            Paragraph nota = new Paragraph("* Estos ítems no tienen producto asignado o no están disponibles actualmente.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY));
            nota.setSpacingBefore(3);
            document.add(nota);
        }

        document.add(new Paragraph(" "));
    }

    private void agregarCabeceraTablaPDFMejorada(PdfPTable table, Color color) {
        String[] headers = {"#", "Est.", "Descripción", "Cant", "P.Unit", "Subtotal"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
            cell.setBackgroundColor(color.darker());
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private void agregarLeyendaEstados(Document document) throws DocumentException {
        document.add(new Paragraph(" "));
        Paragraph leyenda = new Paragraph("Leyenda: [V] = Vendido  |  [  ] = Pendiente  |  [X] = No Disponible  |  ** = Cotizado con proveedor",
            FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY));
        leyenda.setAlignment(Element.ALIGN_CENTER);
        document.add(leyenda);
    }

    private PdfPCell crearCeldaConColor(String texto, Color color) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, color)));
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private void agregarCabeceraPDF(Document document, Configuracion config, ListaEscolar lista) throws DocumentException {
        Color colorPrimario = parseColor(config.getColorPrimario(), Color.BLUE);

        // Tabla de cabecera
        int numColumnas = (Boolean.TRUE.equals(config.getMostrarLogoEnReportes()) && config.getLogoBase64() != null) ? 3 : 2;
        PdfPTable headerTable = new PdfPTable(numColumnas);
        headerTable.setWidthPercentage(100);

        // Logo (si está habilitado)
        if (Boolean.TRUE.equals(config.getMostrarLogoEnReportes()) && config.getLogoBase64() != null) {
            try {
                byte[] logoBytes = Base64.getDecoder().decode(config.getLogoBase64());
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(70, 70);
                PdfPCell cellLogo = new PdfPCell(logo);
                cellLogo.setBorder(Rectangle.NO_BORDER);
                cellLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
                headerTable.addCell(cellLogo);
            } catch (Exception e) {
                PdfPCell cellEmpty = new PdfPCell();
                cellEmpty.setBorder(Rectangle.NO_BORDER);
                headerTable.addCell(cellEmpty);
            }
        }

        // Datos de empresa
        PdfPCell cellEmpresa = new PdfPCell();
        cellEmpresa.setBorder(Rectangle.NO_BORDER);
        cellEmpresa.addElement(new Paragraph(config.getNombreEmpresa(),
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        cellEmpresa.addElement(new Paragraph("RUC: " + config.getRuc(),
            FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cellEmpresa.addElement(new Paragraph(config.getDireccion(),
            FontFactory.getFont(FontFactory.HELVETICA, 9)));
        if (config.getTelefono() != null) {
            cellEmpresa.addElement(new Paragraph("Tel: " + config.getTelefono(),
                FontFactory.getFont(FontFactory.HELVETICA, 9)));
        }
        headerTable.addCell(cellEmpresa);

        // Título y fecha
        PdfPCell cellTitulo = new PdfPCell();
        cellTitulo.setBorder(Rectangle.NO_BORDER);
        cellTitulo.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph pTitulo = new Paragraph("COTIZACIÓN DE LISTA ESCOLAR",
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, colorPrimario));
        pTitulo.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pTitulo);

        Paragraph pCodigo = new Paragraph(lista.getCodigoCompleto(),
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        pCodigo.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pCodigo);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Paragraph pFecha = new Paragraph("Fecha: " + LocalDate.now().format(formatter),
            FontFactory.getFont(FontFactory.HELVETICA, 10));
        pFecha.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pFecha);

        headerTable.addCell(cellTitulo);
        document.add(headerTable);
        document.add(new Paragraph(" "));
    }

    private void agregarDatosAlumno(Document document, ListaEscolar lista) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 3f, 1.5f, 3f});

        Color bgColor = new Color(240, 240, 240);

        // Fila 1
        table.addCell(crearCeldaLabel("Alumno:", bgColor));
        table.addCell(crearCeldaValor(lista.getNombreAlumno()));
        table.addCell(crearCeldaLabel("Grado:", bgColor));
        table.addCell(crearCeldaValor(lista.getGrado()));

        // Fila 2
        table.addCell(crearCeldaLabel("Colegio:", bgColor));
        table.addCell(crearCeldaValor(lista.getColegio() != null ? lista.getColegio() : "-"));
        table.addCell(crearCeldaLabel("Año:", bgColor));
        table.addCell(crearCeldaValor(String.valueOf(lista.getAnioEscolar())));

        // Fila 3 - Contacto
        table.addCell(crearCeldaLabel("Contacto:", bgColor));
        table.addCell(crearCeldaValor(lista.getContactoNombre() != null ? lista.getContactoNombre() : "-"));
        table.addCell(crearCeldaLabel("Teléfono:", bgColor));
        table.addCell(crearCeldaValor(lista.getContactoTelefono() != null ? lista.getContactoTelefono() : "-"));

        document.add(table);
        document.add(new Paragraph(" "));
    }

    private PdfPCell crearCeldaLabel(String texto, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(texto,
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(5);
        return cell;
    }

    private PdfPCell crearCeldaValor(String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "",
            FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cell.setPadding(5);
        return cell;
    }

    private PdfPCell crearCelda(String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "",
            FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cell.setPadding(4);
        return cell;
    }

    private PdfPCell crearCeldaCentrada(String texto) {
        PdfPCell cell = crearCelda(texto);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private PdfPCell crearCeldaDerecha(String texto) {
        PdfPCell cell = crearCelda(texto);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private Color parseColor(String hexColor, Color defaultColor) {
        if (hexColor == null || hexColor.trim().isEmpty()) return defaultColor;
        try {
            String hex = hexColor.replace("#", "");
            return new Color(
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
            );
        } catch (Exception e) {
            return defaultColor;
        }
    }

    // =========================================================
    //  COTIZACIÓN MANUAL (PROVEEDOR)
    // =========================================================

    /**
     * Establece una cotización manual de proveedor para un nivel.
     */
    @PostMapping("/api/detalle/{detalleId}/cotizacion-proveedor")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> setCotizacionProveedor(
            @PathVariable Long detalleId,
            @RequestBody Map<String, Object> datos) {
        try {
            String nivel = (String) datos.get("nivel");
            BigDecimal precio = new BigDecimal(datos.get("precio").toString());
            String descripcion = (String) datos.get("descripcion");
            String proveedor = (String) datos.get("proveedor");

            DetalleListaEscolar detalle = listaService.setCotizacionProveedor(
                detalleId, nivel, precio, descripcion, proveedor);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", detalle.getId());
            response.put("precioEconomico", detalle.getPrecioEconomico() != null ? detalle.getPrecioEconomico() : BigDecimal.ZERO);
            response.put("precioMedio", detalle.getPrecioMedio() != null ? detalle.getPrecioMedio() : BigDecimal.ZERO);
            response.put("precioPremium", detalle.getPrecioPremium() != null ? detalle.getPrecioPremium() : BigDecimal.ZERO);
            response.put("cotizadoProveedorEconomico", Boolean.TRUE.equals(detalle.getCotizadoProveedorEconomico()));
            response.put("cotizadoProveedorMedio", Boolean.TRUE.equals(detalle.getCotizadoProveedorMedio()));
            response.put("cotizadoProveedorPremium", Boolean.TRUE.equals(detalle.getCotizadoProveedorPremium()));
            response.put("nombreProveedor", detalle.getNombreProveedor() != null ? detalle.getNombreProveedor() : "");
            response.put("descripcionManualEconomico", detalle.getDescripcionManualEconomico() != null ? detalle.getDescripcionManualEconomico() : "");
            response.put("descripcionManualMedio", detalle.getDescripcionManualMedio() != null ? detalle.getDescripcionManualMedio() : "");
            response.put("descripcionManualPremium", detalle.getDescripcionManualPremium() != null ? detalle.getDescripcionManualPremium() : "");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error al establecer cotización proveedor: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "Error desconocido");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Vincula un producto real a un item con cotización manual.
     */
    @PostMapping("/api/detalle/{detalleId}/vincular-producto")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> vincularProducto(
            @PathVariable Long detalleId,
            @RequestBody Map<String, Object> datos) {
        try {
            String nivel = (String) datos.get("nivel");
            Long productoId = Long.valueOf(datos.get("productoId").toString());

            DetalleListaEscolar detalle = listaService.vincularProductoAManual(detalleId, nivel, productoId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "id", detalle.getId(),
                "productoComprado", detalle.getProductoComprado()
            ));
        } catch (Exception e) {
            log.error("Error al vincular producto: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================
    //  PRODUCTOS FALTANTES
    // =========================================================

    /**
     * Vista de productos faltantes (demanda insatisfecha).
     */
    @GetMapping("/faltantes")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    public String vistaFaltantes(Model model) {
        model.addAttribute("faltantes", listaService.obtenerProductosFaltantes());
        model.addAttribute("cotizadosProveedor", listaService.obtenerCotizadosProveedor());
        model.addAttribute("estadisticas", listaService.obtenerEstadisticasFaltantes());
        return "lista-escolar/faltantes";
    }

    /**
     * API: Obtiene productos faltantes agrupados.
     */
    @GetMapping("/api/faltantes")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> obtenerFaltantes() {
        return ResponseEntity.ok(listaService.obtenerProductosFaltantes());
    }

    /**
     * API: Obtiene detalles de un producto faltante específico.
     */
    @GetMapping("/api/faltantes/detalle")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalleFaltante(@RequestParam String texto) {
        String textoNormalizado = com.libreria.sistema.model.dto.ProductoFaltanteDTO.normalizarTexto(texto);
        return ResponseEntity.ok(listaService.obtenerDetallesFaltante(textoNormalizado));
    }

    /**
     * API: Obtiene items con cotización de proveedor.
     */
    @GetMapping("/api/cotizados-proveedor")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> obtenerCotizadosProveedor() {
        List<DetalleListaEscolar> items = listaService.obtenerCotizadosProveedor();

        List<Map<String, Object>> resultado = items.stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("textoOriginal", item.getTextoOriginal());
            map.put("cantidad", item.getCantidadSolicitada());
            map.put("nombreProveedor", item.getNombreProveedor());
            map.put("fechaCotizacion", item.getFechaCotizacionProveedor());

            // Información de la lista
            ListaEscolar lista = item.getListaEscolar();
            map.put("listaId", lista.getId());
            map.put("codigoLista", lista.getCodigoCompleto());
            map.put("nombreAlumno", lista.getNombreAlumno());
            map.put("colegio", lista.getColegio());

            // Precios por nivel
            map.put("precioEconomico", item.getPrecioEconomico());
            map.put("precioMedio", item.getPrecioMedio());
            map.put("precioPremium", item.getPrecioPremium());
            map.put("cotizadoProveedorEconomico", item.getCotizadoProveedorEconomico());
            map.put("cotizadoProveedorMedio", item.getCotizadoProveedorMedio());
            map.put("cotizadoProveedorPremium", item.getCotizadoProveedorPremium());
            map.put("descripcionManualEconomico", item.getDescripcionManualEconomico());
            map.put("descripcionManualMedio", item.getDescripcionManualMedio());
            map.put("descripcionManualPremium", item.getDescripcionManualPremium());

            return map;
        }).toList();

        return ResponseEntity.ok(resultado);
    }

    /**
     * API: Asigna producto masivamente a items faltantes.
     */
    @PostMapping("/api/faltantes/asignar-masivo")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> asignarProductoMasivo(@RequestBody Map<String, Object> datos) {
        try {
            String textoNormalizado = (String) datos.get("textoNormalizado");
            String nivel = (String) datos.get("nivel");
            Long productoId = Long.valueOf(datos.get("productoId").toString());

            int actualizados = listaService.asignarProductoMasivo(textoNormalizado, nivel, productoId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "actualizados", actualizados
            ));
        } catch (Exception e) {
            log.error("Error en asignación masiva: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * API: Marca item como producto comprado.
     */
    @PostMapping("/api/detalle/{detalleId}/marcar-comprado")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> marcarComprado(@PathVariable Long detalleId) {
        try {
            DetalleListaEscolar detalle = listaService.marcarProductoComprado(detalleId);
            return ResponseEntity.ok(Map.of("success", true, "id", detalle.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================
    //  UTILIDADES
    // =========================================================

    private List<String> getGradosDisponibles() {
        return List.of(
            "3 años", "4 años", "5 años",
            "1° Primaria", "2° Primaria", "3° Primaria",
            "4° Primaria", "5° Primaria", "6° Primaria",
            "1° Secundaria", "2° Secundaria", "3° Secundaria",
            "4° Secundaria", "5° Secundaria"
        );
    }
}
