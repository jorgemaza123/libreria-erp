package com.libreria.sistema.controller;

import com.libreria.sistema.model.MonthlyBilling;
import com.libreria.sistema.service.SunatBillingService;
import com.libreria.sistema.service.SunatBillingService.BillingSummary;
import com.libreria.sistema.service.SystemConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para la gestion de facturacion SUNAT.
 */
@Controller
@RequestMapping("/configuracion/sunat-billing")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Slf4j
public class SunatBillingController {

    private final SunatBillingService billingService;
    private final SystemConfigurationService configService;

    public SunatBillingController(SunatBillingService billingService,
                                  SystemConfigurationService configService) {
        this.billingService = billingService;
        this.configService = configService;
    }

    /**
     * Vista principal de facturacion SUNAT
     */
    @GetMapping
    public String vistaFacturacion(Model model) {
        BillingSummary resumenActual = billingService.obtenerResumenMesActual();
        List<MonthlyBilling> historial = billingService.obtenerHistorial();
        List<MonthlyBilling> deudas = billingService.obtenerMesesConDeuda();

        model.addAttribute("resumen", resumenActual);
        model.addAttribute("historial", historial);
        model.addAttribute("deudas", deudas);
        model.addAttribute("sunatActivo", "ACTIVO".equals(configService.getSunatModo()));

        return "configuracion/sunat-billing";
    }

    /**
     * API: Obtener resumen del mes actual
     */
    @GetMapping("/api/resumen")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> obtenerResumen() {
        Map<String, Object> response = new HashMap<>();

        try {
            BillingSummary summary = billingService.obtenerResumenMesActual();

            response.put("success", true);
            response.put("mesAnio", summary.getMesAnio());
            response.put("comprobantes", summary.getComprobantesEmitidos());
            response.put("boletas", summary.getBoletas());
            response.put("facturas", summary.getFacturas());
            response.put("costoEstimado", summary.getCostoEstimado());
            response.put("gratisRestantes", summary.getComprobantesGratisRestantes());
            response.put("tieneDeuda", summary.isTieneDeudaPendiente());
            response.put("deudaTotal", summary.getDeudaTotal());
            response.put("mesesConDeuda", summary.getMesesConDeuda());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error obteniendo resumen: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * API: Obtener historial de facturacion
     */
    @GetMapping("/api/historial")
    @ResponseBody
    public ResponseEntity<List<MonthlyBilling>> obtenerHistorial() {
        return ResponseEntity.ok(billingService.obtenerHistorial());
    }

    /**
     * API: Marcar un mes como pagado
     */
    @PostMapping("/api/marcar-pagado")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> marcarComoPagado(
            @RequestParam String mesAnio,
            @RequestParam(required = false) String observaciones) {

        Map<String, Object> response = new HashMap<>();

        try {
            boolean exito = billingService.marcarComoPagado(mesAnio, observaciones);

            if (exito) {
                response.put("success", true);
                response.put("mensaje", "Mes " + mesAnio + " marcado como PAGADO");
                log.info("Administrador marco el mes {} como pagado", mesAnio);
            } else {
                response.put("success", false);
                response.put("mensaje", "No se encontro el registro del mes " + mesAnio);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error marcando pago: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("mensaje", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * API: Activar/Desactivar SUNAT
     */
    @PostMapping("/api/toggle-sunat")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleSunat(@RequestParam boolean activar) {
        Map<String, Object> response = new HashMap<>();

        try {
            String nuevoModo = activar ? "ACTIVO" : "OFFLINE";
            configService.setSunatModo(nuevoModo);

            response.put("success", true);
            response.put("sunatActivo", activar);
            response.put("mensaje", activar ?
                    "SUNAT activado. Los comprobantes se enviaran a SUNAT." :
                    "SUNAT desactivado. Trabajando en modo offline.");

            log.info("SUNAT cambiado a modo: {}", nuevoModo);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error cambiando modo SUNAT: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("mensaje", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * API: Verificar si hay deuda pendiente (para alertas)
     */
    @GetMapping("/api/hay-deuda")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verificarDeuda() {
        Map<String, Object> response = new HashMap<>();

        boolean hayDeuda = billingService.hayDeudaPendiente();
        response.put("hayDeuda", hayDeuda);

        if (hayDeuda) {
            response.put("deudaTotal", billingService.obtenerDeudaTotal());
            response.put("mesesPendientes", billingService.obtenerMesesConDeuda().size());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * API: Estadisticas para el dashboard
     */
    @GetMapping("/api/dashboard-stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> obtenerStatsDashboard() {
        return ResponseEntity.ok(billingService.obtenerEstadisticasDashboard());
    }
}
