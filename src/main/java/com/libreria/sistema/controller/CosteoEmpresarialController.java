package com.libreria.sistema.controller;

import com.libreria.sistema.model.dto.CuentaFijaDTO;
import com.libreria.sistema.service.CosteoEmpresarialService;
import com.libreria.sistema.service.CuentaFijaService;
import com.libreria.sistema.service.StockService;
import com.lowagie.text.DocumentException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Controller
@RequestMapping("/costeo")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
@Slf4j
public class CosteoEmpresarialController {

    private final CosteoEmpresarialService costeoEmpresarialService;
    private final CuentaFijaService cuentaFijaService;
    private final StockService stockService;

    public CosteoEmpresarialController(CosteoEmpresarialService costeoEmpresarialService,
                                       CuentaFijaService cuentaFijaService,
                                       StockService stockService) {
        this.costeoEmpresarialService = costeoEmpresarialService;
        this.cuentaFijaService = cuentaFijaService;
        this.stockService = stockService;
    }

    @GetMapping
    public String index(Model model) {
        YearMonth mes = YearMonth.now();
        model.addAttribute("resumen", costeoEmpresarialService.obtenerResumen());
        model.addAttribute("cuentas", cuentaFijaService.listarTodas());
        model.addAttribute("productos", costeoEmpresarialService.analizarProductos());
        model.addAttribute("categorias", stockService.obtenerCategorias());
        model.addAttribute("ventasDebajoMinimo",
                costeoEmpresarialService.ventasDebajoMinimo(mes.atDay(1), mes.atEndOfMonth()));
        return "costeo/index";
    }

    @PostMapping("/cuentas/guardar")
    @ResponseBody
    public ResponseEntity<?> guardarCuenta(@RequestBody CuentaFijaDTO dto) {
        try {
            var cuenta = cuentaFijaService.guardar(dto);
            return ResponseEntity.ok(Map.of(
                    "message", "Regla de costo guardada",
                    "id", cuenta.getId()
            ));
        } catch (Exception e) {
            log.error("Error guardando regla de costeo", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/productos/{id}/datos")
    @ResponseBody
    public ResponseEntity<?> guardarDatosProducto(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> payload) {
        try {
            var dto = costeoEmpresarialService.actualizarDatosCosteoProducto(
                    id,
                    texto(payload.get("reglaCosteo")),
                    decimal(payload.get("unidadesEstimadasMes")),
                    decimal(payload.get("minutosTrabajoUnidad")),
                    decimal(payload.get("impresionesEquivalentesUnidad")),
                    decimal(payload.get("gananciaObjetivoPct")),
                    decimal(payload.get("gananciaMinimaPct")),
                    bool(payload.get("usarReglaManualPrecio"))
            );
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Error actualizando datos de costeo del producto {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/productos/{id}/aplicar-precio")
    @ResponseBody
    public ResponseEntity<?> aplicarPrecio(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(costeoEmpresarialService.aplicarPrecioSugerido(id));
        } catch (Exception e) {
            log.error("Error aplicando precio sugerido al producto {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/productos/aplicar-precios-lote")
    @ResponseBody
    public ResponseEntity<?> aplicarPreciosLote(@RequestBody Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(costeoEmpresarialService.aplicarPreciosSugeridos(ids(payload.get("productoIds"))));
        } catch (Exception e) {
            log.error("Error aplicando precios sugeridos por lote", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/productos/{id}/historial")
    @ResponseBody
    public ResponseEntity<?> historialProducto(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(costeoEmpresarialService.historialCostosProducto(id));
        } catch (Exception e) {
            log.error("Error obteniendo historial de costos del producto {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/ventas-debajo-minimo")
    @ResponseBody
    public ResponseEntity<?> ventasDebajoMinimo(@RequestParam(required = false) LocalDate inicio,
                                                @RequestParam(required = false) LocalDate fin) {
        YearMonth mes = YearMonth.now();
        LocalDate desde = inicio != null ? inicio : mes.atDay(1);
        LocalDate hasta = fin != null ? fin : mes.atEndOfMonth();
        try {
            return ResponseEntity.ok(costeoEmpresarialService.ventasDebajoMinimo(desde, hasta));
        } catch (Exception e) {
            log.error("Error obteniendo ventas debajo de minimo", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/exportar/excel")
    public ResponseEntity<byte[]> exportarExcel() throws IOException {
        byte[] bytes = costeoEmpresarialService.exportarExcel(costeoEmpresarialService.analizarProductos());
        String filename = "costeo_precios_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/exportar/pdf")
    public void exportarPdf(HttpServletResponse response) throws IOException, DocumentException {
        String filename = "costeo_precios_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        costeoEmpresarialService.exportarPdf(costeoEmpresarialService.analizarProductos(), response.getOutputStream());
    }

    private String texto(Object value) {
        return value != null ? value.toString() : null;
    }

    private BigDecimal decimal(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean bool(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Long> ids(Object value) {
        if (!(value instanceof java.util.List<?> raw)) {
            return java.util.List.of();
        }
        return raw.stream()
                .map(item -> {
                    try {
                        return Long.valueOf(item.toString());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
