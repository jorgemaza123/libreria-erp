package com.libreria.sistema.controller;

import com.libreria.sistema.service.ReporteService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;

@Controller
@RequestMapping("/reportes")
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @GetMapping
    public String index() {
        return "reportes/index";
    }

    @GetMapping("/exportar/excel")
    public void exportarExcel(@RequestParam String tipo,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
                              HttpServletResponse response) throws IOException {

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=Reporte_" + tipo + "_" + LocalDate.now() + ".xlsx";
        response.setHeader(headerKey, headerValue);

        reporteService.generarExcel(tipo, inicio, fin, response.getOutputStream());
    }

    @GetMapping("/exportar/pdf")
    public void exportarPdf(@RequestParam String tipo,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
                            HttpServletResponse response) throws IOException {

        try {
            response.setContentType("application/pdf");
            String headerKey = "Content-Disposition";
            String headerValue = "inline; filename=Reporte_" + tipo + "_" + LocalDate.now() + ".pdf";
            response.setHeader(headerKey, headerValue);

            reporteService.generarPdf(tipo, inicio, fin, response.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(500, "Error al generar PDF: " + e.getMessage());
        }
    }
}