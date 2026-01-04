package com.libreria.sistema.controller;

import com.libreria.sistema.service.ReporteFinancieroService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reportes/financieros")
@Slf4j
public class ReporteFinancieroController {

    @Autowired
    private ReporteFinancieroService reporteService;

    /**
     * Vista principal del dashboard financiero
     */
    @GetMapping
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public String dashboard(Model model) {
        model.addAttribute("titulo", "Dashboard Financiero");
        return "reportes-financieros/dashboard";
    }

    /**
     * Vista de flujo de caja
     */
    @GetMapping("/flujo-caja")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public String flujoCaja(Model model) {
        model.addAttribute("titulo", "Flujo de Caja");
        return "reportes-financieros/flujo-caja";
    }

    /**
     * Vista de rentabilidad
     */
    @GetMapping("/rentabilidad")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public String rentabilidad(Model model) {
        model.addAttribute("titulo", "Análisis de Rentabilidad");
        return "reportes-financieros/rentabilidad";
    }

    /**
     * Vista de análisis de ventas
     */
    @GetMapping("/analisis-ventas")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public String analisisVentas(Model model) {
        model.addAttribute("titulo", "Análisis de Ventas");
        return "reportes-financieros/analisis-ventas";
    }

    // ==================== ENDPOINTS API ====================

    /**
     * Obtener datos del dashboard
     */
    @GetMapping("/api/dashboard")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDashboardData() {
        try {
            Map<String, Object> dashboard = reporteService.generarDashboardFinanciero();
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            log.error("Error generando dashboard: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obtener datos de flujo de caja
     */
    @GetMapping("/api/flujo-caja")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFlujoCaja(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin
    ) {
        try {
            Map<String, Object> flujo = reporteService.generarFlujoCaja(fechaInicio, fechaFin);
            return ResponseEntity.ok(flujo);
        } catch (Exception e) {
            log.error("Error generando flujo de caja: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obtener datos de rentabilidad
     */
    @GetMapping("/api/rentabilidad")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    @ResponseBody
    public ResponseEntity<?> getRentabilidad(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin
    ) {
        try {
            List<Map<String, Object>> rentabilidad = reporteService.generarRentabilidadProductos(fechaInicio, fechaFin);
            return ResponseEntity.ok(rentabilidad);
        } catch (Exception e) {
            log.error("Error generando rentabilidad: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obtener datos de análisis de ventas
     */
    @GetMapping("/api/analisis-ventas")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAnalisisVentas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin
    ) {
        try {
            Map<String, Object> analisis = reporteService.generarAnalisisVentas(fechaInicio, fechaFin);
            return ResponseEntity.ok(analisis);
        } catch (Exception e) {
            log.error("Error generando análisis de ventas: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== EXPORTACIONES ====================

    /**
     * Exportar flujo de caja a Excel
     */
    @GetMapping("/api/flujo-caja/excel")
    @PreAuthorize("hasPermission(null, 'REPORTES_EXPORTAR')")
    public void exportarFlujoCajaExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            HttpServletResponse response
    ) {
        try {
            reporteService.exportarFlujoCajaExcel(fechaInicio, fechaFin, response);
        } catch (IOException e) {
            log.error("Error exportando flujo de caja a Excel: {}", e.getMessage(), e);
        }
    }

    /**
     * Exportar flujo de caja a PDF
     */
    @GetMapping("/api/flujo-caja/pdf")
    @PreAuthorize("hasPermission(null, 'REPORTES_EXPORTAR')")
    public void exportarFlujoCajaPDF(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            HttpServletResponse response
    ) {
        try {
            reporteService.exportarFlujoCajaPDF(fechaInicio, fechaFin, response);
        } catch (IOException e) {
            log.error("Error exportando flujo de caja a PDF: {}", e.getMessage(), e);
        }
    }

    /**
     * Exportar rentabilidad a Excel
     */
    @GetMapping("/api/rentabilidad/excel")
    @PreAuthorize("hasPermission(null, 'REPORTES_EXPORTAR')")
    public void exportarRentabilidadExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            HttpServletResponse response
    ) {
        try {
            reporteService.exportarRentabilidadExcel(fechaInicio, fechaFin, response);
        } catch (IOException e) {
            log.error("Error exportando rentabilidad a Excel: {}", e.getMessage(), e);
        }
    }

    /**
     * Exportar rentabilidad a PDF
     */
    @GetMapping("/api/rentabilidad/pdf")
    @PreAuthorize("hasPermission(null, 'REPORTES_EXPORTAR')")
    public void exportarRentabilidadPDF(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            HttpServletResponse response
    ) {
        try {
            reporteService.exportarRentabilidadPDF(fechaInicio, fechaFin, response);
        } catch (IOException e) {
            log.error("Error exportando rentabilidad a PDF: {}", e.getMessage(), e);
        }
    }
}
