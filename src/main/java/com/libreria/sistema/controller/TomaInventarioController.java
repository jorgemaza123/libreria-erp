package com.libreria.sistema.controller;

import com.libreria.sistema.model.DetalleTomaInventario;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.TomaInventario;
import com.libreria.sistema.model.dto.EtiquetaPdfOpcionesDTO;
import com.libreria.sistema.service.EtiquetaService;
import com.libreria.sistema.service.ProductoService;
import com.libreria.sistema.service.TomaInventarioService;
import com.libreria.sistema.service.TomaInventarioService.ConteoRapidoDTO;
import com.libreria.sistema.service.TomaInventarioService.ConteoDTO;
import com.libreria.sistema.service.TomaInventarioService.EstadisticasToma;
import com.libreria.sistema.service.TomaInventarioService.ResultadoProcesamiento;

import com.lowagie.text.DocumentException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controlador para el módulo de Toma de Inventario Físico.
 */
@Controller
@RequestMapping("/toma-inventario")
@PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_VER') or hasPermission(null, 'INVENTARIO_EDITAR')")
@Slf4j
public class TomaInventarioController {

    private final TomaInventarioService tomaService;
    private final ProductoService productoService;
    private final EtiquetaService etiquetaService;

    public TomaInventarioController(TomaInventarioService tomaService,
                                    ProductoService productoService,
                                    EtiquetaService etiquetaService) {
        this.tomaService = tomaService;
        this.productoService = productoService;
        this.etiquetaService = etiquetaService;
    }

    // ========== VISTAS ==========

    /**
     * Lista de todas las tomas de inventario (historial).
     */
    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<TomaInventario> tomas = tomaService.listarTomas(
                PageRequest.of(page, 15, Sort.by("fechaInicio").descending()));
        List<TomaInventario> contenido = tomas.getContent();

        model.addAttribute("tomas", contenido);
        model.addAttribute("totalesProductosToma", tomaService.contarProductosPorTomas(contenido));
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", tomas.getTotalPages());
        model.addAttribute("totalItems", tomas.getTotalElements());

        // Verificar si hay toma abierta
        model.addAttribute("hayTomaAbierta", tomaService.existeTomaAbierta());

        return "toma-inventario/lista";
    }

    /**
     * Formulario para iniciar nueva toma de inventario.
     */
    @GetMapping("/nueva")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR')")
    public String nuevaToma(Model model, RedirectAttributes redirect) {
        // Verificar si ya hay una toma abierta
        if (tomaService.existeTomaAbierta()) {
            redirect.addFlashAttribute("warning", "Ya existe una toma de inventario abierta. Debe cerrarla antes de iniciar otra.");
            List<TomaInventario> abiertas = tomaService.obtenerTomasAbiertas();
            if (!abiertas.isEmpty()) {
                return "redirect:/toma-inventario/conteo/" + abiertas.get(0).getId();
            }
            return "redirect:/toma-inventario";
        }

        return "toma-inventario/nueva";
    }

    @GetMapping("/rapida")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    public String regularizacionRapida(Model model) {
        prepararModeloRegularizacionRapida(model, false);
        return "toma-inventario/rapida";
    }

    @GetMapping("/rapida/movil")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    public String regularizacionRapidaMovil(Model model) {
        prepararModeloRegularizacionRapida(model, true);
        return "toma-inventario/rapida";
    }

    private void prepararModeloRegularizacionRapida(Model model, boolean modoMovil) {
        Optional<TomaInventario> tomaAbierta = tomaService.obtenerTomaAbiertaActual();
        model.addAttribute("toma", tomaAbierta.orElse(null));
        model.addAttribute("hayTomaAbierta", tomaAbierta.isPresent());
        model.addAttribute("zonasRapidas", tomaService.obtenerZonasRapidas());
        model.addAttribute("zonasRapidasEstado", tomaService.obtenerZonasRapidasConEstado(tomaAbierta.map(TomaInventario::getId).orElse(null)));
        model.addAttribute("clasificacionesInventario", clasificacionesInventario());
        model.addAttribute("modoMovil", modoMovil);
        if (tomaAbierta.isPresent()) {
            Long tomaId = tomaAbierta.get().getId();
            model.addAttribute("stats", tomaService.obtenerEstadisticas(tomaId));
            model.addAttribute("ultimosContados", tomaService.ultimosContados(tomaId));
            model.addAttribute("productosSospechosos", tomaService.listarProductosSospechosos(tomaId));
        } else {
            model.addAttribute("productosSospechosos", List.of());
        }
    }

    private List<Map<String, String>> clasificacionesInventario() {
        return List.of(
                Map.of("valor", Producto.CLASIFICACION_MERCADERIA, "etiqueta", "Mercaderia vendible", "ayuda", "Lapiceros, cartulinas, juguetes, cuadernos"),
                Map.of("valor", Producto.CLASIFICACION_INSUMO, "etiqueta", "Insumo", "ayuda", "Papel fotografico, tintas, vinil, globos, flores"),
                Map.of("valor", Producto.CLASIFICACION_SERVICIO, "etiqueta", "Servicio", "ayuda", "Impresiones, edicion, CV, maquetas, sublimacion"),
                Map.of("valor", Producto.CLASIFICACION_INACTIVO, "etiqueta", "Inactivo", "ayuda", "Producto que ya no vendes"),
                Map.of("valor", Producto.CLASIFICACION_DESCONOCIDO, "etiqueta", "Desconocido", "ayuda", "Encontrado, pendiente de clasificar"));
    }

    @PostMapping("/rapida/iniciar")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    public String iniciarRegularizacionRapida(@RequestParam(required = false) String observaciones,
                                              RedirectAttributes redirect) {
        TomaInventario toma = tomaService.iniciarTomaRapida(observaciones);
        redirect.addFlashAttribute("success", "Regularizacion rapida lista: " + toma.getCodigo());
        return "redirect:/toma-inventario/rapida";
    }

    /**
     * Crea una nueva toma de inventario.
     */
    @PostMapping("/crear")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR')")
    public String crearToma(@RequestParam(required = false) String observaciones,
                            RedirectAttributes redirect) {
        try {
            TomaInventario toma = tomaService.iniciarToma(observaciones);
            redirect.addFlashAttribute("success",
                    "Toma de inventario " + toma.getCodigo() + " iniciada correctamente con " +
                    toma.getTotalProductos() + " productos.");
            return "redirect:/toma-inventario/conteo/" + toma.getId();
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/toma-inventario";
        } catch (Exception e) {
            log.error("Error al crear toma de inventario", e);
            redirect.addFlashAttribute("error", "Error al iniciar la toma: " + e.getMessage());
            return "redirect:/toma-inventario";
        }
    }

    /**
     * Vista de conteo (tabla estilo Excel).
     */
    @GetMapping("/conteo/{id}")
    public String vistaConteo(@PathVariable Long id,
                              @RequestParam(required = false) String filtro,
                              Model model,
                              RedirectAttributes redirect) {
        TomaInventario toma = tomaService.obtenerPorId(id).orElse(null);
        if (toma == null) {
            redirect.addFlashAttribute("error", "Toma de inventario no encontrada");
            return "redirect:/toma-inventario";
        }

        List<DetalleTomaInventario> detalles = filtro != null && !filtro.isBlank()
                ? tomaService.buscarDetalles(id, filtro)
                : tomaService.listarDetalles(id);

        EstadisticasToma stats = tomaService.obtenerEstadisticas(id);

        model.addAttribute("toma", toma);
        model.addAttribute("detalles", detalles);
        model.addAttribute("stats", stats);
        model.addAttribute("filtro", filtro);
        model.addAttribute("esEditable", TomaInventario.ESTADO_ABIERTO.equals(toma.getEstado()));

        return "toma-inventario/conteo";
    }

    /**
     * Vista de resumen antes de procesar.
     */
    @GetMapping("/resumen/{id}")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_PROCESAR')")
    public String vistaResumen(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        TomaInventario toma = tomaService.obtenerPorId(id).orElse(null);
        if (toma == null) {
            redirect.addFlashAttribute("error", "Toma de inventario no encontrada");
            return "redirect:/toma-inventario";
        }

        if (!TomaInventario.ESTADO_ABIERTO.equals(toma.getEstado())) {
            redirect.addFlashAttribute("info", "Esta toma ya fue procesada");
            return "redirect:/toma-inventario/conteo/" + id;
        }

        List<DetalleTomaInventario> conDiferencia = tomaService.listarDetallesConDiferencia(id);

        EstadisticasToma stats = tomaService.obtenerEstadisticas(id);

        model.addAttribute("toma", toma);
        model.addAttribute("detallesConDiferencia", conDiferencia);
        model.addAttribute("stats", stats);

        return "toma-inventario/resumen";
    }

    // ========== OPERACIONES AJAX ==========

    /**
     * Guarda el conteo de un producto individual (AJAX).
     */
    @PostMapping("/guardar-conteo")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> guardarConteo(@RequestBody ConteoRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            DetalleTomaInventario detalle = tomaService.guardarConteo(
                    request.getDetalleId(),
                    request.getCantidadFisica(),
                    request.getObservacion());

            response.put("success", true);
            response.put("diferencia", detalle.getDiferencia());
            response.put("contado", detalle.getContado());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error al guardar conteo", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Guarda múltiples conteos de una vez (AJAX).
     */
    @PostMapping("/guardar-conteos-masivo")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> guardarConteosMasivo(@RequestBody ConteosMasivoRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            tomaService.guardarConteosMasivo(request.getTomaId(), request.getConteos());

            EstadisticasToma stats = tomaService.obtenerEstadisticas(request.getTomaId());

            response.put("success", true);
            response.put("mensaje", "Conteos guardados correctamente");
            response.put("stats", stats);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error al guardar conteos masivos", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Obtiene estadísticas actualizadas de una toma (AJAX).
     */
    @GetMapping("/estadisticas/{id}")
    @ResponseBody
    public ResponseEntity<EstadisticasToma> obtenerEstadisticas(@PathVariable Long id) {
        try {
            EstadisticasToma stats = tomaService.obtenerEstadisticas(id);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/rapida/api/buscar")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> buscarRegularizacionRapida(@RequestParam(required = false) String termino) {
        Optional<TomaInventario> toma = tomaService.obtenerTomaAbiertaActual();
        if (toma.isEmpty()) {
            return ResponseEntity.badRequest().body(error("Primero inicia una toma de inventario."));
        }
        try {
            List<ConteoRapidoDTO> resultados = tomaService.buscarParaConteoRapido(toma.get().getId(), termino);
            return ResponseEntity.ok(resultados);
        } catch (Exception e) {
            log.error("Error buscando producto para regularizacion rapida", e);
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @PostMapping("/rapida/api/conteo")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> guardarRegularizacionRapida(@RequestBody ConteoRapidoRequest request) {
        Optional<TomaInventario> toma = tomaService.obtenerTomaAbiertaActual();
        if (toma.isEmpty()) {
            return ResponseEntity.badRequest().body(error("Primero inicia una toma de inventario."));
        }
        if (request == null || request.productoId == null) {
            return ResponseEntity.badRequest().body(error("Selecciona un producto."));
        }
        try {
            ConteoRapidoDTO guardado = tomaService.guardarConteoRapido(
                    toma.get().getId(),
                    request.productoId,
                    request.cantidadFisica,
                    request.zona,
                    request.observacion);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("producto", guardado);
            response.put("stats", tomaService.obtenerEstadisticas(toma.get().getId()));
            response.put("ultimos", tomaService.ultimosContados(toma.get().getId()));
            response.put("zonasEstado", tomaService.obtenerZonasRapidasConEstado(toma.get().getId()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error guardando conteo rapido", e);
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @PostMapping("/rapida/api/producto")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> crearProductoRegularizacionRapida(@RequestBody ProductoRapidoRequest request) {
        Optional<TomaInventario> toma = tomaService.obtenerTomaAbiertaActual();
        if (toma.isEmpty()) {
            return ResponseEntity.badRequest().body(error("Primero inicia una toma de inventario."));
        }
        if (request == null || request.nombre == null || request.nombre.isBlank()) {
            return ResponseEntity.badRequest().body(error("El nombre del producto es obligatorio."));
        }
        try {
            Producto producto = new Producto();
            producto.setNombre(request.nombre.trim().toUpperCase());
            producto.setCodigoInterno(limpiar(request.codigoInterno));
            producto.setCodigoBarra(limpiar(request.codigoBarra));
            producto.setCategoria(limpiar(request.categoria));
            producto.setMarca(limpiar(request.marca));
            producto.setColor(limpiar(request.color));
            producto.setModelo(limpiar(request.modelo));
            producto.setTipo(limpiar(request.tipo) != null ? limpiar(request.tipo).toUpperCase() : "ESTANDAR");
            producto.setClasificacion(Producto.normalizarClasificacionInventario(request.clasificacion));
            if (Producto.CLASIFICACION_SERVICIO.equals(producto.getClasificacion())) {
                producto.setTipo("SERVICIO");
            }
            if (Producto.CLASIFICACION_INACTIVO.equals(producto.getClasificacion())) {
                producto.setActivo(false);
            }
            producto.setPrecioCompra(request.precioCompra != null ? request.precioCompra : BigDecimal.ZERO);
            producto.setPrecioVenta(request.precioVenta != null ? request.precioVenta : BigDecimal.ZERO);
            int stockInicial = request.cantidadFisica != null && request.cantidadFisica > 0 ? request.cantidadFisica : 0;
            producto.setStockActual(producto.controlaStockInventario() ? stockInicial : 0);
            producto.setStockMinimo(request.stockMinimo != null ? request.stockMinimo : 0);
            if (!Producto.CLASIFICACION_INACTIVO.equals(producto.getClasificacion())) {
                producto.setActivo(true);
            }
            producto.setUnidadMedida("UNIDAD");
            producto.setOrigenCatalogo(Producto.ORIGEN_CATALOGO_GENERAL);

            productoService.guardar(producto);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            if (producto.controlaStockInventario()) {
                ConteoRapidoDTO guardado = tomaService.guardarConteoRapido(
                        toma.get().getId(),
                        producto.getId(),
                        request.cantidadFisica,
                        request.zona,
                        "Producto creado durante regularizacion rapida");
                response.put("producto", guardado);
                response.put("mensaje", "Producto creado y contado.");
            } else {
                response.put("producto", null);
                response.put("mensaje", "Registro creado. No entra al conteo porque es servicio o inactivo.");
            }
            response.put("stats", tomaService.obtenerEstadisticas(toma.get().getId()));
            response.put("ultimos", tomaService.ultimosContados(toma.get().getId()));
            response.put("zonasEstado", tomaService.obtenerZonasRapidasConEstado(toma.get().getId()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creando producto desde regularizacion rapida", e);
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @PostMapping("/rapida/api/producto/{productoId}/datos")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> actualizarProductoRegularizacionRapida(@PathVariable Long productoId,
                                                                    @RequestBody ProductoRapidoRequest request) {
        Optional<TomaInventario> toma = tomaService.obtenerTomaAbiertaActual();
        if (toma.isEmpty()) {
            return ResponseEntity.badRequest().body(error("Primero inicia una toma de inventario."));
        }
        try {
            ConteoRapidoDTO producto = tomaService.guardarCorreccionProducto(
                    toma.get().getId(),
                    productoId,
                    request.nombre,
                    request.categoria,
                    request.marca,
                    request.color,
                    request.modelo,
                    request.tipo,
                    request.clasificacion,
                    request.precioCompra,
                    request.precioVenta);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("producto", producto);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error actualizando producto desde regularizacion rapida {}", productoId, e);
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @PostMapping("/rapida/api/zonas")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> guardarZonasRapidas(@RequestBody ZonasRapidasRequest request) {
        try {
            List<String> zonas = tomaService.guardarZonasRapidas(request != null ? request.zonas : null);
            Long tomaId = tomaService.obtenerTomaAbiertaActual().map(TomaInventario::getId).orElse(null);
            return ResponseEntity.ok(Map.of(
                    "zonas", zonas,
                    "zonasEstado", tomaService.obtenerZonasRapidasConEstado(tomaId)));
        } catch (Exception e) {
            log.error("Error guardando zonas de regularizacion", e);
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @PostMapping("/rapida/api/zonas/estado")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> guardarEstadoZonaRapida(@RequestBody ZonaEstadoRequest request) {
        try {
            if (request == null || request.zona == null || request.zona.isBlank()) {
                return ResponseEntity.badRequest().body(error("Selecciona una zona."));
            }
            return ResponseEntity.ok(Map.of("zonasEstado",
                    tomaService.guardarEstadoZonaRapida(request.zona, request.estado)));
        } catch (Exception e) {
            log.error("Error guardando estado de zona de regularizacion", e);
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @GetMapping("/rapida/api/sospechosos")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_VER') or hasPermission(null, 'INVENTARIO_VER')")
    @ResponseBody
    public ResponseEntity<?> listarProductosSospechosos() {
        Optional<TomaInventario> toma = tomaService.obtenerTomaAbiertaActual();
        if (toma.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(tomaService.listarProductosSospechosos(toma.get().getId()));
    }

    @GetMapping("/rapida/api/etiquetas/productos")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_VER') or hasPermission(null, 'INVENTARIO_VER')")
    @ResponseBody
    public ResponseEntity<?> listarProductosEtiquetasRegularizacion() {
        Optional<TomaInventario> toma = tomaService.obtenerTomaAbiertaActual();
        if (toma.isEmpty()) {
            return ResponseEntity.badRequest().body(error("Primero inicia una toma de inventario."));
        }
        List<TomaInventarioService.ConteoRapidoDTO> productos =
                tomaService.listarProductosEtiquetasRegularizacion(toma.get().getId());
        List<Long> sugeridos = tomaService.obtenerProductoIdsEtiquetasRegularizacion(toma.get().getId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("productos", productos);
        response.put("productoIdsSugeridos", sugeridos);
        response.put("total", productos.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rapida/etiquetas/imprimir")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_VER') or hasPermission(null, 'INVENTARIO_VER')")
    public ResponseEntity<?> imprimirEtiquetasRegularizacionAvanzada(@RequestBody EtiquetaPdfOpcionesDTO request)
            throws IOException, DocumentException {
        Optional<TomaInventario> toma = tomaService.obtenerTomaAbiertaActual();
        if (toma.isEmpty()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error("Primero inicia una toma de inventario."));
        }
        if (request == null || request.getProductoIds() == null || request.getProductoIds().isEmpty()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error("Agrega al menos un producto a la tanda de etiquetas."));
        }

        Set<Long> permitidos = new LinkedHashSet<>(tomaService.listarProductosEtiquetasRegularizacion(toma.get().getId())
                .stream()
                .map(producto -> producto.productoId)
                .toList());
        LinkedHashSet<Long> seleccionadosOrdenados = new LinkedHashSet<>();
        for (Long productoId : request.getProductoIds()) {
            if (productoId != null && permitidos.contains(productoId)) {
                seleccionadosOrdenados.add(productoId);
            }
        }
        List<Long> seleccionados = List.copyOf(seleccionadosOrdenados);
        if (seleccionados.isEmpty()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error("Los productos seleccionados no pertenecen a la toma abierta."));
        }

        int cantidadSegura = request.getCantidad() != null && request.getCantidad() > 0
                ? Math.min(request.getCantidad(), 100)
                : 1;
        request.setCantidad(cantidadSegura);
        request.setProductoIds(seleccionados);
        productoService.asegurarCodigosParaEtiquetas(seleccionados);

        byte[] pdf = etiquetaService.generarPdfEtiquetas(seleccionados, cantidadSegura, request);
        String filename = "etiquetas_regularizacion_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/rapida/etiquetas")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_VER') or hasPermission(null, 'INVENTARIO_VER')")
    public ResponseEntity<?> imprimirEtiquetasRegularizacion(@RequestParam(defaultValue = "1") int cantidad)
            throws IOException, DocumentException {
        TomaInventario toma = tomaService.obtenerTomaAbiertaActual()
                .orElseThrow(() -> new RuntimeException("No hay toma abierta"));
        List<Long> productoIds = tomaService.obtenerProductoIdsEtiquetasRegularizacion(toma.getId());
        if (productoIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error("No hay productos sin codigo para imprimir etiquetas en esta regularizacion."));
        }
        productoService.asegurarCodigosParaEtiquetas(productoIds);
        int cantidadSegura = Math.max(1, Math.min(cantidad, 100));
        byte[] pdf = etiquetaService.generarPdfEtiquetas(productoIds, cantidadSegura);
        String filename = "etiquetas_regularizacion_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/rapida/api/deshacer")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> deshacerUltimoConteoRapido() {
        Optional<TomaInventario> toma = tomaService.obtenerTomaAbiertaActual();
        if (toma.isEmpty()) {
            return ResponseEntity.badRequest().body(error("Primero inicia una toma de inventario."));
        }
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("producto", tomaService.deshacerUltimoConteo(toma.get().getId()));
            response.put("stats", tomaService.obtenerEstadisticas(toma.get().getId()));
            response.put("ultimos", tomaService.ultimosContados(toma.get().getId()));
            response.put("zonasEstado", tomaService.obtenerZonasRapidasConEstado(toma.get().getId()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deshaciendo conteo rapido", e);
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @PostMapping("/rapida/api/aplicar-precios")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_PROCESAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> aplicarPreciosRegularizacion() {
        Optional<TomaInventario> toma = tomaService.obtenerTomaAbiertaActual();
        if (toma.isEmpty()) {
            return ResponseEntity.badRequest().body(error("Primero inicia una toma de inventario."));
        }
        try {
            Map<String, Object> response = new LinkedHashMap<>(tomaService.aplicarPreciosSugeridosContados(toma.get().getId()));
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error aplicando precios sugeridos desde regularizacion", e);
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @GetMapping("/rapida/exportar/excel")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_VER') or hasPermission(null, 'INVENTARIO_VER')")
    public ResponseEntity<byte[]> exportarRegularizacionExcel() throws IOException {
        TomaInventario toma = tomaService.obtenerTomaAbiertaActual()
                .orElseThrow(() -> new RuntimeException("No hay toma abierta"));
        byte[] bytes = tomaService.exportarRegularizacionExcel(toma.getId());
        String filename = "regularizacion_inventario_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/rapida/exportar/pdf")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_VER') or hasPermission(null, 'INVENTARIO_VER')")
    public void exportarRegularizacionPdf(HttpServletResponse response) throws IOException, DocumentException {
        TomaInventario toma = tomaService.obtenerTomaAbiertaActual()
                .orElseThrow(() -> new RuntimeException("No hay toma abierta"));
        String filename = "regularizacion_inventario_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        tomaService.exportarRegularizacionPdf(toma.getId(), response.getOutputStream());
    }

    // ========== ACCIONES ==========

    /**
     * Procesa y cierra la toma de inventario aplicando los ajustes.
     */
    @PostMapping("/procesar/{id}")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_PROCESAR')")
    public String procesarToma(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            ResultadoProcesamiento resultado = tomaService.procesarAjuste(id);

            StringBuilder mensaje = new StringBuilder("Toma procesada correctamente. ");
            mensaje.append(resultado.ajustesAplicados).append(" ajustes aplicados");

            if (resultado.faltantes > 0) {
                mensaje.append(" (").append(resultado.faltantes).append(" faltantes: -")
                       .append(resultado.totalFaltante).append(" unidades)");
            }
            if (resultado.sobrantes > 0) {
                mensaje.append(" (").append(resultado.sobrantes).append(" sobrantes: +")
                       .append(resultado.totalSobrante).append(" unidades)");
            }

            redirect.addFlashAttribute("success", mensaje.toString());
            return "redirect:/toma-inventario/conteo/" + id;

        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("warning", e.getMessage());
            return "redirect:/toma-inventario/conteo/" + id;
        } catch (Exception e) {
            log.error("Error al procesar toma", e);
            redirect.addFlashAttribute("error", "Error al procesar: " + e.getMessage());
            return "redirect:/toma-inventario/conteo/" + id;
        }
    }

    /**
     * Cancela una toma de inventario sin aplicar cambios.
     */
    @PostMapping("/cancelar/{id}")
    @PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_PROCESAR')")
    public String cancelarToma(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            tomaService.cancelarToma(id);
            redirect.addFlashAttribute("info", "Toma de inventario cancelada");
            return "redirect:/toma-inventario";
        } catch (Exception e) {
            log.error("Error al cancelar toma", e);
            redirect.addFlashAttribute("error", "Error al cancelar: " + e.getMessage());
            return "redirect:/toma-inventario/conteo/" + id;
        }
    }

    private Map<String, Object> error(String mensaje) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", mensaje != null ? mensaje : "No se pudo completar la operacion.");
        return response;
    }

    private String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    // ========== DTOs para requests ==========

    public static class ConteoRequest {
        private Long detalleId;
        private Integer cantidadFisica;
        private String observacion;

        public Long getDetalleId() { return detalleId; }
        public void setDetalleId(Long detalleId) { this.detalleId = detalleId; }
        public Integer getCantidadFisica() { return cantidadFisica; }
        public void setCantidadFisica(Integer cantidadFisica) { this.cantidadFisica = cantidadFisica; }
        public String getObservacion() { return observacion; }
        public void setObservacion(String observacion) { this.observacion = observacion; }
    }

    public static class ConteosMasivoRequest {
        private Long tomaId;
        private List<ConteoDTO> conteos;

        public Long getTomaId() { return tomaId; }
        public void setTomaId(Long tomaId) { this.tomaId = tomaId; }
        public List<ConteoDTO> getConteos() { return conteos; }
        public void setConteos(List<ConteoDTO> conteos) { this.conteos = conteos; }
    }

    public static class ConteoRapidoRequest {
        public Long productoId;
        public Integer cantidadFisica;
        public String zona;
        public String observacion;
    }

    public static class ProductoRapidoRequest {
        public String nombre;
        public String codigoInterno;
        public String codigoBarra;
        public String categoria;
        public String marca;
        public String color;
        public String modelo;
        public String tipo;
        public String clasificacion;
        public BigDecimal precioCompra;
        public BigDecimal precioVenta;
        public Integer stockMinimo;
        public Integer cantidadFisica;
        public String zona;
    }

    public static class ZonasRapidasRequest {
        public List<String> zonas;
    }

    public static class ZonaEstadoRequest {
        public String zona;
        public String estado;
    }
}
