package com.libreria.sistema.config;

import com.libreria.sistema.service.SunatBillingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduler para tareas automaticas relacionadas con la facturacion SUNAT.
 * - Cierre automatico de mes el dia 1 a las 00:05
 * - Verificacion de meses sin cerrar al iniciar la aplicacion
 */
@Component
@Slf4j
public class BillingScheduler {

    private final SunatBillingService billingService;

    public BillingScheduler(SunatBillingService billingService) {
        this.billingService = billingService;
    }

    /**
     * Ejecuta el cierre de mes automaticamente el dia 1 de cada mes a las 00:05.
     * Cron: segundo minuto hora dia-del-mes mes dia-de-semana
     * "0 5 0 1 * *" = A las 00:05:00 del dia 1 de cada mes
     */
    @Scheduled(cron = "0 5 0 1 * *")
    public void cierreMesAutomatico() {
        log.info("=== INICIO CIERRE DE MES AUTOMATICO - {} ===", LocalDateTime.now());

        try {
            billingService.procesarCambioMes();
            log.info("=== CIERRE DE MES COMPLETADO EXITOSAMENTE ===");
        } catch (Exception e) {
            log.error("ERROR en cierre de mes automatico: {}", e.getMessage(), e);
        }
    }

    /**
     * Verifica cada hora si hay meses pendientes de cerrar.
     * Esto actua como respaldo por si el servidor no estaba activo el dia 1.
     * Cron: "0 0 * * * *" = Cada hora en punto
     */
    @Scheduled(cron = "0 0 * * * *")
    public void verificarMesesPendientes() {
        try {
            // Solo verificar si estamos en los primeros 5 dias del mes
            LocalDate hoy = LocalDate.now();
            if (hoy.getDayOfMonth() <= 5) {
                verificarYCerrarMesAnterior();
            }
        } catch (Exception e) {
            log.error("Error verificando meses pendientes: {}", e.getMessage());
        }
    }

    /**
     * Verifica si el mes anterior fue cerrado, si no, lo cierra.
     * Este metodo tambien puede ser llamado desde otros servicios (verificacion perezosa).
     */
    public void verificarYCerrarMesAnterior() {
        LocalDate hoy = LocalDate.now();
        LocalDate mesAnterior = hoy.minusMonths(1);
        String mesAnteriorStr = mesAnterior.format(DateTimeFormatter.ofPattern("MM-yyyy"));

        log.debug("Verificando si el mes {} necesita cierre...", mesAnteriorStr);

        try {
            // Asegurar que el mes anterior tenga sus contadores actualizados
            var billing = billingService.obtenerOCrearRegistroMes(mesAnteriorStr);

            // Si el registro tiene 0 comprobantes pero hay ventas en ese mes, actualizar
            if (billing.getCantidadComprobantes() == 0 ||
                billing.getFechaActualizacion() == null ||
                billing.getFechaActualizacion().toLocalDate().isBefore(hoy.minusDays(1))) {

                billingService.actualizarContadoresMes(billing);
                log.info("Mes {} verificado y actualizado. Comprobantes: {}, Monto: {}",
                        mesAnteriorStr,
                        billing.getCantidadComprobantes(),
                        billing.getMontoCalculado());
            }
        } catch (Exception e) {
            log.error("Error verificando mes anterior {}: {}", mesAnteriorStr, e.getMessage());
        }
    }
}
