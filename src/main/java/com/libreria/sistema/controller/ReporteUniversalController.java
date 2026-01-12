package com.libreria.sistema.controller;

import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.service.ReporteUniversalService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Controlador para el módulo de Reportes Universales Avanzados.
 * Proporciona endpoints para generar y descargar reportes en Excel y PDF.
 */
@Controller
@RequestMapping("/reportes/avanzados")
@Slf4j
public class ReporteUniversalController {

    private final ReporteUniversalService reporteService;
    private final ConfiguracionService configuracionService;

    public ReporteUniversalController(ReporteUniversalService reporteService,
                                       ConfiguracionService configuracionService) {
        this.reporteService = reporteService;
        this.configuracionService = configuracionService;
    }

    /**
     * Vista principal de reportes avanzados con filtros dinámicos.
     */
    @GetMapping
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public String vistaReportesAvanzados(Model model) {
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        model.addAttribute("usuarios", reporteService.obtenerUsuarios());
        model.addAttribute("categorias", reporteService.obtenerCategorias());
        model.addAttribute("productos", reporteService.obtenerProductosActivos());
        model.addAttribute("active", "reportes_avanzados");
        return "reportes/avanzado";
    }

    // ==========================================
    //        REPORTES DE PRODUCTOS
    // ==========================================

    /**
     * Descarga Excel de stock actual
     */
    @GetMapping("/productos/stock-actual/excel")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarExcelStockActual(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"stock_actual.xlsx\"");
            reporteService.generarExcelStockActual(response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar Excel de stock actual", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    /**
     * Descarga PDF de stock actual
     */
    @GetMapping("/productos/stock-actual/pdf")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarPdfStockActual(HttpServletResponse response) {
        try {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"stock_actual.pdf\"");
            reporteService.generarPdfStockActual(response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar PDF de stock actual", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    /**
     * Descarga Excel de stock bajo
     */
    @GetMapping("/productos/stock-bajo/excel")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarExcelStockBajo(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"stock_bajo.xlsx\"");
            reporteService.generarExcelStockBajo(response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar Excel de stock bajo", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    /**
     * Descarga Excel de productos más vendidos
     */
    @GetMapping("/productos/mas-vendidos/excel")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarExcelProductosMasVendidos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"productos_mas_vendidos.xlsx\"");
            reporteService.generarExcelProductosMasVendidos(inicio, fin, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar Excel de productos más vendidos", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    /**
     * Descarga PDF de productos más vendidos
     */
    @GetMapping("/productos/mas-vendidos/pdf")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarPdfProductosMasVendidos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            HttpServletResponse response) {
        try {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"productos_mas_vendidos.pdf\"");
            reporteService.generarPdfProductosMasVendidos(inicio, fin, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar PDF de productos más vendidos", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    // ==========================================
    //        REPORTES DE VENTAS
    // ==========================================

    /**
     * Descarga Excel de ventas por fecha
     */
    @GetMapping("/ventas/por-fecha/excel")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarExcelVentasPorFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"ventas_por_fecha.xlsx\"");
            reporteService.generarExcelVentasPorFecha(inicio, fin, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar Excel de ventas por fecha", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    /**
     * Descarga Excel de ventas por usuario
     */
    @GetMapping("/ventas/por-usuario/excel")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarExcelVentasPorUsuario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) Long usuarioId,
            HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"ventas_por_usuario.xlsx\"");
            reporteService.generarExcelVentasPorUsuario(inicio, fin, usuarioId, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar Excel de ventas por usuario", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    /**
     * Descarga Excel de ventas por método de pago
     */
    @GetMapping("/ventas/por-metodo-pago/excel")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarExcelVentasPorMetodoPago(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) String metodoPago,
            HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"ventas_por_metodo_pago.xlsx\"");
            reporteService.generarExcelVentasPorMetodoPago(inicio, fin, metodoPago, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar Excel de ventas por método de pago", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    /**
     * Descarga Excel de ventas con ganancia
     */
    @GetMapping("/ventas/ganancia/excel")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarExcelVentasGanancia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"ventas_ganancia.xlsx\"");
            reporteService.generarExcelVentasConGanancia(inicio, fin, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar Excel de ventas con ganancia", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    /**
     * Descarga PDF de ventas con ganancia
     */
    @GetMapping("/ventas/ganancia/pdf")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarPdfVentasGanancia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            HttpServletResponse response) {
        try {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"ventas_ganancia.pdf\"");
            reporteService.generarPdfVentasConGanancia(inicio, fin, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar PDF de ventas con ganancia", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    // ==========================================
    //        REPORTES DE KARDEX
    // ==========================================

    /**
     * Descarga Excel de Kardex de un producto
     */
    @GetMapping("/kardex/excel")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarExcelKardex(
            @RequestParam Long productoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"kardex_producto.xlsx\"");
            reporteService.generarExcelKardexProducto(productoId, inicio, fin, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar Excel de Kardex", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    /**
     * Descarga PDF de Kardex de un producto
     */
    @GetMapping("/kardex/pdf")
    @PreAuthorize("hasPermission(null, 'REPORTES_VER')")
    public void descargarPdfKardex(
            @RequestParam Long productoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            HttpServletResponse response) {
        try {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"kardex_producto.pdf\"");
            reporteService.generarPdfKardexProducto(productoId, inicio, fin, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar PDF de Kardex", e);
            enviarError(response, "Error al generar el reporte");
        }
    }

    // ==========================================
    //        UTILIDADES
    // ==========================================

    private void enviarError(HttpServletResponse response, String mensaje) {
        try {
            response.sendError(500, mensaje);
        } catch (Exception ex) {
            log.error("Error al enviar respuesta de error", ex);
        }
    }
}
