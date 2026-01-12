package com.libreria.sistema.service;

import com.libreria.sistema.model.MonthlyBilling;
import com.libreria.sistema.model.MonthlyBilling.EstadoPago;
import com.libreria.sistema.repository.MonthlyBillingRepository;
import com.libreria.sistema.repository.VentaRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para gestionar la facturacion mensual del consumo SUNAT.
 * Controla el contador de comprobantes, calcula costos y gestiona pagos.
 */
@Service
@Slf4j
public class SunatBillingService {

    private final VentaRepository ventaRepository;
    private final MonthlyBillingRepository billingRepository;
    private final SystemConfigurationService configService;

    // Limite de comprobantes gratuitos por mes
    private static final int LIMITE_GRATIS = 20;

    // Tabla de precios por cantidad de comprobantes
    private static final int[][] TABLA_PRECIOS = {
            {0, 20, 0},         // 0-20: S/ 0.00 (Gratis)
            {21, 100, 20},      // 21-100: S/ 20.00
            {101, 300, 40},     // 101-300: S/ 40.00
            {301, 500, 60},     // 301-500: S/ 60.00
            {501, 1000, 90},    // 501-1000: S/ 90.00
            {1001, 2000, 150},  // 1001-2000: S/ 150.00
            {2001, 5000, 250},  // 2001-5000: S/ 250.00
            {5001, Integer.MAX_VALUE, 400} // 5001+: S/ 400.00
    };

    public SunatBillingService(VentaRepository ventaRepository,
                               MonthlyBillingRepository billingRepository,
                               SystemConfigurationService configService) {
        this.ventaRepository = ventaRepository;
        this.billingRepository = billingRepository;
        this.configService = configService;
    }

    /**
     * DTO con el resumen del mes actual
     */
    @Data
    public static class BillingSummary {
        private String mesAnio;
        private int comprobantesEmitidos;
        private int boletas;
        private int facturas;
        private int comprobantesGratisRestantes;
        private BigDecimal costoEstimado;
        private String rangoActual;
        private boolean sunatActivo;
        private boolean tieneDeudaPendiente;
        private BigDecimal deudaTotal;
        private int mesesConDeuda;
        private String mensaje;
    }

    /**
     * Obtiene el resumen de facturacion del mes actual.
     * Incluye verificacion perezosa del mes anterior.
     */
    public BillingSummary obtenerResumenMesActual() {
        LocalDate hoy = LocalDate.now();
        String mesAnio = hoy.format(DateTimeFormatter.ofPattern("MM-yyyy"));

        // Verificacion perezosa: asegurar que el mes anterior este cerrado
        // Solo los primeros 5 dias del mes para evitar procesamiento innecesario
        if (hoy.getDayOfMonth() <= 5) {
            verificarCierreMesAnterior();
        }

        return obtenerResumenMes(mesAnio);
    }

    /**
     * Verificacion perezosa: cierra el mes anterior si no esta cerrado.
     * Se llama automaticamente al obtener el resumen del mes actual.
     */
    private void verificarCierreMesAnterior() {
        try {
            LocalDate hoy = LocalDate.now();
            LocalDate mesAnterior = hoy.minusMonths(1);
            String mesAnteriorStr = mesAnterior.format(DateTimeFormatter.ofPattern("MM-yyyy"));

            // Verificar si existe el registro del mes anterior
            MonthlyBilling billingAnterior = billingRepository.findByMesAnio(mesAnteriorStr).orElse(null);

            // Si no existe o no ha sido actualizado recientemente, procesarlo
            if (billingAnterior == null) {
                log.info("Creando registro de cierre para mes anterior: {}", mesAnteriorStr);
                billingAnterior = new MonthlyBilling(mesAnteriorStr);
                billingAnterior = billingRepository.save(billingAnterior);
                actualizarContadoresMes(billingAnterior);
            } else if (billingAnterior.getFechaActualizacion() == null ||
                       billingAnterior.getFechaActualizacion().toLocalDate().isBefore(hoy.minusDays(1))) {
                // Si fue actualizado hace mas de 1 dia, verificar de nuevo
                log.debug("Actualizando contadores del mes anterior: {}", mesAnteriorStr);
                actualizarContadoresMes(billingAnterior);
            }
        } catch (Exception e) {
            log.warn("Error en verificacion perezosa de mes anterior: {}", e.getMessage());
        }
    }

    /**
     * Obtiene el resumen de facturacion de un mes especifico.
     */
    public BillingSummary obtenerResumenMes(String mesAnio) {
        BillingSummary summary = new BillingSummary();
        summary.setMesAnio(mesAnio);

        try {
            // Verificar si SUNAT esta activo
            String sunatModo = configService.getSunatModo();
            summary.setSunatActivo("ACTIVO".equals(sunatModo));

            // Obtener o crear registro del mes
            MonthlyBilling billing = obtenerOCrearRegistroMes(mesAnio);

            // Actualizar contadores desde la BD de ventas
            actualizarContadoresMes(billing);

            summary.setComprobantesEmitidos(billing.getCantidadComprobantes());
            summary.setBoletas(billing.getCantidadBoletas());
            summary.setFacturas(billing.getCantidadFacturas());
            summary.setCostoEstimado(billing.getMontoCalculado());

            // Calcular comprobantes gratis restantes
            int gratisRestantes = Math.max(0, LIMITE_GRATIS - billing.getCantidadComprobantes());
            summary.setComprobantesGratisRestantes(gratisRestantes);

            // Rango actual
            summary.setRangoActual(obtenerRangoActual(billing.getCantidadComprobantes()));

            // Verificar deudas pendientes de meses anteriores
            List<MonthlyBilling> deudas = billingRepository.findMesesConDeudaPendiente();
            // Excluir el mes actual de las deudas
            deudas.removeIf(d -> d.getMesAnio().equals(mesAnio));

            summary.setTieneDeudaPendiente(!deudas.isEmpty());
            summary.setMesesConDeuda(deudas.size());
            summary.setDeudaTotal(deudas.stream()
                    .map(MonthlyBilling::getMontoCalculado)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            // Mensaje informativo
            if (billing.getCantidadComprobantes() <= LIMITE_GRATIS) {
                summary.setMensaje("Dentro del rango gratuito. " + gratisRestantes + " comprobantes gratis restantes.");
            } else {
                summary.setMensaje("Has superado el limite gratuito. Costo actual: S/ " +
                        billing.getMontoCalculado().toString());
            }

        } catch (Exception e) {
            log.error("Error obteniendo resumen del mes {}: {}", mesAnio, e.getMessage(), e);
            summary.setMensaje("Error al calcular: " + e.getMessage());
        }

        return summary;
    }

    /**
     * Obtiene o crea el registro de facturacion para un mes.
     */
    @Transactional
    public MonthlyBilling obtenerOCrearRegistroMes(String mesAnio) {
        return billingRepository.findByMesAnio(mesAnio)
                .orElseGet(() -> {
                    MonthlyBilling nuevo = new MonthlyBilling(mesAnio);
                    return billingRepository.save(nuevo);
                });
    }

    /**
     * Actualiza los contadores del mes consultando las ventas reales.
     */
    @Transactional
    public void actualizarContadoresMes(MonthlyBilling billing) {
        // Parsear mes-anio para obtener rango de fechas
        String[] partes = billing.getMesAnio().split("-");
        int mes = Integer.parseInt(partes[0]);
        int anio = Integer.parseInt(partes[1]);

        LocalDate inicioMes = LocalDate.of(anio, mes, 1);
        LocalDate finMes = inicioMes.withDayOfMonth(inicioMes.lengthOfMonth());

        // Contar comprobantes electronicos
        List<Object[]> conteos = ventaRepository.countByTipoComprobanteAndPeriodo(inicioMes, finMes);

        int boletas = 0;
        int facturas = 0;

        for (Object[] row : conteos) {
            String tipo = (String) row[0];
            long cantidad = (Long) row[1];

            if ("BOLETA".equalsIgnoreCase(tipo)) {
                boletas = (int) cantidad;
            } else if ("FACTURA".equalsIgnoreCase(tipo)) {
                facturas = (int) cantidad;
            }
        }

        int total = boletas + facturas;

        billing.setCantidadBoletas(boletas);
        billing.setCantidadFacturas(facturas);
        billing.setCantidadComprobantes(total);

        // Calcular costo
        BigDecimal costo = calcularCostoPorCantidad(total);
        billing.setMontoCalculado(costo);

        // Actualizar estado
        if (costo.compareTo(BigDecimal.ZERO) == 0) {
            billing.setEstadoPago(EstadoPago.GRATIS);
        } else if (billing.getEstadoPago() == EstadoPago.GRATIS) {
            // Si antes era gratis y ahora tiene costo, cambiar a pendiente
            billing.setEstadoPago(EstadoPago.PENDIENTE);
        }

        billingRepository.save(billing);
    }

    /**
     * Calcula el costo basado en la cantidad de comprobantes.
     */
    private BigDecimal calcularCostoPorCantidad(int cantidad) {
        for (int[] rango : TABLA_PRECIOS) {
            if (cantidad >= rango[0] && cantidad <= rango[1]) {
                return BigDecimal.valueOf(rango[2]);
            }
        }
        return BigDecimal.valueOf(TABLA_PRECIOS[TABLA_PRECIOS.length - 1][2]);
    }

    /**
     * Obtiene la descripcion del rango actual.
     */
    private String obtenerRangoActual(int cantidad) {
        for (int[] rango : TABLA_PRECIOS) {
            if (cantidad >= rango[0] && cantidad <= rango[1]) {
                if (rango[1] == Integer.MAX_VALUE) {
                    return rango[0] + "+";
                }
                return rango[0] + " - " + rango[1];
            }
        }
        return "Desconocido";
    }

    /**
     * Marca un mes como pagado.
     */
    @Transactional
    public boolean marcarComoPagado(String mesAnio, String observaciones) {
        try {
            MonthlyBilling billing = billingRepository.findByMesAnio(mesAnio)
                    .orElse(null);

            if (billing == null) {
                log.warn("No se encontro registro para el mes {}", mesAnio);
                return false;
            }

            billing.setEstadoPago(EstadoPago.PAGADO);
            billing.setFechaPago(LocalDateTime.now());
            billing.setObservaciones(observaciones);

            billingRepository.save(billing);
            log.info("Mes {} marcado como PAGADO", mesAnio);

            return true;

        } catch (Exception e) {
            log.error("Error marcando mes {} como pagado: {}", mesAnio, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Obtiene el historial de facturacion.
     */
    public List<MonthlyBilling> obtenerHistorial() {
        return billingRepository.findAllByOrderByMesAnioDesc();
    }

    /**
     * Obtiene solo los meses con deuda pendiente.
     */
    public List<MonthlyBilling> obtenerMesesConDeuda() {
        return billingRepository.findMesesConDeudaPendiente();
    }

    /**
     * Verifica si hay deuda pendiente de meses anteriores.
     */
    public boolean hayDeudaPendiente() {
        String mesActual = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-yyyy"));
        List<MonthlyBilling> deudas = billingRepository.findMesesConDeudaPendiente();
        // Excluir mes actual
        return deudas.stream().anyMatch(d -> !d.getMesAnio().equals(mesActual));
    }

    /**
     * Obtiene la deuda total pendiente.
     */
    public BigDecimal obtenerDeudaTotal() {
        return billingRepository.sumDeudaTotal();
    }

    /**
     * Obtiene estadisticas para el widget del dashboard.
     */
    public Map<String, Object> obtenerEstadisticasDashboard() {
        Map<String, Object> stats = new HashMap<>();

        BillingSummary summary = obtenerResumenMesActual();

        stats.put("mesAnio", summary.getMesAnio());
        stats.put("comprobantesEmitidos", summary.getComprobantesEmitidos());
        stats.put("boletas", summary.getBoletas());
        stats.put("facturas", summary.getFacturas());
        stats.put("costoEstimado", summary.getCostoEstimado());
        stats.put("comprobantesGratisRestantes", summary.getComprobantesGratisRestantes());
        stats.put("limiteGratis", LIMITE_GRATIS);
        stats.put("rangoActual", summary.getRangoActual());
        stats.put("sunatActivo", summary.isSunatActivo());
        stats.put("mensaje", summary.getMensaje());

        // Info de deudas
        stats.put("tieneDeudaPendiente", summary.isTieneDeudaPendiente());
        stats.put("mesesConDeuda", summary.getMesesConDeuda());
        stats.put("deudaTotal", summary.getDeudaTotal());

        // Porcentaje de uso del limite gratis
        int porcentaje = Math.min(100, (summary.getComprobantesEmitidos() * 100) / LIMITE_GRATIS);
        stats.put("porcentajeGratisUsado", porcentaje);

        return stats;
    }

    /**
     * Metodo para ejecutar al inicio de cada mes (puede ser llamado por un Scheduler).
     * Cierra el mes anterior y crea el registro del nuevo mes.
     */
    @Transactional
    public void procesarCambioMes() {
        LocalDate hoy = LocalDate.now();
        LocalDate mesAnterior = hoy.minusMonths(1);

        String mesAnteriorStr = mesAnterior.format(DateTimeFormatter.ofPattern("MM-yyyy"));
        String mesActualStr = hoy.format(DateTimeFormatter.ofPattern("MM-yyyy"));

        // Asegurar que el mes anterior tenga sus contadores actualizados
        MonthlyBilling billingAnterior = obtenerOCrearRegistroMes(mesAnteriorStr);
        actualizarContadoresMes(billingAnterior);

        // Crear registro para el nuevo mes
        obtenerOCrearRegistroMes(mesActualStr);

        log.info("Cambio de mes procesado. Anterior: {}, Actual: {}", mesAnteriorStr, mesActualStr);
    }
}
