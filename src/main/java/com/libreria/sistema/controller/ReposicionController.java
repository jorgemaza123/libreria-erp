package com.libreria.sistema.controller;

import com.libreria.sistema.service.ReposicionPendienteService;
import com.lowagie.text.DocumentException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Controller
@RequestMapping("/reposicion")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
@Slf4j
public class ReposicionController {

    private final ReposicionPendienteService reposicionPendienteService;

    public ReposicionController(ReposicionPendienteService reposicionPendienteService) {
        this.reposicionPendienteService = reposicionPendienteService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate inicio,
                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fin,
                        Model model) {
        if (inicio == null || fin == null) {
            YearMonth mes = YearMonth.now();
            inicio = mes.atDay(1);
            fin = mes.atEndOfMonth();
        }
        model.addAttribute("inicio", inicio);
        model.addAttribute("fin", fin);
        model.addAttribute("inicioFmt", inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("finFmt", fin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("resumen", reposicionPendienteService.resumen(inicio, fin));
        model.addAttribute("pendientes", reposicionPendienteService.listarPendientes());
        model.addAttribute("ventasProducto", reposicionPendienteService.ventasPorProducto(inicio, fin));
        return "reposicion/index";
    }

    @GetMapping("/api/pendientes")
    @ResponseBody
    public ResponseEntity<?> pendientes() {
        return ResponseEntity.ok(reposicionPendienteService.listarPendientes());
    }

    @PostMapping("/api/{id}/actualizar")
    @ResponseBody
    public ResponseEntity<?> actualizar(@PathVariable Long id,
                                        @RequestBody Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(reposicionPendienteService.actualizarManual(
                    id,
                    decimal(payload.get("cantidadPendiente")),
                    decimal(payload.get("montoReposicionPendiente")),
                    texto(payload.get("prioridad")),
                    texto(payload.get("notas"))
            ));
        } catch (Exception e) {
            log.error("Error actualizando reposicion {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/{id}/marcar-repuesto")
    @ResponseBody
    public ResponseEntity<?> marcarRepuesto(@PathVariable Long id) {
        try {
            reposicionPendienteService.marcarRepuesto(id);
            return ResponseEntity.ok(Map.of("message", "Producto marcado como repuesto"));
        } catch (Exception e) {
            log.error("Error marcando reposicion como repuesta {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/exportar/excel")
    public ResponseEntity<byte[]> exportarExcel() throws IOException {
        byte[] bytes = reposicionPendienteService.exportarPendientesExcel(reposicionPendienteService.listarPendientes());
        String filename = "reposicion_pendiente_" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/exportar/pdf")
    public void exportarPdf(HttpServletResponse response) throws IOException, DocumentException {
        String filename = "reposicion_pendiente_" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        reposicionPendienteService.exportarPendientesPdf(reposicionPendienteService.listarPendientes(), response.getOutputStream());
    }

    private String texto(Object value) {
        return value != null ? value.toString() : null;
    }

    private BigDecimal decimal(Object value) {
        if (value == null || value.toString().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
