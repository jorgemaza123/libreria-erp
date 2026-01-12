package com.libreria.sistema.service;

import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.model.dto.ReporteDTO;
import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.VentaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final CajaRepository cajaRepository;
    private final SunatBillingService sunatBillingService;
    private final LicenseValidationService licenseService;

    public DashboardService(ProductoRepository productoRepository,
                           VentaRepository ventaRepository,
                           CajaRepository cajaRepository,
                           SunatBillingService sunatBillingService,
                           LicenseValidationService licenseService) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.cajaRepository = cajaRepository;
        this.sunatBillingService = sunatBillingService;
        this.licenseService = licenseService;
    }

    public Map<String, Object> obtenerDatosDashboard() {
        LocalDate hoy = LocalDate.now();
        LocalDate inicioMes = hoy.withDayOfMonth(1);

        // --- 1. KPIs PRINCIPALES ---
        List<Venta> ventasMes = ventaRepository.findAll().stream()
                .filter(v -> v.getFechaEmision() != null && !v.getFechaEmision().isBefore(inicioMes) && "EMITIDO".equals(v.getEstado()))
                .collect(Collectors.toList());

        BigDecimal totalVentasMes = ventasMes.stream()
                .map(v -> v.getTotal() != null ? v.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcular Créditos por Cobrar (CORREGIDO NULL POINTER)
        BigDecimal totalCreditosPendientes = ventaRepository.findAll().stream()
                .filter(v -> v.getSaldoPendiente() != null && // <--- VALIDACIÓN IMPORTANTE
                             v.getSaldoPendiente().compareTo(BigDecimal.ZERO) > 0 && 
                             !"ANULADO".equals(v.getEstado()))
                .map(Venta::getSaldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcular Valor del Inventario
        List<Producto> productos = productoRepository.findAll();
        BigDecimal valorInventario = productos.stream()
                .map(p -> {
                    BigDecimal costo = p.getPrecioCompra() != null ? p.getPrecioCompra() : BigDecimal.ZERO;
                    BigDecimal stock = new BigDecimal(p.getStockActual() != null ? p.getStockActual() : 0);
                    return costo.multiply(stock);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcular Gastos del Mes
        BigDecimal gastosMes = cajaRepository.findAll().stream()
                .filter(m -> "EGRESO".equals(m.getTipo()) && 
                             m.getFecha() != null && 
                             !m.getFecha().toLocalDate().isBefore(inicioMes))
                .map(m -> m.getMonto() != null ? m.getMonto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- 2. DATOS PARA GRÁFICOS ---
        List<ReporteDTO> topProductos = productoRepository.obtenerTopProductos();

        // Comparativa
        Map<String, Map<String, BigDecimal>> comparativa = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth mes = YearMonth.now().minusMonths(i);
            String etiqueta = mes.format(DateTimeFormatter.ofPattern("MMM yyyy")); 
            
            BigDecimal ingresos = ventaRepository.findAll().stream()
                .filter(v -> v.getFechaEmision() != null && YearMonth.from(v.getFechaEmision()).equals(mes))
                .map(v -> v.getTotal() != null ? v.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            BigDecimal egresos = cajaRepository.findAll().stream()
                .filter(m -> "EGRESO".equals(m.getTipo()) && 
                             m.getFecha() != null && 
                             YearMonth.from(m.getFecha()).equals(mes))
                .map(m -> m.getMonto() != null ? m.getMonto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            comparativa.put(etiqueta, Map.of("ingreso", ingresos, "egreso", egresos));
        }

        long ventasAlCredito = ventaRepository.findAll().stream()
                .filter(v -> "CREDITO".equals(v.getFormaPago())).count();
        long ventasContado = ventaRepository.findAll().stream()
                .filter(v -> "CONTADO".equals(v.getFormaPago())).count();

        // --- 3. ALERTA STOCK ---
        List<Producto> stockCritico = productos.stream()
                .filter(p -> p.getStockActual() != null && p.getStockMinimo() != null &&
                             p.getStockActual() <= p.getStockMinimo() && p.isActivo())
                .collect(Collectors.toList());

        // --- 4. DATOS SUNAT Y LICENCIA ---
        Map<String, Object> sunatStats = sunatBillingService.obtenerEstadisticasDashboard();
        LicenseValidationService.LicenseInfo licenseInfo = licenseService.validarLicencia();

        // Usar HashMap mutable para poder agregar más datos
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("kpiVentasMes", totalVentasMes);
        resultado.put("kpiCreditos", totalCreditosPendientes);
        resultado.put("kpiInventario", valorInventario);
        resultado.put("kpiGastos", gastosMes);
        resultado.put("topProductos", topProductos);
        resultado.put("comparativa", comparativa);
        resultado.put("pieCredito", Map.of("credito", ventasAlCredito, "contado", ventasContado));
        resultado.put("stockCritico", stockCritico);
        resultado.put("sinMovimiento", new ArrayList<>());

        // Agregar stats de SUNAT Billing
        resultado.put("sunatStats", sunatStats);

        // Agregar info de licencia
        resultado.put("licenseInfo", licenseInfo);
        resultado.put("alertaPago", licenseInfo.isAlertaPago());

        // Agregar alerta de deuda SUNAT
        resultado.put("tieneDeudaSunat", sunatBillingService.hayDeudaPendiente());
        resultado.put("deudaTotalSunat", sunatBillingService.obtenerDeudaTotal());

        return resultado;
    }
}