package com.libreria.sistema.controller;

import com.libreria.sistema.service.MiNegocioService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/mi-negocio")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
public class MiNegocioController {

    private final MiNegocioService miNegocioService;

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    public String index(
            @RequestParam(name = "periodo", defaultValue = "semana") String periodo,
            @RequestParam(name = "inicio", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate inicio,
            @RequestParam(name = "fin", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fin,
            Model model) {

        LocalDate hoy = LocalDate.now();

        // Calcular rango según período seleccionado
        switch (periodo) {
            case "hoy":
                inicio = hoy;
                fin = hoy;
                break;
            case "semana":
                inicio = hoy.with(DayOfWeek.MONDAY);
                fin = hoy;
                break;
            case "mes":
                inicio = hoy.withDayOfMonth(1);
                fin = hoy;
                break;
            case "personalizado":
                if (inicio == null)
                    inicio = hoy.withDayOfMonth(1);
                if (fin == null)
                    fin = hoy;
                break;
            default:
                inicio = hoy.with(DayOfWeek.MONDAY);
                fin = hoy;
                periodo = "semana";
        }

        Map<String, Object> analisis = miNegocioService.obtenerAnalisis(inicio, fin);
        model.addAllAttributes(analisis);
        model.addAttribute("periodo", periodo);
        model.addAttribute("inicioPeriodo", inicio);
        model.addAttribute("finPeriodo", fin);
        model.addAttribute("inicioFmt", inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("finFmt", fin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("inicioIso", inicio.toString());
        model.addAttribute("finIso", fin.toString());

        return "mi-negocio/index";
    }

    /**
     * NUEVO — Resumen financiero del inventario en tiempo real.
     * Sin cambios en BD: calcula sobre los datos existentes de productos y compras.
     */
    @GetMapping("/api/inventario-resumen")
    @ResponseBody
    public ResponseEntity<?> inventarioResumen() {
        try {
            // Valor al costo: stock * precioCompra (productos activos con stock > 0)
            Number valCostoNum = (Number) em.createQuery(
                    "SELECT COALESCE(SUM(CAST(p.stockActual AS big_decimal) * p.precioCompra), 0) " +
                            "FROM Producto p WHERE p.activo = true AND p.stockActual > 0 AND p.precioCompra IS NOT NULL")
                    .getSingleResult();
            BigDecimal valorAlCosto = new BigDecimal(valCostoNum.toString()).setScale(2, RoundingMode.HALF_UP);

            // Valor al precio de venta
            Number valVentaNum = (Number) em.createQuery(
                    "SELECT COALESCE(SUM(CAST(p.stockActual AS big_decimal) * p.precioVenta), 0) " +
                            "FROM Producto p WHERE p.activo = true AND p.stockActual > 0 AND p.precioVenta IS NOT NULL")
                    .getSingleResult();
            BigDecimal valorAlVenta = new BigDecimal(valVentaNum.toString()).setScale(2, RoundingMode.HALF_UP);

            BigDecimal gananciaPotencial = valorAlVenta.subtract(valorAlCosto);

            // Productos con stock bajo mínimo
            Number bajoStockNum = (Number) em.createQuery(
                    "SELECT COUNT(p) FROM Producto p WHERE p.activo = true AND p.stockActual <= p.stockMinimo")
                    .getSingleResult();
            long productosStockBajo = bajoStockNum != null ? bajoStockNum.longValue() : 0L;

            // Total compras últimos 30 días
            LocalDate hace30 = LocalDate.now().minusDays(30);
            Number compras30Num = (Number) em.createQuery(
                    "SELECT COALESCE(SUM(c.total), 0) FROM Compra c WHERE c.fecha >= :d AND c.estado != 'ANULADA'")
                    .setParameter("d", hace30.atStartOfDay())
                    .getSingleResult();
            BigDecimal compras30d = new BigDecimal(compras30Num.toString()).setScale(2, RoundingMode.HALF_UP);

            // Total ventas últimos 30 días
            Number ventas30Num = (Number) em.createQuery(
                    "SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.fechaEmision >= :d " +
                            "AND v.estado IN ('EMITIDO','PAGADO_TOTAL','DEVUELTO_PARCIAL')")
                    .setParameter("d", hace30)
                    .getSingleResult();
            BigDecimal ventas30d = new BigDecimal(ventas30Num.toString()).setScale(2, RoundingMode.HALF_UP);

            // Total productos activos con stock
            Number totalProdNum = (Number) em.createQuery(
                    "SELECT COUNT(p) FROM Producto p WHERE p.activo = true AND p.stockActual > 0")
                    .getSingleResult();

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("valorAlCosto", valorAlCosto);
            resp.put("valorAlVenta", valorAlVenta);
            resp.put("gananciaPotencial", gananciaPotencial);
            resp.put("productosStockBajo", productosStockBajo);
            resp.put("compras30d", compras30d);
            resp.put("ventas30d", ventas30d);
            resp.put("totalProductos", totalProdNum != null ? totalProdNum.longValue() : 0L);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
