package com.libreria.sistema.controller;

import com.libreria.sistema.model.Kardex;
import com.libreria.sistema.model.dto.DataTableResponse;
import com.libreria.sistema.model.dto.StockDTO;
import com.libreria.sistema.service.RolePermissionService;
import com.libreria.sistema.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stock")
@PreAuthorize("hasPermission(null, 'STOCK_VER')")
@Slf4j
public class StockController {

    private final StockService stockService;
    private final RolePermissionService permissionService;

    public StockController(StockService stockService, RolePermissionService permissionService) {
        this.stockService = stockService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public String index(Model model, Authentication auth) {
        Map<String, Object> kpis = stockService.obtenerKPIs();
        model.addAttribute("kpis", kpis);
        model.addAttribute("categorias", stockService.obtenerCategorias());
        model.addAttribute("puedeAjustar", permissionService.tienePermiso(auth.getName(), "STOCK_AJUSTAR"));
        model.addAttribute("puedeVerMovimientos", permissionService.tienePermiso(auth.getName(), "STOCK_MOVIMIENTOS"));
        return "stock/index";
    }

    @GetMapping("/api/buscar")
    @ResponseBody
    public DataTableResponse<StockDTO> buscarStock(
            @RequestParam(defaultValue = "0") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String termino,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String estado,
            @RequestParam(name = "order[0][column]", defaultValue = "1") String orderColumn,
            @RequestParam(name = "order[0][dir]", defaultValue = "asc") String orderDir) {
        return stockService.buscarStock(draw, start, length, termino, categoria, estado, orderColumn, orderDir);
    }

    @GetMapping("/producto/{id}")
    public String detalleProducto(@PathVariable Long id, Model model, Authentication auth) {
        StockDTO dto = stockService.obtenerDetalleProducto(id);
        model.addAttribute("producto", dto);
        model.addAttribute("puedeAjustar", permissionService.tienePermiso(auth.getName(), "STOCK_AJUSTAR"));
        model.addAttribute("puedeVerMovimientos", permissionService.tienePermiso(auth.getName(), "STOCK_MOVIMIENTOS"));
        return "stock/detalle";
    }

    @GetMapping("/api/movimientos/{id}")
    @PreAuthorize("hasPermission(null, 'STOCK_MOVIMIENTOS')")
    @ResponseBody
    public Map<String, Object> obtenerMovimientos(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Page<Kardex> movimientos = stockService.obtenerMovimientos(id, page, size);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<Map<String, Object>> data = movimientos.getContent().stream().map(k -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", k.getId());
            m.put("fecha", k.getFecha() != null ? k.getFecha().format(fmt) : "");
            m.put("tipo", k.getTipo());
            m.put("motivo", k.getMotivo());
            m.put("cantidad", k.getCantidad());
            m.put("stockAnterior", k.getStockAnterior());
            m.put("stockActual", k.getStockActual());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        result.put("totalPages", movimientos.getTotalPages());
        result.put("totalElements", movimientos.getTotalElements());
        result.put("currentPage", page);
        return result;
    }

    @GetMapping("/api/evolucion/{id}")
    @ResponseBody
    public Map<String, Object> obtenerEvolucion(@PathVariable Long id) {
        return stockService.obtenerEvolucionStock(id);
    }

    @PostMapping("/api/ajustar")
    @PreAuthorize("hasPermission(null, 'STOCK_AJUSTAR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> ajustarStock(@RequestParam Long productoId,
                                                             @RequestParam Integer nuevoStock,
                                                             @RequestParam(required = false) String motivo,
                                                             Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        try {
            stockService.ajustarStock(productoId, nuevoStock, motivo, auth.getName());
            response.put("success", true);
            response.put("message", "Stock ajustado correctamente");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error al ajustar stock", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/exportar/excel")
    public ResponseEntity<byte[]> exportarExcel(@RequestParam(required = false) String termino,
                                                 @RequestParam(required = false) String categoria,
                                                 @RequestParam(required = false) String estado) {
        try {
            byte[] bytes = stockService.exportarExcel(termino, categoria, estado);
            String filename = "stock_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } catch (Exception e) {
            log.error("Error al exportar Excel de stock", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
