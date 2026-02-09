package com.libreria.sistema.controller;

import com.libreria.sistema.model.DetalleTomaInventario;
import com.libreria.sistema.model.TomaInventario;
import com.libreria.sistema.service.TomaInventarioService;
import com.libreria.sistema.service.TomaInventarioService.ConteoDTO;
import com.libreria.sistema.service.TomaInventarioService.EstadisticasToma;
import com.libreria.sistema.service.TomaInventarioService.ResultadoProcesamiento;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para el módulo de Toma de Inventario Físico.
 */
@Controller
@RequestMapping("/toma-inventario")
@PreAuthorize("hasPermission(null, 'TOMA_INVENTARIO_VER') or hasPermission(null, 'INVENTARIO_EDITAR')")
@Slf4j
public class TomaInventarioController {

    private final TomaInventarioService tomaService;

    public TomaInventarioController(TomaInventarioService tomaService) {
        this.tomaService = tomaService;
    }

    // ========== VISTAS ==========

    /**
     * Lista de todas las tomas de inventario (historial).
     */
    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<TomaInventario> tomas = tomaService.listarTomas(
                PageRequest.of(page, 15, Sort.by("fechaInicio").descending()));

        model.addAttribute("tomas", tomas.getContent());
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

        // Obtener solo detalles con diferencia
        List<DetalleTomaInventario> detalles = tomaService.listarDetalles(id);
        List<DetalleTomaInventario> conDiferencia = detalles.stream()
                .filter(d -> d.getDiferencia() != null && d.getDiferencia() != 0)
                .toList();

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
}
