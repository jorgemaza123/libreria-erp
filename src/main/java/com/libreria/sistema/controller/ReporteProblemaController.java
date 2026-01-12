package com.libreria.sistema.controller;

import com.libreria.sistema.model.ReporteProblema;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.service.ReporteProblemaService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para gestionar reportes de problemas/incidencias en la tienda.
 */
@Controller
@RequestMapping("/incidencias")
@Slf4j
@PreAuthorize("hasPermission(null, 'INCIDENCIAS_VER')")
public class ReporteProblemaController {

    private final ReporteProblemaService reporteService;
    private final ProductoRepository productoRepository;
    private final ConfiguracionService configuracionService;

    public ReporteProblemaController(ReporteProblemaService reporteService,
                                      ProductoRepository productoRepository,
                                      ConfiguracionService configuracionService) {
        this.reporteService = reporteService;
        this.productoRepository = productoRepository;
        this.configuracionService = configuracionService;
    }

    /**
     * Vista principal de incidencias
     */
    @GetMapping
    public String index(Model model,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "TODOS") String filtroEstado) {

        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        model.addAttribute("active", "incidencias");

        // Estadísticas
        model.addAttribute("stats", reporteService.obtenerEstadisticas());

        // Listado según filtro
        if ("TODOS".equals(filtroEstado)) {
            Page<ReporteProblema> reportes = reporteService.listarTodos(
                    PageRequest.of(page, 15, Sort.by(Sort.Direction.DESC, "fechaReporte")));
            model.addAttribute("reportes", reportes);
        } else {
            List<ReporteProblema> reportes = reporteService.listarPorEstado(filtroEstado);
            model.addAttribute("reportesLista", reportes);
        }

        model.addAttribute("filtroEstado", filtroEstado);
        model.addAttribute("tiposProblema", reporteService.obtenerTiposProblema());
        model.addAttribute("nivelesUrgencia", reporteService.obtenerNivelesUrgencia());
        model.addAttribute("acciones", reporteService.obtenerAcciones());
        model.addAttribute("productos", productoRepository.findByActivoTrue());

        return "incidencias/index";
    }

    /**
     * Vista de mis reportes (para vendedores)
     */
    @GetMapping("/mis-reportes")
    public String misReportes(Model model) {
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        model.addAttribute("active", "incidencias");
        model.addAttribute("reportes", reporteService.listarMisReportes());
        model.addAttribute("tiposProblema", reporteService.obtenerTiposProblema());
        model.addAttribute("nivelesUrgencia", reporteService.obtenerNivelesUrgencia());
        model.addAttribute("productos", productoRepository.findByActivoTrue());

        return "incidencias/mis-reportes";
    }

    /**
     * API: Crear nuevo reporte
     */
    @PostMapping("/api/crear")
    @ResponseBody
    public ResponseEntity<?> crearReporte(@RequestBody Map<String, Object> datos) {
        try {
            String tipoProblema = (String) datos.get("tipoProblema");
            String titulo = (String) datos.get("titulo");
            String descripcion = (String) datos.get("descripcion");
            Long productoId = datos.get("productoId") != null ?
                    Long.parseLong(datos.get("productoId").toString()) : null;
            String ubicacion = (String) datos.get("ubicacion");
            String urgencia = (String) datos.get("urgencia");
            Integer cantidadAfectada = datos.get("cantidadAfectada") != null ?
                    Integer.parseInt(datos.get("cantidadAfectada").toString()) : null;
            String imagenBase64 = (String) datos.get("imagenBase64");

            ReporteProblema reporte = reporteService.crearReporte(
                    tipoProblema, titulo, descripcion, productoId,
                    ubicacion, urgencia, cantidadAfectada, imagenBase64);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", reporte.getId());
            response.put("mensaje", "Reporte creado exitosamente");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error al crear reporte", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * API: Actualizar estado de reporte (solo admin/supervisor)
     */
    @PostMapping("/api/actualizar-estado")
    @ResponseBody
    @PreAuthorize("hasPermission(null, 'INCIDENCIAS_EDITAR')")
    public ResponseEntity<?> actualizarEstado(@RequestBody Map<String, Object> datos) {
        try {
            Long id = Long.parseLong(datos.get("id").toString());
            String nuevoEstado = (String) datos.get("estado");
            String comentario = (String) datos.get("comentario");
            String accionTomada = (String) datos.get("accionTomada");

            ReporteProblema reporte = reporteService.actualizarEstado(id, nuevoEstado, comentario, accionTomada);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mensaje", "Estado actualizado correctamente",
                    "nuevoEstado", reporte.getEstado()
            ));

        } catch (Exception e) {
            log.error("Error al actualizar estado", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * API: Obtener detalle de un reporte
     */
    @GetMapping("/api/detalle/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalle(@PathVariable Long id) {
        return reporteService.obtenerPorId(id)
                .map(reporte -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", reporte.getId());
                    data.put("tipoProblema", reporte.getTipoProblema());
                    data.put("titulo", reporte.getTitulo());
                    data.put("descripcion", reporte.getDescripcion());
                    data.put("ubicacion", reporte.getUbicacion());
                    data.put("urgencia", reporte.getUrgencia());
                    data.put("estado", reporte.getEstado());
                    data.put("fechaReporte", reporte.getFechaReporte().toString());
                    data.put("cantidadAfectada", reporte.getCantidadAfectada());
                    data.put("imagenBase64", reporte.getImagenBase64());
                    data.put("comentarioResolucion", reporte.getComentarioResolucion());
                    data.put("accionTomada", reporte.getAccionTomada());

                    if (reporte.getProducto() != null) {
                        data.put("productoNombre", reporte.getProducto().getNombre());
                        data.put("productoCodigo", reporte.getProducto().getCodigoInterno());
                    }
                    if (reporte.getUsuarioReporta() != null) {
                        data.put("usuarioReporta", reporte.getUsuarioReporta().getNombreCompleto());
                    }
                    if (reporte.getUsuarioAtiende() != null) {
                        data.put("usuarioAtiende", reporte.getUsuarioAtiende().getNombreCompleto());
                    }
                    if (reporte.getFechaResolucion() != null) {
                        data.put("fechaResolucion", reporte.getFechaResolucion().toString());
                    }

                    return ResponseEntity.ok(data);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * API: Obtener estadísticas
     */
    @GetMapping("/api/estadisticas")
    @ResponseBody
    public ResponseEntity<?> obtenerEstadisticas() {
        return ResponseEntity.ok(reporteService.obtenerEstadisticas());
    }

    /**
     * API: Eliminar reporte (solo admin)
     */
    @DeleteMapping("/api/eliminar/{id}")
    @ResponseBody
    @PreAuthorize("hasPermission(null, 'INCIDENCIAS_EDITAR')")
    public ResponseEntity<?> eliminarReporte(@PathVariable Long id) {
        try {
            reporteService.eliminar(id);
            return ResponseEntity.ok(Map.of("success", true, "mensaje", "Reporte eliminado"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Vista de reportes pendientes urgentes (para dashboard admin)
     */
    @GetMapping("/pendientes")
    @PreAuthorize("hasPermission(null, 'INCIDENCIAS_EDITAR')")
    public String pendientesUrgentes(Model model) {
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        model.addAttribute("active", "incidencias");
        model.addAttribute("reportes", reporteService.listarPendientesUrgentes());
        model.addAttribute("tiposProblema", reporteService.obtenerTiposProblema());
        model.addAttribute("acciones", reporteService.obtenerAcciones());

        return "incidencias/pendientes";
    }
}
