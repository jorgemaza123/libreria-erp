package com.libreria.sistema.controller;

import com.libreria.sistema.service.LicenseValidationService;
import com.libreria.sistema.service.LicenseValidationService.LicenseInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador para la gestión de licencias del sistema.
 */
@Controller
@RequestMapping("/licencia")
@Slf4j
public class LicenciaController {

    private final LicenseValidationService licenseService;

    public LicenciaController(LicenseValidationService licenseService) {
        this.licenseService = licenseService;
    }

    /**
     * Vista principal de licencia/activación.
     */
    @GetMapping
    public String vistaLicencia(Model model) {
        LicenseInfo info = licenseService.validarLicencia();

        model.addAttribute("licenseInfo", info);
        model.addAttribute("installationUUID", licenseService.getInstallationUUID());
        model.addAttribute("sunatActivo", licenseService.isSunatActivo());

        return "licencia/index";
    }

    /**
     * Activar/Registrar una nueva licencia.
     */
    @PostMapping("/activar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> activarLicencia(@RequestParam String licenseHash) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (licenseHash == null || licenseHash.isBlank()) {
                response.put("success", false);
                response.put("mensaje", "Debe ingresar el código de licencia.");
                return ResponseEntity.badRequest().body(response);
            }

            // Limpiar el hash (quitar espacios, saltos de línea)
            licenseHash = licenseHash.trim().replaceAll("\\s+", "");

            LicenseInfo info = licenseService.registrarLicencia(licenseHash);

            if (info.getEstado() == LicenseValidationService.EstadoLicencia.ACTIVO ||
                info.getEstado() == LicenseValidationService.EstadoLicencia.EN_GRACIA) {

                response.put("success", true);
                response.put("mensaje", "Licencia activada correctamente.");
                response.put("estado", info.getEstado().name());
                response.put("fechaVencimiento", info.getFechaVencimiento().toString());
                response.put("diasRestantes", info.getDiasRestantes());
                response.put("sunatActivo", info.isSunatActivo());
                response.put("nivelPlan", info.getNivelPlan());

                log.info("Licencia activada exitosamente. Plan: {}", info.getNivelPlan());
                return ResponseEntity.ok(response);

            } else {
                response.put("success", false);
                response.put("mensaje", info.getMensaje());
                response.put("estado", info.getEstado().name());
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Error activando licencia: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("mensaje", "Error al procesar la licencia: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Desactivar SUNAT para trabajar en modo offline.
     */
    @PostMapping("/desactivar-sunat")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> desactivarSunat() {
        Map<String, Object> response = new HashMap<>();

        try {
            licenseService.desactivarSunat();
            response.put("success", true);
            response.put("mensaje", "SUNAT desactivado. Ahora trabajará en modo offline.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error desactivando SUNAT: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("mensaje", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Reactivar SUNAT (solo si la licencia lo permite).
     */
    @PostMapping("/activar-sunat")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> activarSunat() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean activado = licenseService.activarSunat();

            if (activado) {
                response.put("success", true);
                response.put("mensaje", "SUNAT activado correctamente.");
            } else {
                response.put("success", false);
                response.put("mensaje", "No se puede activar SUNAT. Verifique su licencia.");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error activando SUNAT: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("mensaje", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * API para obtener estado de licencia.
     */
    @GetMapping("/api/estado")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> obtenerEstado() {
        Map<String, Object> response = new HashMap<>();

        try {
            LicenseInfo info = licenseService.validarLicencia();

            response.put("estado", info.getEstado().name());
            response.put("mensaje", info.getMensaje());
            response.put("diasRestantes", info.getDiasRestantes());
            response.put("alertaPago", info.isAlertaPago());
            response.put("sunatActivo", licenseService.isSunatActivo());
            response.put("installationUUID", licenseService.getInstallationUUID());

            if (info.getFechaVencimiento() != null) {
                response.put("fechaVencimiento", info.getFechaVencimiento().toString());
            }
            if (info.getNivelPlan() != null) {
                response.put("nivelPlan", info.getNivelPlan());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error obteniendo estado: {}", e.getMessage(), e);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Generar licencia de prueba (solo para desarrollo).
     * En producción, este endpoint debería estar deshabilitado o protegido.
     */
    @GetMapping("/api/generar-prueba")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generarLicenciaPrueba(
            @RequestParam(defaultValue = "30") int dias,
            @RequestParam(defaultValue = "1") String sunat,
            @RequestParam(defaultValue = "BASICO") String plan) {

        Map<String, Object> response = new HashMap<>();

        try {
            String uuid = licenseService.getInstallationUUID();
            String fechaVencimiento = java.time.LocalDate.now().plusDays(dias).toString();

            String licenciaPlana = String.format("%s|%s|%s|%s", uuid, fechaVencimiento, sunat, plan);
            String hash = licenseService.encriptar(licenciaPlana);

            response.put("uuid", uuid);
            response.put("licenciaPlana", licenciaPlana);
            response.put("hash", hash);
            response.put("nota", "Copie el hash y úselo para activar la licencia.");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error generando licencia de prueba: {}", e.getMessage(), e);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
