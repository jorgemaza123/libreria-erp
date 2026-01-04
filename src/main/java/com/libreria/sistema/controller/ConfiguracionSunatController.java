package com.libreria.sistema.controller;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.ConfiguracionSunat;
import com.libreria.sistema.repository.ConfiguracionSunatRepository;
import com.libreria.sistema.service.FacturacionElectronicaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/configuracion/sunat")
public class ConfiguracionSunatController {

    private final ConfiguracionSunatRepository configuracionRepo;
    private final FacturacionElectronicaService facturacionService;

    public ConfiguracionSunatController(
            ConfiguracionSunatRepository configuracionRepo,
            FacturacionElectronicaService facturacionService) {
        this.configuracionRepo = configuracionRepo;
        this.facturacionService = facturacionService;
    }

    /**
     * Vista de configuración de SUNAT
     */
    @GetMapping
    public String mostrarConfiguracion(Model model) {
        ConfiguracionSunat config = configuracionRepo.findFirstByOrderByIdDesc()
                .orElse(new ConfiguracionSunat());

        // Verificar si la configuración está completa
        boolean configCompleta = config.getId() != null &&
                                 config.getRucEmisor() != null &&
                                 config.getRazonSocialEmisor() != null &&
                                 config.getDireccionFiscal() != null &&
                                 config.getUrlApiSunat() != null &&
                                 config.getTokenApiSunat() != null;

        model.addAttribute("config", config);
        model.addAttribute("isActiva", facturacionService.isFacturacionElectronicaActiva());
        model.addAttribute("configCompleta", configCompleta);

        return "configuracion/sunat";
    }

    /**
     * Guardar o actualizar configuración de SUNAT
     */
    @PostMapping("/guardar")
    @Auditable(modulo = "CONFIGURACION", accion = "MODIFICAR", descripcion = "Configuración SUNAT")
    public String guardarConfiguracion(
            @ModelAttribute ConfiguracionSunat config,
            RedirectAttributes redirectAttributes) {

        try {
            // Validaciones
            if (config.getRucEmisor() == null || config.getRucEmisor().length() != 11) {
                redirectAttributes.addFlashAttribute("error", "El RUC debe tener 11 dígitos");
                return "redirect:/configuracion/sunat";
            }

            if (config.getUbigeoEmisor() != null && config.getUbigeoEmisor().length() != 6) {
                redirectAttributes.addFlashAttribute("error", "El Ubigeo debe tener 6 dígitos");
                return "redirect:/configuracion/sunat";
            }

            // Si existe configuración previa, actualizar; si no, crear nueva
            ConfiguracionSunat configExistente = configuracionRepo.findFirstByOrderByIdDesc()
                    .orElse(null);

            if (configExistente != null) {
                config.setId(configExistente.getId());
            }

            configuracionRepo.save(config);

            redirectAttributes.addFlashAttribute("success",
                    "Configuración de SUNAT guardada correctamente");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al guardar configuración: " + e.getMessage());
        }

        return "redirect:/configuracion/sunat";
    }

    /**
     * Activar/Desactivar facturación electrónica
     */
    @PostMapping("/toggle")
    @ResponseBody
    @Auditable(modulo = "CONFIGURACION", accion = "MODIFICAR", descripcion = "Toggle facturación electrónica")
    public String toggleFacturacionElectronica(@RequestParam Boolean activo) {
        try {
            ConfiguracionSunat config = configuracionRepo.findFirstByOrderByIdDesc()
                    .orElseThrow(() -> new RuntimeException(
                            "Debe configurar SUNAT antes de activar facturación electrónica"));

            config.setFacturaElectronicaActiva(activo);
            configuracionRepo.save(config);

            return activo ? "ACTIVADO" : "DESACTIVADO";

        } catch (Exception e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

    /**
     * Sincronizar correlativos con SUNAT
     */
    @PostMapping("/sincronizar")
    @ResponseBody
    public String sincronizar() {
        try {
            String resultado = facturacionService.sincronizarConSunat();
            return resultado;
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Test de conexión con APISUNAT (endpoint de validación)
     */
    @PostMapping("/test-conexion")
    @ResponseBody
    public String testConexion() {
        try {
            ConfiguracionSunat config = facturacionService.obtenerConfiguracionActual();

            if (config == null) {
                return "ERROR: No hay configuración";
            }

            if (config.getTokenApiSunat() == null || config.getTokenApiSunat().isEmpty()) {
                return "ERROR: Token no configurado";
            }

            // TODO: Implementar llamada real al endpoint de test de APISUNAT
            // Por ahora retornamos OK si la configuración existe
            return "OK: Configuración lista. Token: " +
                   config.getTokenApiSunat().substring(0, Math.min(10, config.getTokenApiSunat().length())) + "...";

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
