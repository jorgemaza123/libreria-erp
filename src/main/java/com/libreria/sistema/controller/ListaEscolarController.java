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
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Integer anio,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Page<ListaEscolar> listas = listaService.buscar(
            estado, busqueda, anio,
            PageRequest.of(page, size)
        );

        model.addAttribute("listas", listas);
        model.addAttribute("estadoFiltro", estado);
        model.addAttribute("busquedaFiltro", busqueda);
        model.addAttribute("anioFiltro", anio);
        model.addAttribute("estadisticas", listaService.obtenerEstadisticas(anio));

        // Resumen financiero por lista (nivel medio como referencia)
        Map<Long, com.libreria.sistema.model.dto.ResumenFinancieroDTO> resumenesMap = new java.util.HashMap<>();
        for (ListaEscolar lista : listas.getContent()) {
            if (!"PENDIENTE".equals(lista.getEstado()) && !"CANCELADA".equals(lista.getEstado())) {
                try {
                    resumenesMap.put(lista.getId(), listaService.calcularResumenFinanciero(lista.getId()));
                } catch (Exception e) {
                    // Si falla el calculo, no bloquear la vista
                }
            }
        }
        model.addAttribute("resumenesMap", resumenesMap);

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
        model.addAttribute("resumenFinanciero", listaService.calcularResumenFinanciero(id));

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
                } else {
                    item.put("productoId", null);
                    item.put("productoNombre", null);
                    item.put("productoStock", 0);
                    item.put("tieneStock", false);
                }

                // Precios por nivel
                item.put("precioEconomico", d.getPrecioEconomico());
                item.put("precioMedio", d.getPrecioMedio());
                item.put("precioPremium", d.getPrecioPremium());

                // Productos por nivel (necesarios para selección dinámica en la tabla/POS)
                Producto pe = d.getProductoEconomico();
                item.put("productoEconomicoId", pe != null ? pe.getId() : null);
                item.put("productoEconomicoNombre", pe != null ? pe.getNombre() : null);
                item.put("productoEconomicoStock", pe != null ? pe.getStockActual() : null);

                Producto pm = d.getProductoMedio();
                item.put("productoMedioId", pm != null ? pm.getId() : null);
                item.put("productoMedioNombre", pm != null ? pm.getNombre() : null);
                item.put("productoMedioStock", pm != null ? pm.getStockActual() : null);

                Producto pp = d.getProductoPremium();
                item.put("productoPremiumId", pp != null ? pp.getId() : null);
                item.put("productoPremiumNombre", pp != null ? pp.getNombre() : null);
                item.put("productoPremiumStock", pp != null ? pp.getStockActual() : null);

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
     * Buscar productos para asignar.
     * FTS + fallback ILIKE está integrado directamente en ProductoBusquedaService.
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
            response.put("comentarios", lista.getComentarios() != null ? lista.getComentarios() : "");

            // Mapear detalles con toda la información por nivel
            response.put("detalles", lista.getDetalles().stream().map(d -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", d.getId());
                item.put("orden", d.getOrden());
                item.put("textoOriginal", d.getTextoOriginal());
                item.put("cantidad", d.getCantidadSolicitada());
                item.put("estado", d.getEstado());
                item.put("esRegalo", d.esItemRegalo());
                item.put("textoRegalo", d.getTextoRegalo());
                item.put("nivelRegalo", d.getNivelRegalo());
                item.put("esServicio", d.esItemServicio());
                item.put("textoServicio", d.getTextoServicio());
                item.put("nivelServicio", d.getNivelServicio());

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

                // Omisión por nivel
                item.put("omitidoEconomico", Boolean.TRUE.equals(d.getOmitidoEconomico()));
                item.put("omitidoMedio", Boolean.TRUE.equals(d.getOmitidoMedio()));
                item.put("omitidoPremium", Boolean.TRUE.equals(d.getOmitidoPremium()));

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
     * Agregar servicio adicional a un nivel
     */
    @PostMapping("/api/{listaId}/servicio")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> agregarServicio(
            @PathVariable Long listaId,
            @RequestBody Map<String, Object> datos) {
        try {
            String texto = (String) datos.get("texto");
            String nivel = (String) datos.get("nivel");
            BigDecimal precio = new BigDecimal(datos.get("precio").toString());

            if (texto == null || texto.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "La descripcion del servicio es requerida"));
            }
            if (precio.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "El precio debe ser mayor a cero"));
            }

            DetalleListaEscolar servicio = listaService.agregarServicio(listaId, texto, precio, nivel);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "id", servicio.getId(),
                "texto", servicio.getTextoOriginal(),
                "nivel", servicio.getNivelServicio(),
                "precio", precio
            ));
        } catch (Exception e) {
            log.error("Error al agregar servicio: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Eliminar servicio
     */
    @DeleteMapping("/api/servicio/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> eliminarServicio(@PathVariable Long id) {
        try {
            listaService.eliminarServicio(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Omitir o des-omitir un item para un nivel específico
     */
    @PostMapping("/api/detalle/{detalleId}/omitir")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> omitirDetalle(
            @PathVariable Long detalleId,
            @RequestBody Map<String, Object> datos) {
        try {
            String nivel = (String) datos.get("nivel");
            boolean omitir = datos.get("omitir") != null ? (Boolean) datos.get("omitir") : true;

            DetalleListaEscolar detalle = listaService.omitirPorNivel(detalleId, nivel, omitir);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "id", detalle.getId(),
                "omitido", detalle.esOmitidoPorNivel(nivel)
            ));
        } catch (Exception e) {
            log.error("Error al omitir detalle: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Actualizar cantidades solicitadas en lote (para persistir antes de PDF/WhatsApp)
     */
    @PutMapping("/api/{listaId}/cantidades")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> actualizarCantidades(
            @PathVariable Long listaId,
            @RequestBody List<Map<String, Object>> cantidades) {
        try {
            listaService.actualizarCantidades(listaId, cantidades);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error al actualizar cantidades: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Guardar comentarios de la lista
     */
    @PutMapping("/api/{id}/comentarios")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> guardarComentarios(
            @PathVariable Long id,
            @RequestBody Map<String, String> datos) {
        try {
            ListaEscolar lista = listaService.obtenerConDetalles(id)
                .orElseThrow(() -> new RuntimeException("Lista no encontrada"));
            lista.setComentarios(datos.get("comentarios"));
            listaService.guardarLista(lista);
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
    //  PDF EXPORT — SISTEMA ADAPTATIVO DE 1 PÁGINA
    // =========================================================

    /**
     * Normaliza descripciones para reducir longitud 20-30% manteniendo claridad.
     * Aplica abreviaciones estándar y limpieza de texto.
     */
    private static String normalizarDescripcion(String texto) {
        if (texto == null || texto.isBlank()) return texto;
        String r = texto.trim();

        // Capitalizar primera letra
        if (r.length() > 1) {
            r = r.substring(0, 1).toUpperCase() + r.substring(1);
        }

        // Abreviaciones comunes
        r = r.replaceAll("(?i)según indicación del profesor", "según profesor");
        r = r.replaceAll("(?i)según indicaciones del profesor", "según profesor");
        r = r.replaceAll("(?i)según indicación del(a)? docente", "según docente");
        r = r.replaceAll("(?i)editorial\\b", "Ed.");
        r = r.replaceAll("(?i)\\bversión\\b", "v.");
        r = r.replaceAll("(?i)\\bcon\\s+", "c/");
        r = r.replaceAll("(?i)\\bpara\\s+", "p/");
        r = r.replaceAll("(?i)\\bunidad(es)?\\b", "und.");
        r = r.replaceAll("(?i)\\bpaquete\\b", "paq.");
        r = r.replaceAll("(?i)\\bgrande\\b", "gde.");
        r = r.replaceAll("(?i)\\bpequeño(a)?\\b", "peq.");
        r = r.replaceAll("(?i)\\bmediano(a)?\\b", "med.");
        r = r.replaceAll("(?i)\\bdiferentes\\s+colores\\b", "colores surtidos");
        r = r.replaceAll("(?i)\\bcuaderno\\s+cuadriculado\\b", "Cuaderno cuad.");
        r = r.replaceAll("(?i)\\bcuaderno\\s+rayado\\b", "Cuaderno ray.");
        r = r.replaceAll("(?i)\\bcuaderno\\s+doble\\s+raya\\b", "Cuaderno doble raya");
        r = r.replaceAll("(?i)\\bforro\\s+rojo\\b", "– Rojo");
        r = r.replaceAll("(?i)\\bforro\\s+azul\\b", "– Azul");
        r = r.replaceAll("(?i)\\bforro\\s+verde\\b", "– Verde");
        r = r.replaceAll("(?i)\\bforro\\s+amarillo\\b", "– Amarillo");
        r = r.replaceAll("(?i)\\btapa\\s+dura\\b", "T/D");
        r = r.replaceAll("(?i)\\btapa\\s+gruesa\\b", "T/G");
        r = r.replaceAll("(?i)\\bhojas\\s+cuadriculadas\\b", "hojas cuad.");
        r = r.replaceAll("(?i)\\bhojas\\s+rayadas\\b", "hojas ray.");
        r = r.replaceAll("(?i)\\bmilimetrado(a)?\\b", "mm.");
        r = r.replaceAll("(?i)\\btransparente(s)?\\b", "transp.");

        // Limpiar espacios múltiples
        r = r.replaceAll("\\s{2,}", " ").trim();

        return r;
    }

    /**
     * Sanitiza un nombre para usarlo como parte del nombre de archivo PDF.
     * Elimina tildes, caracteres especiales y reemplaza espacios con guiones bajos.
     */
    private static String sanitizarNombreArchivo(String nombre) {
        if (nombre == null || nombre.isBlank()) return "";
        String r = nombre.trim()
            .replaceAll("[áàäâÁÀÄÂ]", "a")
            .replaceAll("[éèëêÉÈËÊ]", "e")
            .replaceAll("[íìïîÍÌÏÎ]", "i")
            .replaceAll("[óòöôÓÒÖÔ]", "o")
            .replaceAll("[úùüûÚÙÜÛ]", "u")
            .replaceAll("[ñÑ]", "n")
            .replaceAll("[^a-zA-Z0-9 _-]", "")
            .replaceAll("\\s+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
        // Limitar a 40 caracteres para no generar nombres demasiado largos
        return r.length() > 40 ? r.substring(0, 40) : r;
    }

    /**
     * Layout adaptativo para PDF A4.
     * No usa estimaciones — el modo se selecciona por cantidad de items
     * y se degrada automáticamente en generarPDF() si la medición real no cabe.
     */
    private static class PdfLayout {
        float fontSize;           // Contenido tabla
        float fontSizeHeader;     // Cabecera columnas
        float fontSizeTitle;      // Título nivel
        float fontSizeSubtotal;   // Línea subtotal
        float fontSizeTotal;      // TOTAL destacado (siempre >=9pt)
        float cellPadding;        // Padding vertical celdas
        float leading;            // Factor interlineado
        float spacingBefore;      // Espacio antes de secciones
        float pendientesFontSize; // Fuente sección pendientes
        float condicionesFontSize;
        String mode;

        static final float ANCHO_DESC_PT = (PageSize.A4.getWidth() - 40f) * (3.8f / 7.5f);

        // Modos ordenados del más amplio al más compacto
        static final String[] MODOS = {"EXPANDED", "NORMAL", "COMPACT", "ULTRA_COMPACT", "NANO", "MICRO"};

        /** Selecciona modo inicial por cantidad de items (sin verificación de altura). */
        static PdfLayout modoInicial(int totalFilas) {
            PdfLayout l = new PdfLayout();
            if (totalFilas <= 12)      aplicarExpanded(l);
            else if (totalFilas <= 22) aplicarNormal(l);
            else if (totalFilas <= 32) aplicarCompact(l);
            else if (totalFilas <= 44) aplicarUltraCompact(l);
            else if (totalFilas <= 58) aplicarNano(l);
            else                       aplicarMicro(l);
            return l;
        }

        /** Degrada al siguiente modo más compacto. Retorna false si ya es MICRO. */
        boolean degradar() {
            return switch (mode) {
                case "EXPANDED"      -> { aplicarNormal(this);       yield true; }
                case "NORMAL"        -> { aplicarCompact(this);      yield true; }
                case "COMPACT"       -> { aplicarUltraCompact(this); yield true; }
                case "ULTRA_COMPACT" -> { aplicarNano(this);         yield true; }
                case "NANO"          -> { aplicarMicro(this);        yield true; }
                default              -> false; // Ya es MICRO, no se puede degradar más
            };
        }

        /** <=12 items: fuentes grandes, celdas generosas */
        private static void aplicarExpanded(PdfLayout l) {
            l.fontSize = 11f;
            l.fontSizeHeader = 11.5f;
            l.fontSizeTitle = 11f;
            l.fontSizeSubtotal = 10f;
            l.fontSizeTotal = 12f;
            l.cellPadding = 5.0f;
            l.leading = 1.4f;
            l.spacingBefore = 6f;
            l.pendientesFontSize = 10f;
            l.condicionesFontSize = 7.5f;
            l.mode = "EXPANDED";
        }

        /** 13-22 items: balanceado */
        private static void aplicarNormal(PdfLayout l) {
            l.fontSize = 10f;
            l.fontSizeHeader = 10.5f;
            l.fontSizeTitle = 10f;
            l.fontSizeSubtotal = 9f;
            l.fontSizeTotal = 11f;
            l.cellPadding = 3.5f;
            l.leading = 1.25f;
            l.spacingBefore = 5f;
            l.pendientesFontSize = 9f;
            l.condicionesFontSize = 7f;
            l.mode = "NORMAL";
        }

        /** 23-32 items: compacto */
        private static void aplicarCompact(PdfLayout l) {
            l.fontSize = 9f;
            l.fontSizeHeader = 9.5f;
            l.fontSizeTitle = 8.5f;
            l.fontSizeSubtotal = 8f;
            l.fontSizeTotal = 10f;
            l.cellPadding = 1.8f;
            l.leading = 1.1f;
            l.spacingBefore = 3f;
            l.pendientesFontSize = 8f;
            l.condicionesFontSize = 6.5f;
            l.mode = "COMPACT";
        }

        /** >32 items: ultra compacto */
        private static void aplicarUltraCompact(PdfLayout l) {
            l.fontSize = 8f;
            l.fontSizeHeader = 8.5f;
            l.fontSizeTitle = 7.5f;
            l.fontSizeSubtotal = 7f;
            l.fontSizeTotal = 9f;
            l.cellPadding = 0.8f;
            l.leading = 1.0f;
            l.spacingBefore = 1.5f;
            l.pendientesFontSize = 7f;
            l.condicionesFontSize = 6f;
            l.mode = "ULTRA_COMPACT";
        }

        /** ~45-58 items: nano compacto */
        private static void aplicarNano(PdfLayout l) {
            l.fontSize = 7.5f;
            l.fontSizeHeader = 8f;
            l.fontSizeTitle = 7f;
            l.fontSizeSubtotal = 6.5f;
            l.fontSizeTotal = 8.5f;
            l.cellPadding = 0.4f;
            l.leading = 0.9f;
            l.spacingBefore = 1f;
            l.pendientesFontSize = 6.5f;
            l.condicionesFontSize = 5.5f;
            l.mode = "NANO";
        }

        /** 58+ items: mínimo legible */
        private static void aplicarMicro(PdfLayout l) {
            l.fontSize = 7f;
            l.fontSizeHeader = 7.5f;
            l.fontSizeTitle = 6.5f;
            l.fontSizeSubtotal = 6f;
            l.fontSizeTotal = 8f;
            l.cellPadding = 0.2f;
            l.leading = 0.85f;
            l.spacingBefore = 0.5f;
            l.pendientesFontSize = 6f;
            l.condicionesFontSize = 5f;
            l.mode = "MICRO";
        }

        static boolean textoHaraWrap(String texto, float fontSize) {
            if (texto == null || texto.isEmpty()) return false;
            return texto.length() * fontSize * 0.5f > ANCHO_DESC_PT;
        }
    }

    /**
     * Separa los ítems de un nivel en COTIZADOS vs NO DISPONIBLES.
     */
    private static class ItemsSeparados {
        final java.util.List<DetalleListaEscolar> cotizados;
        final java.util.List<DetalleListaEscolar> noDisponibles;
        final java.util.List<String> descripcionesCotizados; // para cálculo de wrap

        ItemsSeparados(java.util.List<DetalleListaEscolar> cotizados,
                       java.util.List<DetalleListaEscolar> noDisponibles,
                       java.util.List<String> descripcionesCotizados) {
            this.cotizados = cotizados;
            this.noDisponibles = noDisponibles;
            this.descripcionesCotizados = descripcionesCotizados;
        }

        int totalFilas() {
            int filas = cotizados.size() + 3; // datos + título + header + subtotal
            if (!noDisponibles.isEmpty()) {
                filas += noDisponibles.size() + 2; // datos + título + header
            }
            return filas;
        }

        /** Cuenta filas cuya descripción probablemente hará wrap a 2+ líneas. */
        int filasMultilinea(float fontSize) {
            int count = 0;
            for (String desc : descripcionesCotizados) {
                if (PdfLayout.textoHaraWrap(desc, fontSize)) count++;
            }
            // También contar textos largos en no disponibles
            for (DetalleListaEscolar d : noDisponibles) {
                if (PdfLayout.textoHaraWrap(d.getTextoOriginal(), fontSize)) count++;
            }
            return count;
        }
    }

    private ItemsSeparados separarItems(ListaEscolar lista, String nivel) {
        java.util.List<DetalleListaEscolar> cotizados = new java.util.ArrayList<>();
        java.util.List<DetalleListaEscolar> noDisponibles = new java.util.ArrayList<>();
        java.util.List<String> descripcionesCotizados = new java.util.ArrayList<>();

        for (DetalleListaEscolar detalle : lista.getDetalles()) {
            // Saltar items omitidos para este nivel
            if (detalle.esOmitidoPorNivel(nivel)) {
                continue;
            }
            if (detalle.esItemRegalo() && detalle.getNivelRegalo() != null && !detalle.getNivelRegalo().equals(nivel)) {
                continue;
            }
            if (detalle.esItemServicio() && detalle.getNivelServicio() != null && !detalle.getNivelServicio().equals(nivel)) {
                continue;
            }

            BigDecimal precio = detalle.getPrecioPorNivel(nivel);
            boolean tieneProducto = detalle.getNombreProductoPorNivel(nivel) != null;
            boolean tienePrecio = precio != null && precio.compareTo(BigDecimal.ZERO) > 0;
            boolean esCotizacionProveedor = detalle.esCotizacionProveedorPorNivel(nivel);

            if (detalle.esItemRegalo()) {
                cotizados.add(detalle);
                descripcionesCotizados.add("REGALO: " + normalizarDescripcion(detalle.getTextoOriginal()));
            } else if (detalle.esItemServicio()) {
                cotizados.add(detalle);
                descripcionesCotizados.add("SERVICIO: " + normalizarDescripcion(detalle.getTextoOriginal()));
            } else if ((tieneProducto && tienePrecio) || (esCotizacionProveedor && tienePrecio)) {
                cotizados.add(detalle);
                String desc;
                boolean esCotiProv = esCotizacionProveedor && !tieneProducto;
                if (esCotiProv) {
                    String descManual = detalle.getDescripcionManualPorNivel(nivel);
                    desc = descManual != null && !descManual.isBlank() ? descManual : detalle.getTextoOriginal();
                    if (detalle.getNombreProveedor() != null && !detalle.getNombreProveedor().isBlank()) {
                        desc += " (" + detalle.getNombreProveedor() + ")";
                    }
                } else {
                    String nombreProd = detalle.getNombreProductoPorNivel(nivel);
                    desc = nombreProd != null ? nombreProd : detalle.getTextoOriginal();
                }
                descripcionesCotizados.add(normalizarDescripcion(desc));
            } else {
                noDisponibles.add(detalle);
            }
        }

        return new ItemsSeparados(cotizados, noDisponibles, descripcionesCotizados);
    }

    /**
     * Genera PDF profesional con degradación automática de modo.
     * Algoritmo: tryLayout(EXPANDED) → medir todo → si no cabe → degradar() → rebuild.
     * TODAS las alturas se miden con getTotalHeight() + setLockedWidth(true).
     * El espacio sobrante se distribuye elásticamente entre body y footer.
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

            Set<String> nivelesSet = new HashSet<>(Arrays.asList(niveles.toUpperCase().split(",")));

            response.setContentType("application/pdf");
            // Nombre del archivo: NombreAlumno_Cotizacion_LE001_000003.pdf
            // Usa el nombre del alumno; si no existe, usa el del contacto/apoderado
            String nombrePersona = lista.getNombreAlumno() != null && !lista.getNombreAlumno().isBlank()
                    ? sanitizarNombreArchivo(lista.getNombreAlumno())
                    : sanitizarNombreArchivo(lista.getContactoNombre());
            String prefijo = (nombrePersona != null && !nombrePersona.isBlank()) ? nombrePersona + "_" : "";
            String filename = prefijo + "Cotizacion_" + lista.getCodigoCompleto().replace("-", "_") + ".pdf";
            response.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");

            // A4 con márgenes: 20 laterales, 15 top/bottom
            Document document = new Document(PageSize.A4, 20, 20, 15, 15);
            PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
            document.open();

            float anchoUtil = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
            // Altura útil de la página (espacio entre márgenes)
            float alturaUtil = document.getPageSize().getHeight() - document.topMargin() - document.bottomMargin();

            boolean primeraPagina = true;
            for (String nivel : List.of("ECONOMICO", "MEDIO", "PREMIUM")) {
                if (!nivelesSet.contains(nivel)) continue;

                if (!primeraPagina) {
                    document.newPage();
                }
                primeraPagina = false;

                // 1) Separar items
                ItemsSeparados items = separarItems(lista, nivel);

                // 2) DEGRADACIÓN AUTOMÁTICA DE MODO
                // Intenta el modo más amplio posible, degrada si no cabe en 1 página
                PdfLayout layout = PdfLayout.modoInicial(items.totalFilas());

                // Construir TODAS las tablas y medir ANTES de agregar al documento
                PdfPTable tHeader = pdfBuildHeader(config, lista, layout);
                PdfPTable tAlumno = pdfBuildDatosAlumno(lista, layout);
                PdfPTable[] bodyTables = pdfBuildSeccionNivel(lista, nivel, config, items, layout);
                BigDecimal totalNivel = pdfCalcularTotal(items, nivel);
                // Footer combinado 2 columnas: total (izq) + condiciones+leyenda+comentarios (der)
                PdfPTable tFooter = pdfBuildFooterCombinado(totalNivel, config, layout, lista, items);

                // Medir alturas reales — el footer ya incluye condiciones, leyenda y comentarios
                float hTotal = pdfMedirTodo(anchoUtil, tHeader, tAlumno, bodyTables, tFooter);
                // Gaps entre secciones (~25pt suficiente)
                float margenGaps = 25f;

                // Loop de degradación: si no cabe, reconstruir con modo más compacto
                while (hTotal + margenGaps > alturaUtil && layout.degradar()) {
                    tHeader = pdfBuildHeader(config, lista, layout);
                    tAlumno = pdfBuildDatosAlumno(lista, layout);
                    bodyTables = pdfBuildSeccionNivel(lista, nivel, config, items, layout);
                    tFooter = pdfBuildFooterCombinado(totalNivel, config, layout, lista, items);
                    hTotal = pdfMedirTodo(anchoUtil, tHeader, tAlumno, bodyTables, tFooter);
                }

                log.debug("PDF modo final: {} | hTotal: {}pt | alturaUtil: {}pt | cabe: {}",
                    layout.mode, hTotal + margenGaps, alturaUtil, (hTotal + margenGaps) <= alturaUtil);

                // 3) AGREGAR TODO AL DOCUMENTO
                // Header
                document.add(tHeader);
                // Línea separadora
                pdfAgregarLineaSeparadora(document, config, layout);
                // Datos alumno
                document.add(tAlumno);

                // Body (tabla cotizados + pendientes)
                for (PdfPTable bodyTable : bodyTables) {
                    if (bodyTable != null) document.add(bodyTable);
                }

                // 4) FOOTER COMBINADO — total + condiciones + leyenda + comentarios en 2 columnas
                // Calcula cuánto espacio libre queda y lo usa como spacingBefore del footer
                float posY = writer.getVerticalPosition(true);
                float espacioDisponible = posY - document.bottomMargin();
                tFooter.setLockedWidth(true);
                tFooter.setTotalWidth(anchoUtil);
                float hFooter = tFooter.getTotalHeight();
                float gapFooter = Math.min(Math.max(espacioDisponible - hFooter, 3f), 50f);
                tFooter.setSpacingBefore(gapFooter);
                document.add(tFooter);
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
     * Mide la altura total de TODOS los componentes del PDF usando getTotalHeight().
     * Todas las tablas se miden con setLockedWidth(true) para resultados precisos.
     * El footer es la tabla combinada de 2 columnas (total + condiciones + leyenda + comentarios).
     */
    private float pdfMedirTodo(float anchoUtil, PdfPTable tHeader, PdfPTable tAlumno,
                                PdfPTable[] bodyTables, PdfPTable tFooter) {
        float total = 0;

        // Header
        tHeader.setLockedWidth(true);
        tHeader.setTotalWidth(anchoUtil);
        total += tHeader.getTotalHeight();

        // Línea separadora (~5pt)
        total += 5f;

        // Datos alumno
        tAlumno.setLockedWidth(true);
        tAlumno.setTotalWidth(anchoUtil);
        total += tAlumno.getTotalHeight();

        // Body tables (cotizados + pendientes)
        for (PdfPTable bt : bodyTables) {
            if (bt != null) {
                bt.setLockedWidth(true);
                bt.setTotalWidth(anchoUtil);
                total += bt.getTotalHeight();
            }
        }

        // Footer combinado (2 columnas: total izq + condiciones/leyenda/comentarios der)
        tFooter.setLockedWidth(true);
        tFooter.setTotalWidth(anchoUtil);
        total += tFooter.getTotalHeight();

        return total;
    }

    /** Calcula el total monetario sin construir tablas. */
    private BigDecimal pdfCalcularTotal(ItemsSeparados items, String nivel) {
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleListaEscolar detalle : items.cotizados) {
            if (detalle.esItemRegalo()) continue;
            BigDecimal precio = detalle.getPrecioPorNivel(nivel);
            int cantidad = detalle.getCantidadSolicitada() != null ? detalle.getCantidadSolicitada() : 1;
            BigDecimal subtotal = (precio != null ? precio : BigDecimal.ZERO).multiply(BigDecimal.valueOf(cantidad));
            total = total.add(subtotal);
        }
        return total;
    }

    // =========================================================
    //  PDF — SECCIONES DE RENDERIZADO
    // =========================================================

    /** Construye la tabla header (empresa + título) sin agregar al documento. */
    private PdfPTable pdfBuildHeader(Configuracion config, ListaEscolar lista, PdfLayout layout) throws DocumentException {
        Color colorPrimario = parseColor(config.getColorPrimario(), new Color(44, 62, 80));
        float escala = pdfEscala(layout);

        boolean tienelogo = Boolean.TRUE.equals(config.getMostrarLogoEnReportes()) && config.getLogoBase64() != null;
        PdfPTable headerTable = new PdfPTable(tienelogo ? 3 : 2);
        headerTable.setWidthPercentage(100);
        headerTable.setKeepTogether(true);

        if (tienelogo) {
            headerTable.setWidths(new float[]{0.7f, 3.5f, 3.3f});
            try {
                byte[] logoBytes = Base64.getDecoder().decode(config.getLogoBase64());
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(40 * escala, 40 * escala);
                PdfPCell cellLogo = new PdfPCell(logo);
                cellLogo.setBorder(Rectangle.NO_BORDER);
                cellLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cellLogo.setPadding(2);
                headerTable.addCell(cellLogo);
            } catch (Exception e) {
                PdfPCell c = new PdfPCell(); c.setBorder(Rectangle.NO_BORDER); headerTable.addCell(c);
            }
        } else {
            headerTable.setWidths(new float[]{4f, 3.5f});
        }

        // Datos empresa
        PdfPCell cellEmpresa = new PdfPCell();
        cellEmpresa.setBorder(Rectangle.NO_BORDER);
        cellEmpresa.setPadding(2);
        cellEmpresa.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph pEmpresa = new Paragraph(config.getNombreEmpresa(),
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f * escala, new Color(44, 62, 80)));
        cellEmpresa.addElement(pEmpresa);

        String info = "RUC: " + config.getRuc() + "  |  " + config.getDireccion()
            + (config.getTelefono() != null ? "  |  Tel: " + config.getTelefono() : "");
        float infoFs = 6.5f * escala;
        Paragraph pInfo = new Paragraph(info, FontFactory.getFont(FontFactory.HELVETICA, infoFs, new Color(120, 120, 120)));
        pInfo.setLeading(infoFs + 3);
        cellEmpresa.addElement(pInfo);
        headerTable.addCell(cellEmpresa);

        // Título derecho: COTIZACIÓN + código + validez
        PdfPCell cellTitulo = new PdfPCell();
        cellTitulo.setBorder(Rectangle.NO_BORDER);
        cellTitulo.setPadding(2);
        cellTitulo.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph pTitulo = new Paragraph("COTIZACIÓN LISTA ESCOLAR",
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f * escala, colorPrimario));
        pTitulo.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pTitulo);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        float subFs = 7.5f * escala;
        String codigoYApoderado = lista.getCodigoCompleto()
            + (lista.getContactoNombre() != null && !lista.getContactoNombre().isBlank()
               ? " - " + lista.getContactoNombre() : "");
        Paragraph pCodigo = new Paragraph(codigoYApoderado + "  |  " + LocalDate.now().format(fmt),
            FontFactory.getFont(FontFactory.HELVETICA, subFs, new Color(100, 100, 100)));
        pCodigo.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pCodigo);

        Paragraph pValidez = new Paragraph("Válido hasta: " + LocalDate.now().plusDays(7).format(fmt),
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, subFs - 0.5f, new Color(180, 60, 60)));
        pValidez.setAlignment(Element.ALIGN_RIGHT);
        cellTitulo.addElement(pValidez);

        headerTable.addCell(cellTitulo);
        headerTable.setSpacingAfter(2);
        return headerTable;
    }

    /** Agrega solo la línea separadora al documento (no es tabla medible). */
    private void pdfAgregarLineaSeparadora(Document document, Configuracion config, PdfLayout layout) throws DocumentException {
        Color colorPrimario = parseColor(config.getColorPrimario(), new Color(44, 62, 80));
        boolean compacto = "COMPACT".equals(layout.mode) || "ULTRA_COMPACT".equals(layout.mode);
        PdfPTable linea = new PdfPTable(1);
        linea.setWidthPercentage(100);
        PdfPCell lc = new PdfPCell();
        lc.setBorder(Rectangle.BOTTOM);
        lc.setBorderColor(colorPrimario);
        lc.setBorderWidth(1.2f);
        lc.setFixedHeight(1f);
        linea.addCell(lc);
        linea.setSpacingAfter(compacto ? 2 : 4);
        document.add(linea);
    }

    /** Construye la tabla de datos del alumno sin agregar al documento. */
    private PdfPTable pdfBuildDatosAlumno(ListaEscolar lista, PdfLayout layout) throws DocumentException {
        float escala = pdfEscala(layout);
        float fs = 7f * escala;
        float pad = 2f * escala;

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 2.8f, 1f, 2.5f, 1f, 2.2f});
        table.setKeepTogether(true);

        Color bgLabel = new Color(240, 243, 247);
        Color labelColor = new Color(70, 70, 70);
        Color valorColor = new Color(44, 62, 80);

        // Fila 1
        table.addCell(pdfCeldaLimpia("Alumno:", fs, pad, bgLabel, labelColor, Element.ALIGN_LEFT));
        table.addCell(pdfCeldaLimpia(lista.getNombreAlumno(), fs, pad, Color.WHITE, valorColor, Element.ALIGN_LEFT));
        table.addCell(pdfCeldaLimpia("Grado:", fs, pad, bgLabel, labelColor, Element.ALIGN_LEFT));
        table.addCell(pdfCeldaLimpia(lista.getGrado(), fs, pad, Color.WHITE, valorColor, Element.ALIGN_LEFT));
        table.addCell(pdfCeldaLimpia("Colegio:", fs, pad, bgLabel, labelColor, Element.ALIGN_LEFT));
        table.addCell(pdfCeldaLimpia(lista.getColegio() != null ? lista.getColegio() : "-", fs, pad, Color.WHITE, valorColor, Element.ALIGN_LEFT));

        // Fila 2
        table.addCell(pdfCeldaLimpia("Apoderado:", fs, pad, bgLabel, labelColor, Element.ALIGN_LEFT));
        table.addCell(pdfCeldaLimpia(lista.getContactoNombre() != null ? lista.getContactoNombre() : "-", fs, pad, Color.WHITE, valorColor, Element.ALIGN_LEFT));
        table.addCell(pdfCeldaLimpia("Teléfono:", fs, pad, bgLabel, labelColor, Element.ALIGN_LEFT));
        table.addCell(pdfCeldaLimpia(lista.getContactoTelefono() != null ? lista.getContactoTelefono() : "-", fs, pad, Color.WHITE, valorColor, Element.ALIGN_LEFT));
        table.addCell(pdfCeldaLimpia("Año:", fs, pad, bgLabel, labelColor, Element.ALIGN_LEFT));
        table.addCell(pdfCeldaLimpia(String.valueOf(lista.getAnioEscolar()), fs, pad, Color.WHITE, valorColor, Element.ALIGN_LEFT));

        table.setSpacingAfter(escala < 1f ? 1 : 3);
        return table;
    }

    /** Calcula factor de escala según modo del layout. */
    private float pdfEscala(PdfLayout layout) {
        return switch (layout.mode) {
            case "EXPANDED" -> 1.1f;
            case "COMPACT", "ULTRA_COMPACT" -> 0.85f;
            default -> 1f;
        };
    }

    private String tituloNivel(String nivel) {
        return switch (nivel) {
            case "ECONOMICO" -> "OPCIÓN ECONÓMICA";
            case "MEDIO" -> "OPCIÓN MEDIA";
            case "PREMIUM" -> "OPCIÓN PREMIUM";
            default -> nivel;
        };
    }

    /**
     * Construye las tablas del body (cotizados + pendientes) sin agregar al documento.
     * Retorna array de 1-2 tablas: [tablaCotizados, tablaPendientes?].
     */
    private PdfPTable[] pdfBuildSeccionNivel(ListaEscolar lista, String nivel,
                                         Configuracion config, ItemsSeparados items, PdfLayout layout) throws DocumentException {
        Color colorNivel = switch (nivel) {
            case "ECONOMICO" -> new Color(108, 117, 125);
            case "MEDIO" -> new Color(23, 162, 184);
            case "PREMIUM" -> new Color(255, 193, 7);
            default -> Color.GRAY;
        };

        String tituloNivel = tituloNivel(nivel);

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";
        float fs = layout.fontSize;
        float pad = layout.cellPadding;

        // Colores para diseño limpio
        Color grisSuave = new Color(248, 248, 248);
        Color lineaGris = new Color(220, 220, 220);
        Color textoOscuro = new Color(51, 51, 51);

        java.util.ArrayList<PdfPTable> resultado = new java.util.ArrayList<>();

        // === TABLA COTIZADOS ===
        if (!items.cotizados.isEmpty()) {
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.35f, 0.45f, 3.9f, 0.55f, 1.1f, 1.15f});
            table.setSpacingBefore(layout.spacingBefore);
            table.setSplitLate(false);

            // Título del nivel — barra de color sólido
            PdfPCell cellTit = new PdfPCell(new Phrase(tituloNivel,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, layout.fontSizeTitle, Color.WHITE)));
            cellTit.setBackgroundColor(colorNivel);
            cellTit.setPadding(pad + 1.5f);
            cellTit.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellTit.setColspan(6);
            cellTit.setBorder(Rectangle.NO_BORDER);
            table.addCell(cellTit);

            // Cabecera de columnas
            for (String h : new String[]{"#", "Est.", "Descripción", "Cant", "P.Unit", "Subtotal"}) {
                PdfPCell cell = new PdfPCell(new Phrase(h,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, layout.fontSizeHeader, Color.WHITE)));
                cell.setBackgroundColor(new Color(60, 60, 60));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(pad + 0.5f);
                cell.setBorder(Rectangle.NO_BORDER);
                table.addCell(cell);
            }

            BigDecimal totalCotizados = BigDecimal.ZERO;
            int num = 0;

            for (DetalleListaEscolar detalle : items.cotizados) {
                num++;
                BigDecimal precio = detalle.getPrecioPorNivel(nivel);
                int cantidad = detalle.getCantidadSolicitada() != null ? detalle.getCantidadSolicitada() : 1;
                BigDecimal subtotal = (precio != null ? precio : BigDecimal.ZERO).multiply(BigDecimal.valueOf(cantidad));

                boolean filaAlterna = (num % 2 == 0);
                Color bgFila = filaAlterna ? grisSuave : Color.WHITE;

                String iconoEstado = detalle.estaVendido() ? "[V]" : "[  ]";
                Color colorEstado = detalle.estaVendido() ? new Color(40, 167, 69) : new Color(150, 150, 150);

                table.addCell(pdfCeldaLimpia(String.valueOf(num), fs, pad, bgFila, textoOscuro, Element.ALIGN_CENTER));
                table.addCell(pdfCeldaLimpia(iconoEstado, fs, pad, bgFila, colorEstado, Element.ALIGN_CENTER));

                // Descripción normalizada
                String descripcion;
                boolean esCotizacionProv = detalle.esCotizacionProveedorPorNivel(nivel) &&
                                           detalle.getNombreProductoPorNivel(nivel) == null;
                if (detalle.esItemRegalo()) {
                    descripcion = "REGALO: " + normalizarDescripcion(detalle.getTextoOriginal());
                } else if (detalle.esItemServicio()) {
                    descripcion = "SERVICIO: " + normalizarDescripcion(detalle.getTextoOriginal());
                } else if (esCotizacionProv) {
                    String descManual = detalle.getDescripcionManualPorNivel(nivel);
                    String nombreMostrar = descManual != null && !descManual.isBlank() ? descManual : detalle.getTextoOriginal();
                    descripcion = normalizarDescripcion(nombreMostrar);
                    if (detalle.getNombreProveedor() != null && !detalle.getNombreProveedor().isBlank()) {
                        descripcion += " (" + detalle.getNombreProveedor() + ")";
                    }
                } else {
                    String nombreProducto = detalle.getNombreProductoPorNivel(nivel);
                    descripcion = normalizarDescripcion(nombreProducto != null ? nombreProducto : detalle.getTextoOriginal());
                }
                table.addCell(pdfCeldaLimpia(descripcion, fs, pad, bgFila, textoOscuro, Element.ALIGN_LEFT));
                table.addCell(pdfCeldaLimpia(String.valueOf(cantidad), fs, pad, bgFila, textoOscuro, Element.ALIGN_CENTER));

                if (detalle.esItemRegalo()) {
                    table.addCell(pdfCeldaLimpia("GRATIS", fs, pad, bgFila, new Color(40, 167, 69), Element.ALIGN_RIGHT));
                    table.addCell(pdfCeldaLimpia("-", fs, pad, bgFila, textoOscuro, Element.ALIGN_RIGHT));
                } else {
                    table.addCell(pdfCeldaLimpia(moneda + String.format("%.2f", precio), fs, pad, bgFila, textoOscuro, Element.ALIGN_RIGHT));
                    table.addCell(pdfCeldaLimpia(moneda + String.format("%.2f", subtotal), fs, pad, bgFila, textoOscuro, Element.ALIGN_RIGHT));
                    totalCotizados = totalCotizados.add(subtotal);
                }
            }

            // Línea separadora sutil antes del subtotal
            PdfPCell lineaSep = new PdfPCell();
            lineaSep.setColspan(6);
            lineaSep.setBorder(Rectangle.TOP);
            lineaSep.setBorderColor(lineaGris);
            lineaSep.setBorderWidth(0.5f);
            lineaSep.setFixedHeight(1f);
            table.addCell(lineaSep);

            // Fila SUBTOTAL
            PdfPCell cellLabel = new PdfPCell(new Phrase("TOTAL " + tituloNivel,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, layout.fontSizeSubtotal, textoOscuro)));
            cellLabel.setColspan(5);
            cellLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellLabel.setPadding(pad + 1);
            cellLabel.setBackgroundColor(Color.WHITE);
            cellLabel.setBorder(Rectangle.NO_BORDER);
            table.addCell(cellLabel);

            PdfPCell cellVal = new PdfPCell(new Phrase(moneda + String.format("%.2f", totalCotizados),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, layout.fontSizeSubtotal, Color.WHITE)));
            cellVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellVal.setPadding(pad + 1);
            cellVal.setBackgroundColor(colorNivel);
            cellVal.setBorder(Rectangle.NO_BORDER);
            table.addCell(cellVal);

            resultado.add(table);
        }

        // === TABLA PENDIENTES ===
        if (!items.noDisponibles.isEmpty()) {
            float pfs = layout.pendientesFontSize;
            float ppad = Math.max(pad * 0.7f, 0.6f);

            PdfPTable tPend = new PdfPTable(3);
            tPend.setWidthPercentage(100);
            tPend.setWidths(new float[]{0.3f, 4.5f, 0.7f});
            tPend.setSpacingBefore(layout.spacingBefore);
            tPend.setSplitLate(false);

            PdfPCell tit = new PdfPCell(new Phrase("PENDIENTES - " + tituloNivel,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, pfs, new Color(120, 120, 120))));
            tit.setColspan(3);
            tit.setBorder(Rectangle.BOTTOM);
            tit.setBorderColor(new Color(200, 200, 200));
            tit.setBorderWidth(0.5f);
            tit.setPadding(ppad + 1);
            tPend.addCell(tit);

            Color grisOscuro = new Color(100, 100, 100);
            for (String h : new String[]{"#", "Item Solicitado", "Cant"}) {
                PdfPCell cell = new PdfPCell(new Phrase(h,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, pfs - 0.5f, grisOscuro)));
                cell.setBackgroundColor(new Color(240, 240, 240));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(ppad);
                cell.setBorder(Rectangle.NO_BORDER);
                tPend.addCell(cell);
            }

            int num = 0;
            for (DetalleListaEscolar detalle : items.noDisponibles) {
                num++;
                int cantidad = detalle.getCantidadSolicitada() != null ? detalle.getCantidadSolicitada() : 1;
                boolean filaAlterna = (num % 2 == 0);
                Color bgFila = filaAlterna ? grisSuave : Color.WHITE;

                boolean mostrarLinea = (num % 5 == 0) && num < items.noDisponibles.size();
                int borderBottom = mostrarLinea ? Rectangle.BOTTOM : Rectangle.NO_BORDER;

                PdfPCell c1 = new PdfPCell(new Phrase(String.valueOf(num),
                    FontFactory.getFont(FontFactory.HELVETICA, pfs, textoOscuro)));
                c1.setHorizontalAlignment(Element.ALIGN_CENTER);
                c1.setPadding(ppad);
                c1.setBackgroundColor(bgFila);
                c1.setBorder(borderBottom);
                if (mostrarLinea) { c1.setBorderColor(lineaGris); c1.setBorderWidth(0.3f); }
                tPend.addCell(c1);

                PdfPCell c2 = new PdfPCell(new Phrase(normalizarDescripcion(detalle.getTextoOriginal()),
                    FontFactory.getFont(FontFactory.HELVETICA, pfs, textoOscuro)));
                c2.setPadding(ppad);
                c2.setBackgroundColor(bgFila);
                c2.setBorder(borderBottom);
                if (mostrarLinea) { c2.setBorderColor(lineaGris); c2.setBorderWidth(0.3f); }
                tPend.addCell(c2);

                PdfPCell c3 = new PdfPCell(new Phrase(String.valueOf(cantidad),
                    FontFactory.getFont(FontFactory.HELVETICA, pfs, textoOscuro)));
                c3.setHorizontalAlignment(Element.ALIGN_CENTER);
                c3.setPadding(ppad);
                c3.setBackgroundColor(bgFila);
                c3.setBorder(borderBottom);
                if (mostrarLinea) { c3.setBorderColor(lineaGris); c3.setBorderWidth(0.3f); }
                tPend.addCell(c3);
            }

            resultado.add(tPend);
        }

        return resultado.toArray(new PdfPTable[0]);
    }

    // =========================================================
    //  PDF — FOOTER BUILDERS (construyen sin agregar al document)
    //  Permiten medir altura real con getTotalHeight() antes de agregar.
    // =========================================================

    /** Construye tabla resumen financiero (Subtotal / IGV / TOTAL). */
    private PdfPTable pdfBuildResumenFinanciero(BigDecimal total, Configuracion config, PdfLayout layout) throws DocumentException {
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";
        float fs = layout.fontSizeTotal;
        float padTotal = Math.max(layout.cellPadding + 3, 7f);

        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(50);
        tabla.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.setWidths(new float[]{3f, 2.5f});

        BigDecimal igv = config.getIgvPorcentaje() != null ? config.getIgvPorcentaje() : new BigDecimal("18.00");
        boolean incluyeIgv = Boolean.TRUE.equals(config.getPreciosIncluyenImpuesto());
        String textoIgv = incluyeIgv ? "IGV (incluido):" : "IGV (" + igv.stripTrailingZeros().toPlainString() + "%):";
        BigDecimal montoIgv = incluyeIgv
            ? total.subtract(total.divide(BigDecimal.ONE.add(igv.divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP)), 2, java.math.RoundingMode.HALF_UP))
            : total.multiply(igv).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal totalFinal = incluyeIgv ? total : total.add(montoIgv);

        // Subtotal
        tabla.addCell(pdfCeldaResumen("Subtotal:", fs - 1.5f, new Color(80, 80, 80), layout.cellPadding));
        tabla.addCell(pdfCeldaResumen(moneda + String.format("%.2f", total), fs - 1.5f, new Color(80, 80, 80), layout.cellPadding));

        // IGV
        tabla.addCell(pdfCeldaResumen(textoIgv, fs - 1.5f, new Color(130, 130, 130), layout.cellPadding));
        tabla.addCell(pdfCeldaResumen(moneda + String.format("%.2f", montoIgv), fs - 1.5f, new Color(130, 130, 130), layout.cellPadding));

        // Separador
        PdfPCell sep = new PdfPCell();
        sep.setColspan(2);
        sep.setBorder(Rectangle.TOP);
        sep.setBorderColor(new Color(180, 180, 180));
        sep.setBorderWidth(1f);
        sep.setFixedHeight(3f);
        tabla.addCell(sep);

        // TOTAL — caja oscura dominante
        Color fondoTotal = new Color(44, 62, 80);
        PdfPCell lbl = new PdfPCell(new Phrase("TOTAL:",
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, fs, Color.WHITE)));
        lbl.setBackgroundColor(fondoTotal);
        lbl.setBorder(Rectangle.NO_BORDER);
        lbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
        lbl.setVerticalAlignment(Element.ALIGN_MIDDLE);
        lbl.setPadding(padTotal);
        tabla.addCell(lbl);

        PdfPCell val = new PdfPCell(new Phrase(moneda + String.format("%.2f", totalFinal),
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, fs, Color.WHITE)));
        val.setBackgroundColor(fondoTotal);
        val.setBorder(Rectangle.NO_BORDER);
        val.setHorizontalAlignment(Element.ALIGN_RIGHT);
        val.setVerticalAlignment(Element.ALIGN_MIDDLE);
        val.setPadding(padTotal);
        tabla.addCell(val);

        return tabla;
    }

    private PdfPCell pdfCeldaResumen(String texto, float fontSize, Color textColor, float padding) {
        PdfPCell cell = new PdfPCell(new Phrase(texto,
            FontFactory.getFont(FontFactory.HELVETICA, fontSize, textColor)));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setPadding(padding);
        return cell;
    }

    /** Construye tabla condiciones comerciales. */
    private PdfPTable pdfBuildCondicionesComerciales(Configuracion config, PdfLayout layout) throws DocumentException {
        float fs = layout.condicionesFontSize;
        Color gris = new Color(110, 110, 110);

        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);

        PdfPCell titulo = new PdfPCell(new Phrase("CONDICIONES COMERCIALES",
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, fs + 0.5f, new Color(44, 62, 80))));
        titulo.setBorder(Rectangle.BOTTOM);
        titulo.setBorderColor(new Color(200, 200, 200));
        titulo.setBorderWidth(0.5f);
        titulo.setPadding(3);
        titulo.setBackgroundColor(new Color(245, 245, 245));
        tabla.addCell(titulo);

        String mon = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";
        String igvTexto = Boolean.TRUE.equals(config.getPreciosIncluyenImpuesto()) ? " con IGV incluido" : "";
        String condiciones = "\u2022 Validez: 7 días calendario.    "
            + "\u2022 Forma de pago: Contado.    "
            + "\u2022 Precios en soles (" + mon + ")" + igvTexto + ".\n"
            + "\u2022 Sujeto a disponibilidad de stock.    "
            + "\u2022 No incluye productos pendientes.";

        Paragraph pCond = new Paragraph(condiciones, FontFactory.getFont(FontFactory.HELVETICA, fs, gris));
        pCond.setLeading(fs * 1.7f);

        PdfPCell contenido = new PdfPCell();
        contenido.addElement(pCond);
        contenido.setBorder(Rectangle.NO_BORDER);
        contenido.setPadding(4);
        tabla.addCell(contenido);

        return tabla;
    }

    /** Construye tabla de firmas (2 columnas). */
    private PdfPTable pdfBuildFirma(Configuracion config, PdfLayout layout) throws DocumentException {
        float fs = layout.condicionesFontSize;

        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(80);
        tabla.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabla.setWidths(new float[]{1f, 1f});

        // Firma cliente
        PdfPCell fc = new PdfPCell();
        fc.setBorder(Rectangle.NO_BORDER);
        fc.setPaddingTop(2);
        fc.setPaddingBottom(2);
        Paragraph l1 = new Paragraph("______________________________",
            FontFactory.getFont(FontFactory.HELVETICA, fs, new Color(180, 180, 180)));
        l1.setAlignment(Element.ALIGN_CENTER);
        fc.addElement(l1);
        Paragraph t1 = new Paragraph("Firma del Cliente",
            FontFactory.getFont(FontFactory.HELVETICA, fs, new Color(120, 120, 120)));
        t1.setAlignment(Element.ALIGN_CENTER);
        fc.addElement(t1);
        tabla.addCell(fc);

        // Firma empresa
        PdfPCell fe = new PdfPCell();
        fe.setBorder(Rectangle.NO_BORDER);
        fe.setPaddingTop(2);
        fe.setPaddingBottom(2);
        Paragraph l2 = new Paragraph("______________________________",
            FontFactory.getFont(FontFactory.HELVETICA, fs, new Color(180, 180, 180)));
        l2.setAlignment(Element.ALIGN_CENTER);
        fe.addElement(l2);
        String empresa = config.getNombreEmpresa() != null ? config.getNombreEmpresa() : "Empresa";
        Paragraph t2 = new Paragraph("Firma y Sello - " + empresa,
            FontFactory.getFont(FontFactory.HELVETICA, fs, new Color(120, 120, 120)));
        t2.setAlignment(Element.ALIGN_CENTER);
        fe.addElement(t2);
        tabla.addCell(fe);

        return tabla;
    }

    /** Construye Paragraph de leyenda (mantenido por compatibilidad, no usado en layout combinado). */
    private Paragraph pdfBuildLeyenda(PdfLayout layout) {
        float fs = Math.max(layout.condicionesFontSize - 1f, 5.5f);
        Paragraph leyenda = new Paragraph(
            "\u25a0 Vendido    \u25a1 Pendiente de venta",
            FontFactory.getFont(FontFactory.HELVETICA, fs, new Color(190, 190, 190)));
        leyenda.setAlignment(Element.ALIGN_CENTER);
        return leyenda;
    }

    /**
     * Footer combinado en DOS COLUMNAS:
     *   Izquierda (42 %): resumen financiero (Subtotal / IGV / TOTAL)
     *   Derecha  (58 %): condiciones comerciales + comentarios + leyenda [V]/[ ]
     *
     * Ocupa la mitad de altura vertical que el layout secuencial original,
     * evitando que la leyenda y las notas se vayan a una segunda página.
     */
    private PdfPTable pdfBuildFooterCombinado(BigDecimal totalNivel, Configuracion config,
                                               PdfLayout layout, ListaEscolar lista,
                                               ItemsSeparados items) throws DocumentException {
        String moneda    = config.getFormatoMoneda() != null ? config.getFormatoMoneda() + " " : "S/ ";
        float  fs        = layout.fontSizeTotal;
        float  pad       = layout.cellPadding;
        float  padTotal  = Math.max(pad + 3f, 6f);
        float  fsCond    = layout.condicionesFontSize;
        float  fsLey     = Math.max(fsCond - 1f, 5f);

        // ── Cálculo IGV ──────────────────────────────────────────────────────────
        BigDecimal igvPct     = config.getIgvPorcentaje() != null ? config.getIgvPorcentaje() : new BigDecimal("18.00");
        boolean    incluyeIgv = Boolean.TRUE.equals(config.getPreciosIncluyenImpuesto());
        String     textoIgv   = incluyeIgv ? "IGV (incluido):" : "IGV (" + igvPct.stripTrailingZeros().toPlainString() + "%):";
        BigDecimal montoIgv   = incluyeIgv
            ? totalNivel.subtract(totalNivel.divide(BigDecimal.ONE.add(igvPct.divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP)), 2, java.math.RoundingMode.HALF_UP))
            : totalNivel.multiply(igvPct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal totalFinal = incluyeIgv ? totalNivel : totalNivel.add(montoIgv);

        // ── Tabla exterior 2 columnas ─────────────────────────────────────────────
        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setWidths(new float[]{42f, 58f});

        // ══ COLUMNA IZQUIERDA: resumen financiero ═══════════════════════════════
        PdfPTable tFin = new PdfPTable(2);
        tFin.setWidths(new float[]{3f, 2.5f});

        tFin.addCell(pdfCeldaResumen("Subtotal:", fs - 1.5f, new Color(80, 80, 80), pad));
        tFin.addCell(pdfCeldaResumen(moneda + String.format("%.2f", totalNivel), fs - 1.5f, new Color(80, 80, 80), pad));

        tFin.addCell(pdfCeldaResumen(textoIgv, fs - 1.5f, new Color(130, 130, 130), pad));
        tFin.addCell(pdfCeldaResumen(moneda + String.format("%.2f", montoIgv), fs - 1.5f, new Color(130, 130, 130), pad));

        PdfPCell sep = new PdfPCell();
        sep.setColspan(2);
        sep.setBorder(Rectangle.TOP);
        sep.setBorderColor(new Color(180, 180, 180));
        sep.setBorderWidth(1f);
        sep.setFixedHeight(3f);
        tFin.addCell(sep);

        Color fondoTotal = new Color(44, 62, 80);
        PdfPCell lblTotal = new PdfPCell(new Phrase("TOTAL:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, fs, Color.WHITE)));
        lblTotal.setBackgroundColor(fondoTotal); lblTotal.setBorder(Rectangle.NO_BORDER);
        lblTotal.setHorizontalAlignment(Element.ALIGN_RIGHT); lblTotal.setVerticalAlignment(Element.ALIGN_MIDDLE);
        lblTotal.setPadding(padTotal);
        tFin.addCell(lblTotal);

        PdfPCell valTotal = new PdfPCell(new Phrase(moneda + String.format("%.2f", totalFinal), FontFactory.getFont(FontFactory.HELVETICA_BOLD, fs, Color.WHITE)));
        valTotal.setBackgroundColor(fondoTotal); valTotal.setBorder(Rectangle.NO_BORDER);
        valTotal.setHorizontalAlignment(Element.ALIGN_RIGHT); valTotal.setVerticalAlignment(Element.ALIGN_MIDDLE);
        valTotal.setPadding(padTotal);
        tFin.addCell(valTotal);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPaddingRight(8f);
        leftCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        leftCell.addElement(tFin);
        outer.addCell(leftCell);

        // ══ COLUMNA DERECHA: condiciones + nota pendientes + comentarios + leyenda ══
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.LEFT);
        rightCell.setBorderColor(new Color(210, 210, 210));
        rightCell.setBorderWidth(0.5f);
        rightCell.setPaddingLeft(8f);
        rightCell.setPaddingTop(2f);
        rightCell.setPaddingBottom(2f);
        rightCell.setVerticalAlignment(Element.ALIGN_TOP);

        // Título condiciones
        Paragraph titCond = new Paragraph("CONDICIONES COMERCIALES",
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, fsCond + 0.5f, new Color(44, 62, 80)));
        titCond.setSpacingAfter(1.5f);
        rightCell.addElement(titCond);

        // Bullet points condensados en 2 líneas
        String mon       = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";
        String igvTextoC = Boolean.TRUE.equals(config.getPreciosIncluyenImpuesto()) ? " con IGV" : "";
        String cond1 = "\u2022 Validez: 7 días.  \u2022 Pago: Contado.  \u2022 Precios en " + mon + igvTextoC + ".";
        String cond2 = "\u2022 Sujeto a disponibilidad de stock.  \u2022 No incluye productos pendientes.";
        Paragraph pCond = new Paragraph(cond1 + "\n" + cond2,
            FontFactory.getFont(FontFactory.HELVETICA, fsCond, new Color(110, 110, 110)));
        pCond.setLeading(fsCond * 1.5f);
        rightCell.addElement(pCond);

        // Nota de items no disponibles (si los hay) — antes vivía en el body
        if (!items.noDisponibles.isEmpty()) {
            Paragraph notaDisp = new Paragraph(
                "\u2139 " + items.noDisponibles.size() + " item(s) sin asignar / no disponible(s).",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, Math.max(fsCond - 0.5f, 5f), new Color(160, 160, 160)));
            notaDisp.setSpacingBefore(2f);
            rightCell.addElement(notaDisp);
        }

        // Comentarios / Notas de la lista (antes vivían en el body)
        if (lista.getComentarios() != null && !lista.getComentarios().isBlank()) {
            Paragraph notaTit = new Paragraph("Notas:",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, fsCond, new Color(70, 70, 70)));
            notaTit.setSpacingBefore(2f);
            rightCell.addElement(notaTit);
            Paragraph notaTxt = new Paragraph(lista.getComentarios(),
                FontFactory.getFont(FontFactory.HELVETICA, Math.max(fsCond - 0.5f, 5f), new Color(80, 80, 80)));
            rightCell.addElement(notaTxt);
        }

        // Leyenda [■] Vendido / [□] Pendiente de venta
        Paragraph leyenda = new Paragraph("\u25a0 Vendido    \u25a1 Pendiente de venta",
            FontFactory.getFont(FontFactory.HELVETICA, fsLey, new Color(190, 190, 190)));
        leyenda.setAlignment(Element.ALIGN_RIGHT);
        leyenda.setSpacingBefore(3f);
        rightCell.addElement(leyenda);

        outer.addCell(rightCell);
        return outer;
    }

    // =========================================================
    //  PDF — CELDAS PARAMETRIZADAS (diseño limpio sin bordes)
    // =========================================================

    /** Celda limpia sin bordes — diseño profesional con fondo alternado */
    private PdfPCell pdfCeldaLimpia(String texto, float fontSize, float padding,
                                     Color bgColor, Color textColor, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "",
            FontFactory.getFont(FontFactory.HELVETICA, fontSize, textColor)));
        cell.setPadding(padding);
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(align);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private PdfPCell pdfCelda(String texto, float fontSize, float padding) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "",
            FontFactory.getFont(FontFactory.HELVETICA, fontSize)));
        cell.setPadding(padding);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private PdfPCell pdfCeldaCentrada(String texto, float fontSize, float padding) {
        PdfPCell cell = pdfCelda(texto, fontSize, padding);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private PdfPCell pdfCeldaDerecha(String texto, float fontSize, float padding) {
        PdfPCell cell = pdfCelda(texto, fontSize, padding);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private PdfPCell pdfCeldaColor(String texto, Color color, float fontSize, float padding) {
        PdfPCell cell = new PdfPCell(new Phrase(texto,
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, fontSize, color)));
        cell.setPadding(padding);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private PdfPCell pdfCeldaLabel(String texto, Color bgColor, float fontSize, float padding) {
        PdfPCell cell = new PdfPCell(new Phrase(texto,
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, fontSize)));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(padding);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private PdfPCell pdfCeldaValor(String texto, float fontSize, float padding) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "",
            FontFactory.getFont(FontFactory.HELVETICA, fontSize)));
        cell.setPadding(padding);
        cell.setBorder(Rectangle.NO_BORDER);
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
     * API: Elimina productos faltantes (items sin producto asignado) por texto.
     */
    @PostMapping("/api/faltantes/eliminar")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> eliminarFaltantes(@RequestBody Map<String, Object> datos) {
        try {
            @SuppressWarnings("unchecked")
            List<String> textos = (List<String>) datos.get("textos");
            if (textos == null || textos.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Debe especificar productos a eliminar"));
            }

            int eliminados = listaService.eliminarItemsFaltantes(textos);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "eliminados", eliminados
            ));
        } catch (Exception e) {
            log.error("Error al eliminar faltantes: {}", e.getMessage());
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

    /**
     * Exportar productos faltantes a PDF.
     */
    @GetMapping("/faltantes/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    public void exportarFaltantesPDF(jakarta.servlet.http.HttpServletResponse response) throws IOException, DocumentException {
        List<ProductoFaltanteDTO> faltantes = listaService.obtenerProductosFaltantes();

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=\"Productos_Faltantes.pdf\"");

        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.DARK_GRAY);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);

        // Titulo
        Paragraph title = new Paragraph("PRODUCTOS FALTANTES - LISTAS ESCOLARES", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(5);
        document.add(title);

        Paragraph fecha = new Paragraph("Generado: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), subtitleFont);
        fecha.setAlignment(Element.ALIGN_CENTER);
        fecha.setSpacingAfter(15);
        document.add(fecha);

        // Tabla
        PdfPTable table = new PdfPTable(new float[]{10f, 55f, 15f, 20f});
        table.setWidthPercentage(100);

        // Headers
        String[] headers = {"#", "Producto Solicitado", "Veces", "Cant. Total"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new Color(52, 58, 64));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6);
            table.addCell(cell);
        }

        // Datos ordenados por cantidad desc
        faltantes.sort((a, b) -> Integer.compare(b.getCantidadTotal(), a.getCantidadTotal()));

        int num = 0;
        int totalUnidades = 0;
        for (ProductoFaltanteDTO f : faltantes) {
            num++;
            totalUnidades += f.getCantidadTotal();
            Color bgColor = num % 2 == 0 ? new Color(248, 249, 250) : Color.WHITE;

            PdfPCell c1 = new PdfPCell(new Phrase(String.valueOf(num), cellFont));
            c1.setHorizontalAlignment(Element.ALIGN_CENTER);
            c1.setBackgroundColor(bgColor);
            c1.setPadding(4);
            table.addCell(c1);

            PdfPCell c2 = new PdfPCell(new Phrase(f.getTextoOriginal(), cellFont));
            c2.setBackgroundColor(bgColor);
            c2.setPadding(4);
            table.addCell(c2);

            PdfPCell c3 = new PdfPCell(new Phrase(String.valueOf(f.getVecessolicitado()), cellFont));
            c3.setHorizontalAlignment(Element.ALIGN_CENTER);
            c3.setBackgroundColor(bgColor);
            c3.setPadding(4);
            table.addCell(c3);

            PdfPCell c4 = new PdfPCell(new Phrase(String.valueOf(f.getCantidadTotal()), cellFont));
            c4.setHorizontalAlignment(Element.ALIGN_CENTER);
            c4.setBackgroundColor(bgColor);
            c4.setPadding(4);
            table.addCell(c4);
        }

        // Fila total
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL", totalFont));
        totalLabel.setColspan(3);
        totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalLabel.setBackgroundColor(new Color(255, 243, 205));
        totalLabel.setPadding(6);
        table.addCell(totalLabel);

        PdfPCell totalVal = new PdfPCell(new Phrase(String.valueOf(totalUnidades), totalFont));
        totalVal.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalVal.setBackgroundColor(new Color(255, 243, 205));
        totalVal.setPadding(6);
        table.addCell(totalVal);

        document.add(table);

        Paragraph resumen = new Paragraph(
            String.format("%d productos diferentes | %d unidades totales solicitadas", faltantes.size(), totalUnidades),
            subtitleFont
        );
        resumen.setAlignment(Element.ALIGN_CENTER);
        resumen.setSpacingBefore(10);
        document.add(resumen);

        document.close();
    }

    /**
     * Exportar productos faltantes a Excel.
     */
    @GetMapping("/faltantes/excel")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    public void exportarFaltantesExcel(jakarta.servlet.http.HttpServletResponse response) throws IOException {
        List<ProductoFaltanteDTO> faltantes = listaService.obtenerProductosFaltantes();
        faltantes.sort((a, b) -> Integer.compare(b.getCantidadTotal(), a.getCantidadTotal()));

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"Productos_Faltantes.xlsx\"");

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet("Productos Faltantes");

            // Estilo header
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            // Header row
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            String[] cols = {"#", "Producto Solicitado", "Veces Solicitado", "Cantidad Total"};
            for (int i = 0; i < cols.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            // Datos
            int rowNum = 1;
            for (ProductoFaltanteDTO f : faltantes) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(f.getTextoOriginal());
                row.createCell(2).setCellValue(f.getVecessolicitado());
                row.createCell(3).setCellValue(f.getCantidadTotal());
                rowNum++;
            }

            // Auto-size columns
            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(response.getOutputStream());
        }
    }

    // =========================================================
    //  EDITAR TEXTO ORIGINAL Y REPROCESAR
    // =========================================================

    /**
     * Actualiza el texto original de la lista y sincroniza los detalles dinámicamente.
     * Retorna los IDs eliminados y los nuevos ítems para actualización del frontend sin reload.
     */
    @PutMapping("/api/{id}/texto-original")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    @ResponseBody
    public ResponseEntity<?> actualizarTextoOriginal(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            String nuevoTexto = body.get("textoOriginal");
            if (nuevoTexto == null || nuevoTexto.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "El texto no puede estar vacío"));
            }
            Map<String, Object> resultado = listaService.actualizarTextoYReprocesar(id, nuevoTexto);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            log.error("Error al actualizar texto original de lista {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================
    //  ELIMINACIÓN LÓGICA DE LISTA
    // =========================================================

    /**
     * Elimina lógicamente una lista (soft delete: estado = ELIMINADA).
     * Solo ADMIN. Solo si la lista no tiene ventas.
     */
    @DeleteMapping("/api/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> eliminarLista(@PathVariable Long id) {
        try {
            listaService.eliminarLista(id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "mensaje", "Lista eliminada exitosamente"));
        } catch (Exception e) {
            log.error("Error al eliminar lista {}: {}", id, e.getMessage(), e);
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
