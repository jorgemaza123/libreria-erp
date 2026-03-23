package com.libreria.sistema.service;

import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.repository.CompraRepository;
import com.libreria.sistema.repository.DetalleVentaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MiNegocioService {

    private final VentaRepository ventaRepository;
    private final CompraRepository compraRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final CajaRepository cajaRepository;
    private final ProductoRepository productoRepository;

    /**
     * Calcula todos los indicadores para el período dado.
     * También calcula el período anterior de igual duración para comparación.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerAnalisis(LocalDate inicio, LocalDate fin) {
        Map<String, Object> r = new LinkedHashMap<>();

        // ── Período actual ───────────────────────────────────────────
        BigDecimal vendido    = safe(ventaRepository.sumVentasValidasByPeriodo(inicio, fin));
        long       numVentas  = ventaRepository.countVentasValidasByPeriodo(inicio, fin);
        BigDecimal invertido  = safe(compraRepository.sumTotalByPeriodo(
                inicio.atStartOfDay(), fin.atTime(23, 59, 59)));
        BigDecimal ganancia   = safe(detalleVentaRepository.sumarUtilidadPorPeriodo(inicio, fin));
        BigDecimal margenPct  = vendido.compareTo(BigDecimal.ZERO) > 0
                ? ganancia.divide(vendido, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ── Caja del período ────────────────────────────────────────
        BigDecimal ingresosCaja = safe(cajaRepository.sumarIngresosPorFechas(inicio, fin));
        BigDecimal egresosCaja  = safe(cajaRepository.sumarEgresosPorFechas(inicio, fin));
        BigDecimal flujoCaja    = ingresosCaja.subtract(egresosCaja);

        // ── Inventario (valor actual, no por período) ───────────────
        BigDecimal valorInventario = productoRepository.findByActivoTrue().stream()
                .map(p -> {
                    BigDecimal costo = p.getPrecioCompra() != null ? p.getPrecioCompra() : BigDecimal.ZERO;
                    BigDecimal stock = BigDecimal.valueOf(p.getStockActual() != null ? p.getStockActual() : 0);
                    return costo.multiply(stock);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Top 10 productos más rentables ─────────────────────────
        List<Object[]> topRaw = detalleVentaRepository.topProductosPorUtilidad(
                inicio, fin, PageRequest.of(0, 10));
        List<Map<String, Object>> topProductos = new ArrayList<>();
        for (Object[] row : topRaw) {
            BigDecimal ingreso   = row[5] != null ? (BigDecimal) row[5] : BigDecimal.ZERO;
            BigDecimal utilidad  = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
            BigDecimal margenProd = ingreso.compareTo(BigDecimal.ZERO) > 0
                    ? utilidad.divide(ingreso, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nombre",    row[1]);
            item.put("categoria", row[2]);
            item.put("cantidad",  row[3]);
            item.put("ingreso",   ingreso);
            item.put("utilidad",  utilidad);
            item.put("margen",    margenProd);
            topProductos.add(item);
        }

        // ── Período anterior (misma duración) para comparación ─────
        long dias = ChronoUnit.DAYS.between(inicio, fin) + 1;
        LocalDate inicioAnt = inicio.minusDays(dias);
        LocalDate finAnt    = fin.minusDays(dias);
        BigDecimal vendidoAnt  = safe(ventaRepository.sumVentasValidasByPeriodo(inicioAnt, finAnt));
        BigDecimal invertidoAnt = safe(compraRepository.sumTotalByPeriodo(
                inicioAnt.atStartOfDay(), finAnt.atTime(23, 59, 59)));
        BigDecimal gananciaAnt  = safe(detalleVentaRepository.sumarUtilidadPorPeriodo(inicioAnt, finAnt));

        r.put("vendido",         vendido);
        r.put("numVentas",       numVentas);
        r.put("invertido",       invertido);
        r.put("ganancia",        ganancia);
        r.put("margenPct",       margenPct);
        r.put("ingresosCaja",    ingresosCaja);
        r.put("egresosCaja",     egresosCaja);
        r.put("flujoCaja",       flujoCaja);
        r.put("valorInventario", valorInventario);
        r.put("topProductos",    topProductos);
        r.put("varVendido",      variacion(vendido, vendidoAnt));
        r.put("varInvertido",    variacion(invertido, invertidoAnt));
        r.put("varGanancia",     variacion(ganancia, gananciaAnt));
        r.put("vendidoAnt",      vendidoAnt);
        r.put("invertidoAnt",    invertidoAnt);
        r.put("gananciaAnt",     gananciaAnt);
        return r;
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal variacion(BigDecimal actual, BigDecimal anterior) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return actual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
