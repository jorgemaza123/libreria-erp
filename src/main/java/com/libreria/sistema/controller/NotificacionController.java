package com.libreria.sistema.controller;

import com.libreria.sistema.model.Notificacion;
import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.service.NotificacionService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador para el sistema de notificaciones.
 * Proporciona endpoints para la campana de notificaciones y gestión.
 */
@Controller
@RequestMapping("/notificaciones")
@Slf4j
public class NotificacionController {

    private final NotificacionService notificacionService;
    private final ConfiguracionService configuracionService;

    public NotificacionController(NotificacionService notificacionService,
                                   ConfiguracionService configuracionService) {
        this.notificacionService = notificacionService;
        this.configuracionService = configuracionService;
    }

    /**
     * Vista de todas las notificaciones
     */
    @GetMapping
    public String index(Model model, @RequestParam(defaultValue = "0") int page) {
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        model.addAttribute("active", "notificaciones");

        Page<Notificacion> notificaciones = notificacionService.listarTodas(
                PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "fechaCreacion")));

        model.addAttribute("notificaciones", notificaciones);
        model.addAttribute("stats", notificacionService.obtenerEstadisticas());

        return "notificaciones/index";
    }

    /**
     * API: Obtener últimas notificaciones para el dropdown
     */
    @GetMapping("/api/ultimas")
    @ResponseBody
    public ResponseEntity<?> obtenerUltimas(@RequestParam(defaultValue = "8") int limite) {
        List<Notificacion> notificaciones = notificacionService.obtenerUltimas(limite);
        long noLeidas = notificacionService.contarNoLeidas();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");

        List<Map<String, Object>> lista = notificaciones.stream().map(n -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", n.getId());
            item.put("tipo", n.getTipo());
            item.put("titulo", n.getTitulo());
            item.put("mensaje", n.getMensaje());
            item.put("icono", n.getIcono());
            item.put("color", n.getColorIcono());
            item.put("url", n.getUrlAccion());
            item.put("leida", n.getLeida());
            item.put("resuelta", n.getResuelta());
            item.put("prioridad", n.getPrioridad());
            item.put("fecha", n.getFechaCreacion().format(formatter));
            item.put("tiempoRelativo", calcularTiempoRelativo(n.getFechaCreacion()));
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("notificaciones", lista);
        response.put("noLeidas", noLeidas);

        return ResponseEntity.ok(response);
    }

    /**
     * API: Contar notificaciones no leídas
     */
    @GetMapping("/api/contar")
    @ResponseBody
    public ResponseEntity<?> contarNoLeidas() {
        return ResponseEntity.ok(Map.of("count", notificacionService.contarNoLeidas()));
    }

    /**
     * API: Marcar una notificación como leída
     */
    @PostMapping("/api/leer/{id}")
    @ResponseBody
    public ResponseEntity<?> marcarComoLeida(@PathVariable Long id) {
        try {
            notificacionService.marcarComoLeida(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * API: Marcar todas como leídas
     */
    @PostMapping("/api/leer-todas")
    @ResponseBody
    public ResponseEntity<?> marcarTodasComoLeidas() {
        try {
            int actualizadas = notificacionService.marcarTodasComoLeidas();
            return ResponseEntity.ok(Map.of("success", true, "actualizadas", actualizadas));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * API: Marcar como resuelta
     */
    @PostMapping("/api/resolver/{id}")
    @ResponseBody
    public ResponseEntity<?> marcarComoResuelta(@PathVariable Long id) {
        try {
            notificacionService.marcarComoResuelta(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * API: Eliminar notificación
     */
    @DeleteMapping("/api/eliminar/{id}")
    @ResponseBody
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        try {
            notificacionService.eliminar(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * API: Obtener estadísticas
     */
    @GetMapping("/api/estadisticas")
    @ResponseBody
    public ResponseEntity<?> obtenerEstadisticas() {
        return ResponseEntity.ok(notificacionService.obtenerEstadisticas());
    }

    /**
     * API: Forzar verificación de stock (para testing)
     */
    @PostMapping("/api/verificar-stock")
    @ResponseBody
    public ResponseEntity<?> verificarStock() {
        try {
            notificacionService.verificarStockBajo();
            return ResponseEntity.ok(Map.of("success", true, "mensaje", "Verificación completada"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Calcula tiempo relativo (hace 5 min, hace 2 horas, etc.)
     */
    private String calcularTiempoRelativo(java.time.LocalDateTime fecha) {
        java.time.Duration duracion = java.time.Duration.between(fecha, java.time.LocalDateTime.now());

        if (duracion.toMinutes() < 1) return "Ahora";
        if (duracion.toMinutes() < 60) return "Hace " + duracion.toMinutes() + " min";
        if (duracion.toHours() < 24) return "Hace " + duracion.toHours() + " h";
        if (duracion.toDays() < 7) return "Hace " + duracion.toDays() + " días";
        return fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
