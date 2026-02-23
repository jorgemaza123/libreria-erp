package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.ReporteDTO;
import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.repository.CotizacionRepository;
import com.libreria.sistema.repository.DetalleVentaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.VentaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dashboard Service OPTIMIZADO (Fase 1 + Fase 2):
 * - Todos los findAll() reemplazados por queries JPA con filtro en BD
 * - Nuevas metricas: ventas hoy, ayer, mes pasado, variacion %, canal
 * - Grafico de 12 meses en lugar de 6
 * - Top productos filtrado por mes actual
 *
 * ESTADOS VALIDOS PARA KPIs FINANCIEROS (definidos en Constants.java):
 * - EMITIDO, PAGADO_TOTAL, DEVUELTO_PARCIAL
 * EXCLUIDOS: ANULADO, DEVUELTO_TOTAL
 */
@Service
public class DashboardService {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final CajaRepository cajaRepository;
    private final CotizacionRepository cotizacionRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final SunatBillingService sunatBillingService;
    private final LicenseValidationService licenseService;
    private final ConfiguracionService configuracionService;

    public DashboardService(ProductoRepository productoRepository,
                           VentaRepository ventaRepository,
                           CajaRepository cajaRepository,
                           CotizacionRepository cotizacionRepository,
                           DetalleVentaRepository detalleVentaRepository,
                           SunatBillingService sunatBillingService,
                           LicenseValidationService licenseService,
                           ConfiguracionService configuracionService) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.cajaRepository = cajaRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.sunatBillingService = sunatBillingService;
        this.licenseService = licenseService;
        this.configuracionService = configuracionService;
    }

    public Map<String, Object> obtenerDatosDashboard() {
        LocalDate hoy = LocalDate.now();
        LocalDate ayer = hoy.minusDays(1);
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        YearMonth mesAnterior = YearMonth.now().minusMonths(1);
        LocalDate inicioMesPasado = mesAnterior.atDay(1);
        LocalDate finMesPasado = mesAnterior.atEndOfMonth();

        // ============================================================
        // 1. KPIs PRINCIPALES (queries optimizados en BD)
        // ============================================================

        // Ventas del mes (solo estados validos)
        BigDecimal totalVentasMes = ventaRepository.sumVentasValidasByPeriodo(inicioMes, hoy);
        long cantidadVentasMes = ventaRepository.countVentasValidasByPeriodo(inicioMes, hoy);

        // Ventas de hoy
        BigDecimal ventasHoy = ventaRepository.sumVentasValidasByPeriodo(hoy, hoy);
        long cantidadVentasHoy = ventaRepository.countVentasValidasByPeriodo(hoy, hoy);

        // Ventas de ayer
        BigDecimal ventasAyer = ventaRepository.sumVentasValidasByPeriodo(ayer, ayer);
        long cantidadVentasAyer = ventaRepository.countVentasValidasByPeriodo(ayer, ayer);

        // Ventas del mes pasado
        BigDecimal ventasMesPasado = ventaRepository.sumVentasValidasByPeriodo(inicioMesPasado, finMesPasado);
        long cantidadVentasMesPasado = ventaRepository.countVentasValidasByPeriodo(inicioMesPasado, finMesPasado);

        // Creditos por cobrar (query directo en BD)
        BigDecimal totalCreditosPendientes = ventaRepository.sumCreditosPendientes();

        // Valor del inventario (query optimizado con repository)
        BigDecimal valorInventario = calcularValorInventario();

        // Gastos del mes (egresos de caja)
        BigDecimal gastosMes = cajaRepository.sumarEgresosPorFechas(inicioMes, hoy);

        // ============================================================
        // 2. INDICADORES DE VARIACION (%)
        // ============================================================

        // Variacion ventas hoy vs ayer
        BigDecimal variacionDiaria = calcularVariacion(ventasHoy, ventasAyer);

        // Variacion mes actual vs mes pasado
        BigDecimal variacionMensual = calcularVariacion(totalVentasMes, ventasMesPasado);

        // Variacion cantidad pedidos mes actual vs mes pasado
        BigDecimal variacionPedidos = calcularVariacion(
                BigDecimal.valueOf(cantidadVentasMes),
                BigDecimal.valueOf(cantidadVentasMesPasado));

        // ============================================================
        // 3. DATOS PARA GRAFICOS (queries agrupados en BD)
        // ============================================================

        // Top 5 productos del MES (no historico global)
        List<ReporteDTO> topProductos = ventaRepository.obtenerTopProductosMes(
                inicioMes, PageRequest.of(0, 5));

        // Grafico de ventas mensuales (12 meses) - query agrupado
        LocalDate inicio12Meses = YearMonth.now().minusMonths(11).atDay(1);
        Map<String, Map<String, BigDecimal>> comparativa = construirComparativa12Meses(inicio12Meses);

        // Modalidad de venta (CONTADO vs CREDITO) - query agrupado
        Map<String, Long> pieCredito = construirPieModalidad();

        // Ventas por canal
        List<Map<String, Object>> ventasPorCanal = construirVentasPorCanal(inicioMes, hoy);

        // ============================================================
        // 4. KPIs COTIZACIONES
        // ============================================================
        List<String> estadosPendientesCoti = Arrays.asList("EMITIDA", "EMITIDO", "ENVIADA", "APROBADA", "EN_NEGOCIACION");
        long cotizacionesPendientes = cotizacionRepository.countByEstadoIn(estadosPendientesCoti);
        BigDecimal montoCotizadoMes = cotizacionRepository.sumTotalByFechaCreacionBetween(
                inicioMes.atStartOfDay(), LocalDateTime.now());
        List<String> estadosConvertidos = Arrays.asList("CONVERTIDA", "CONVERTIDO_VENTA");
        long cotizacionesMes = cotizacionRepository.countByFechaCreacionBetween(
                inicioMes.atStartOfDay(), LocalDateTime.now());
        long convertidasMes = cotizacionRepository.countByEstadoInAndFechaCreacionBetween(
                estadosConvertidos, inicioMes.atStartOfDay(), LocalDateTime.now());
        double tasaConversionCoti = cotizacionesMes > 0 ? (double) convertidasMes / cotizacionesMes * 100 : 0;

        // ============================================================
        // 4.5. KPIs UTILIDAD BRUTA (solo hoy y mes — queries livianas)
        // ============================================================
        BigDecimal utilidadHoy = detalleVentaRepository.sumarUtilidadPorPeriodo(hoy, hoy);
        BigDecimal utilidadMes = detalleVentaRepository.sumarUtilidadPorPeriodo(inicioMes, hoy);
        BigDecimal utilidadMesPasado = detalleVentaRepository.sumarUtilidadPorPeriodo(inicioMesPasado, finMesPasado);
        BigDecimal variacionUtilidadMensual = calcularVariacion(utilidadMes, utilidadMesPasado);
        BigDecimal margenPromedioMes = (totalVentasMes != null && totalVentasMes.compareTo(BigDecimal.ZERO) > 0)
                ? utilidadMes.divide(totalVentasMes, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ============================================================
        // 5. ALERTAS STOCK + MARGEN BAJO
        // ============================================================
        List<Producto> stockCritico = productoRepository.obtenerStockCritico();

        // Alerta de productos con margen bajo en el mes
        BigDecimal margenMinimoAlerta = configuracionService.obtenerConfiguracion().getMargenMinimoAlerta();
        if (margenMinimoAlerta == null) margenMinimoAlerta = new BigDecimal("15.00");
        List<Object[]> allMargenBajo = detalleVentaRepository.productosConMargenBajo(inicioMes, hoy, margenMinimoAlerta);
        int cantidadMargenBajo = allMargenBajo.size();
        List<Map<String, Object>> alertasMargenBajo = new ArrayList<>();
        int limit = Math.min(allMargenBajo.size(), 10);
        for (int i = 0; i < limit; i++) {
            Object[] row = allMargenBajo.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("productoId", row[0]);
            item.put("nombre", row[1]);
            item.put("ingreso", row[2]);
            item.put("utilidad", row[3]);
            item.put("margen", ((Number) row[4]).doubleValue());
            alertasMargenBajo.add(item);
        }

        // ============================================================
        // 5. DATOS SUNAT Y LICENCIA
        // ============================================================
        Map<String, Object> sunatStats = sunatBillingService.obtenerEstadisticasDashboard();
        LicenseValidationService.LicenseInfo licenseInfo = licenseService.validarLicencia();

        // ============================================================
        // CONSTRUIR RESULTADO
        // ============================================================
        Map<String, Object> resultado = new HashMap<>();

        // KPIs originales
        resultado.put("kpiVentasMes", totalVentasMes);
        resultado.put("kpiCreditos", totalCreditosPendientes);
        resultado.put("kpiInventario", valorInventario);
        resultado.put("kpiGastos", gastosMes);

        // KPIs nuevos (Fase 2)
        resultado.put("ventasHoy", ventasHoy);
        resultado.put("cantidadVentasHoy", cantidadVentasHoy);
        resultado.put("ventasAyer", ventasAyer);
        resultado.put("cantidadVentasAyer", cantidadVentasAyer);
        resultado.put("ventasMesPasado", ventasMesPasado);
        resultado.put("cantidadVentasMes", cantidadVentasMes);
        resultado.put("cantidadVentasMesPasado", cantidadVentasMesPasado);

        // Indicadores de variacion
        resultado.put("variacionDiaria", variacionDiaria);
        resultado.put("variacionMensual", variacionMensual);
        resultado.put("variacionPedidos", variacionPedidos);

        // Graficos
        resultado.put("topProductos", topProductos);
        resultado.put("comparativa", comparativa);
        resultado.put("pieCredito", pieCredito);
        resultado.put("ventasPorCanal", ventasPorCanal);

        // Alertas
        resultado.put("stockCritico", stockCritico);
        resultado.put("sinMovimiento", new ArrayList<>());
        resultado.put("alertasMargenBajo", alertasMargenBajo);
        resultado.put("cantidadMargenBajo", cantidadMargenBajo);
        resultado.put("margenMinimoConfig", margenMinimoAlerta);

        // Cotizaciones
        resultado.put("cotizacionesPendientes", cotizacionesPendientes);
        resultado.put("montoCotizadoMes", montoCotizadoMes);
        resultado.put("cotizacionesMes", cotizacionesMes);
        resultado.put("tasaConversionCoti", Math.round(tasaConversionCoti * 10.0) / 10.0);

        // Utilidad bruta
        resultado.put("utilidadHoy", utilidadHoy);
        resultado.put("utilidadMes", utilidadMes);
        resultado.put("variacionUtilidadMensual", variacionUtilidadMensual);
        resultado.put("margenPromedioMes", margenPromedioMes);

        // SUNAT y Licencia
        resultado.put("sunatStats", sunatStats);
        resultado.put("licenseInfo", licenseInfo);
        resultado.put("alertaPago", licenseInfo.isAlertaPago());
        resultado.put("tieneDeudaSunat", sunatBillingService.hayDeudaPendiente());
        resultado.put("deudaTotalSunat", sunatBillingService.obtenerDeudaTotal());

        return resultado;
    }

    /**
     * Calcula variacion porcentual entre valor actual y anterior.
     * Retorna 0 si el anterior es 0 (evita division por cero).
     */
    private BigDecimal calcularVariacion(BigDecimal actual, BigDecimal anterior) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return actual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Calcula valor del inventario sumando (precioCompra * stockActual) de productos activos.
     * Usa findByActivoTrue() que solo trae productos activos.
     */
    private BigDecimal calcularValorInventario() {
        List<Producto> productos = productoRepository.findByActivoTrue();
        return productos.stream()
                .map(p -> {
                    BigDecimal costo = p.getPrecioCompra() != null ? p.getPrecioCompra() : BigDecimal.ZERO;
                    BigDecimal stock = new BigDecimal(p.getStockActual() != null ? p.getStockActual() : 0);
                    return costo.multiply(stock);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Construye la comparativa de ingresos vs egresos para 12 meses.
     * Usa queries agrupados en BD en lugar de findAll() en loop.
     */
    private Map<String, Map<String, BigDecimal>> construirComparativa12Meses(LocalDate inicio12Meses) {
        // Obtener ventas mensuales agrupadas desde BD
        List<Object[]> ventasMensuales = ventaRepository.ventasMensualesAgrupadas(inicio12Meses);
        Map<String, BigDecimal> ventasPorMes = new HashMap<>();
        for (Object[] row : ventasMensuales) {
            ventasPorMes.put((String) row[0], (BigDecimal) row[1]);
        }

        // Obtener egresos mensuales agrupados desde BD
        List<Object[]> egresosMensuales = cajaRepository.egresosMensualesAgrupados(
                inicio12Meses.atStartOfDay());
        Map<String, BigDecimal> egresosPorMes = new HashMap<>();
        for (Object[] row : egresosMensuales) {
            egresosPorMes.put((String) row[0], (BigDecimal) row[1]);
        }

        // Construir mapa ordenado con los 12 meses
        Map<String, Map<String, BigDecimal>> comparativa = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth mes = YearMonth.now().minusMonths(i);
            String clave = mes.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String etiqueta = mes.format(DateTimeFormatter.ofPattern("MMM yyyy"));

            BigDecimal ingresos = ventasPorMes.getOrDefault(clave, BigDecimal.ZERO);
            BigDecimal egresos = egresosPorMes.getOrDefault(clave, BigDecimal.ZERO);

            comparativa.put(etiqueta, Map.of("ingreso", ingresos, "egreso", egresos));
        }

        return comparativa;
    }

    /**
     * Construye el pie chart de modalidad de venta (CONTADO vs CREDITO).
     * Usa query agrupado en BD.
     */
    private Map<String, Long> construirPieModalidad() {
        List<Object[]> resultados = ventaRepository.countByFormaPago();
        long credito = 0;
        long contado = 0;
        for (Object[] row : resultados) {
            String forma = (String) row[0];
            long count = (Long) row[1];
            if ("CREDITO".equals(forma)) {
                credito = count;
            } else if ("CONTADO".equals(forma)) {
                contado = count;
            }
        }
        return Map.of("credito", credito, "contado", contado);
    }

    /**
     * Construye datos de ventas por canal para grafico.
     */
    private List<Map<String, Object>> construirVentasPorCanal(LocalDate inicio, LocalDate fin) {
        List<Object[]> resultados = ventaRepository.ventasPorCanal(inicio, fin);
        List<Map<String, Object>> canales = new ArrayList<>();
        for (Object[] row : resultados) {
            Map<String, Object> canal = new HashMap<>();
            canal.put("canal", row[0]);
            canal.put("cantidad", row[1]);
            canal.put("total", row[2]);
            canales.add(canal);
        }
        return canales;
    }
}
