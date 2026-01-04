package com.libreria.sistema.controller;

import com.libreria.sistema.model.DevolucionVenta;
import com.libreria.sistema.model.dto.DevolucionDTO;
import com.libreria.sistema.service.DevolucionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/devoluciones")
@Slf4j
public class DevolucionController {

    @Autowired
    private DevolucionService devolucionService;

    /**
     * Vista principal de devoluciones
     */
    @GetMapping
    @PreAuthorize("hasPermission(null, 'DEVOLUCIONES_VER')")
    public String index(Model model) {
        model.addAttribute("titulo", "Gestión de Devoluciones");
        return "devoluciones/index";
    }

    /**
     * Vista de formulario de nueva devolución
     */
    @GetMapping("/nueva/{ventaId}")
    @PreAuthorize("hasPermission(null, 'DEVOLUCIONES_CREAR')")
    public String nuevaDevolucion(@PathVariable Long ventaId, Model model) {
        model.addAttribute("ventaId", ventaId);
        model.addAttribute("titulo", "Nueva Devolución");
        return "devoluciones/nueva";
    }

    /**
     * Procesar devolución
     */
    @PostMapping("/api/procesar")
    @PreAuthorize("hasPermission(null, 'DEVOLUCIONES_CREAR')")
    @ResponseBody
    public ResponseEntity<?> procesarDevolucion(@RequestBody DevolucionDTO dto) {
        try {
            Map<String, Object> resultado = devolucionService.procesarDevolucion(dto);
            log.info("Devolución procesada: NC {}-{}", resultado.get("serie"), resultado.get("numero"));
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            log.error("Error al procesar devolución: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Buscar devoluciones con filtros
     */
    @GetMapping("/api/buscar")
    @PreAuthorize("hasPermission(null, 'DEVOLUCIONES_VER')")
    @ResponseBody
    public ResponseEntity<Page<DevolucionVenta>> buscar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "fechaCreacion") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir
    ) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC")
                    ? Sort.by(sortBy).ascending()
                    : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<DevolucionVenta> resultado = devolucionService.buscarConFiltros(
                    estado, fechaInicio, fechaFin, pageable
            );

            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            log.error("Error al buscar devoluciones: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Obtener detalle de devolución
     */
    @GetMapping("/api/{id}")
    @PreAuthorize("hasPermission(null, 'DEVOLUCIONES_VER')")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalle(@PathVariable Long id) {
        try {
            DevolucionVenta devolucion = devolucionService.obtenerPorId(id);
            return ResponseEntity.ok(devolucion);
        } catch (Exception e) {
            log.error("Error al obtener devolución: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Anular devolución (solo ADMIN)
     */
    @PostMapping("/api/anular/{id}")
    @PreAuthorize("hasPermission(null, 'DEVOLUCIONES_ANULAR')")
    @ResponseBody
    public ResponseEntity<?> anularDevolucion(@PathVariable Long id) {
        try {
            devolucionService.anularDevolucion(id);
            log.info("Devolución {} anulada", id);
            return ResponseEntity.ok(Map.of("mensaje", "Devolución anulada correctamente"));
        } catch (Exception e) {
            log.error("Error al anular devolución: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reenviar nota de crédito a SUNAT
     */
    @PostMapping("/api/reenviar-sunat/{id}")
    @PreAuthorize("hasPermission(null, 'DEVOLUCIONES_CREAR')")
    @ResponseBody
    public ResponseEntity<?> reenviarSunat(@PathVariable Long id) {
        try {
            String estadoSunat = devolucionService.enviarNotaCreditoSunat(id);
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Nota de crédito enviada a SUNAT",
                    "estadoSunat", estadoSunat
            ));
        } catch (Exception e) {
            log.error("Error al reenviar a SUNAT: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obtener devoluciones de una venta específica
     */
    @GetMapping("/api/por-venta/{ventaId}")
    @PreAuthorize("hasPermission(null, 'DEVOLUCIONES_VER')")
    @ResponseBody
    public ResponseEntity<?> obtenerPorVenta(@PathVariable Long ventaId) {
        try {
            return ResponseEntity.ok(devolucionService.obtenerDevolucionesPorVenta(ventaId));
        } catch (Exception e) {
            log.error("Error al obtener devoluciones por venta: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Validar si una venta puede ser devuelta
     */
    @PostMapping("/api/validar")
    @PreAuthorize("hasPermission(null, 'DEVOLUCIONES_VER')")
    @ResponseBody
    public ResponseEntity<?> validarDevolucion(@RequestBody DevolucionDTO dto) {
        try {
            devolucionService.validarDevolucion(dto);
            return ResponseEntity.ok(Map.of("valido", true, "mensaje", "La devolución puede procesarse"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("valido", false, "mensaje", e.getMessage()));
        }
    }
}
