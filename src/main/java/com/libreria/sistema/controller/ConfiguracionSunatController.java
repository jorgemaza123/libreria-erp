package com.libreria.sistema.controller;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.ConfiguracionSunat;
import com.libreria.sistema.repository.ConfiguracionSunatRepository;
import com.libreria.sistema.service.ConfiguracionService; // IMPORTANTE: Para el layout
import com.libreria.sistema.service.FacturacionElectronicaService;
import com.libreria.sistema.service.SystemConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/configuracion/sunat")
@Slf4j
public class ConfiguracionSunatController {

    private final ConfiguracionSunatRepository configuracionRepo;
    private final FacturacionElectronicaService facturacionService;
    private final ConfiguracionService generalConfigService;
    private final SystemConfigurationService systemConfigService;

    public ConfiguracionSunatController(
            ConfiguracionSunatRepository configuracionRepo,
            FacturacionElectronicaService facturacionService,
            ConfiguracionService generalConfigService,
            SystemConfigurationService systemConfigService) {
        this.configuracionRepo = configuracionRepo;
        this.facturacionService = facturacionService;
        this.generalConfigService = generalConfigService;
        this.systemConfigService = systemConfigService;
    }

    /**
     * Vista de configuración de SUNAT
     */
    @GetMapping
    public String mostrarConfiguracion(Model model) {
        // 1. Cargar Configuración General (NECESARIO PARA EL LAYOUT: nombreEmpresa, logo, etc.)
        model.addAttribute("config", generalConfigService.obtenerConfiguracion());

        // 2. Cargar Configuración SUNAT (Con nombre distinto para no chocar)
        ConfiguracionSunat sunatConfig = configuracionRepo.findFirstByOrderByIdDesc()
                .orElse(new ConfiguracionSunat());

        // Verificar si la configuración está completa
        boolean configCompleta = sunatConfig.getId() != null &&
                                 sunatConfig.getRucEmisor() != null &&
                                 sunatConfig.getRazonSocialEmisor() != null &&
                                 sunatConfig.getDireccionFiscal() != null &&
                                 sunatConfig.getUrlApiSunat() != null &&
                                 sunatConfig.getTokenApiSunat() != null;

        model.addAttribute("sunatConfig", sunatConfig); // Usamos 'sunatConfig'
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
            @ModelAttribute("sunatConfig") ConfiguracionSunat sunatConfig, // Recibir con el nombre correcto
            RedirectAttributes redirectAttributes) {

        try {
            // Validaciones
            if (sunatConfig.getRucEmisor() == null || sunatConfig.getRucEmisor().length() != 11) {
                redirectAttributes.addFlashAttribute("error", "El RUC debe tener 11 dígitos");
                return "redirect:/configuracion/sunat";
            }

            if (sunatConfig.getUbigeoEmisor() != null && !sunatConfig.getUbigeoEmisor().isEmpty() && sunatConfig.getUbigeoEmisor().length() != 6) {
                redirectAttributes.addFlashAttribute("error", "El Ubigeo debe tener 6 dígitos");
                return "redirect:/configuracion/sunat";
            }

            // Si existe configuración previa, recuperar ID y estado activo para no perderlos
            ConfiguracionSunat configExistente = configuracionRepo.findFirstByOrderByIdDesc()
                    .orElse(null);

            if (configExistente != null) {
                sunatConfig.setId(configExistente.getId());
                sunatConfig.setFacturaElectronicaActiva(configExistente.getFacturaElectronicaActiva());
            }

            configuracionRepo.save(sunatConfig);

            redirectAttributes.addFlashAttribute("success", "Configuración de SUNAT guardada correctamente");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al guardar configuración: " + e.getMessage());
        }

        return "redirect:/configuracion/sunat";
    }

    @PostMapping("/toggle")
    @ResponseBody
    @Auditable(modulo = "CONFIGURACION", accion = "MODIFICAR", descripcion = "Toggle facturación electrónica")
    public String toggleFacturacionElectronica(@RequestParam Boolean activo) {
        ConfiguracionSunat config = configuracionRepo.findFirstByOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException("Debe configurar SUNAT antes de activar facturación electrónica"));

        // Validar que la configuración esté completa antes de activar
        if (Boolean.TRUE.equals(activo)) {
            boolean configCompleta = config.getRucEmisor() != null && !config.getRucEmisor().isBlank()
                    && config.getRazonSocialEmisor() != null && !config.getRazonSocialEmisor().isBlank()
                    && config.getDireccionFiscal() != null && !config.getDireccionFiscal().isBlank()
                    && config.getTokenApiSunat() != null && !config.getTokenApiSunat().isBlank()
                    && config.getUrlApiSunat() != null && !config.getUrlApiSunat().isBlank();

            if (!configCompleta) {
                throw new RuntimeException("Complete la configuración SUNAT (RUC, Razón Social, Dirección, Token y URL) antes de activar.");
            }
        }

        // Guardar el estado del toggle
        config.setFacturaElectronicaActiva(activo);
        configuracionRepo.save(config);

        // Sincronizar el modo SUNAT en SystemConfiguration (fuente de verdad para billing)
        systemConfigService.setSunatModo(Boolean.TRUE.equals(activo) ? "ACTIVO" : "OFFLINE");

        // Al activar: sincronizar correlativos con SUNAT para evitar duplicados
        if (Boolean.TRUE.equals(activo)) {
            try {
                String resultadoSync = facturacionService.sincronizarConSunat();
                log.info("Sincronización SUNAT al activar toggle: {}", resultadoSync);
            } catch (Exception e) {
                // La sincronización es opcional (puede fallar si SUNAT no responde)
                // No bloqueamos la activación, pero notificamos
                log.warn("No se pudo sincronizar con SUNAT al activar (correlativos iniciados en 0): {}", e.getMessage());
                return "ACTIVADO_SIN_SYNC";
            }
        }

        return activo ? "ACTIVADO" : "DESACTIVADO";
    }

    @PostMapping("/sincronizar")
    @ResponseBody
    public String sincronizar() {
        try {
            return facturacionService.sincronizarConSunat();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @PostMapping("/test-conexion")
    @ResponseBody
    public String testConexion() {
        try {
            ConfiguracionSunat config = facturacionService.obtenerConfiguracionActual();

            if (config == null) return "ERROR: No hay configuración";
            if (config.getTokenApiSunat() == null || config.getTokenApiSunat().isEmpty()) return "ERROR: Token no configurado";

            return "OK: Configuración lista. Token: " +
                    config.getTokenApiSunat().substring(0, Math.min(10, config.getTokenApiSunat().length())) + "...";

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}