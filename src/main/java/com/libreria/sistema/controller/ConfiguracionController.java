package com.libreria.sistema.controller;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.service.ConfiguracionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import java.util.Base64;

@Controller
@RequestMapping("/configuracion")
@PreAuthorize("hasPermission(null, 'CONFIGURACION_VER')")
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    public ConfiguracionController(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @GetMapping
    public String index() {
        return "redirect:/configuracion/general";
    }

    @GetMapping("/general")
    public String general(Model model) {
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "configuracion/general";
    }

    /**
     * Método OPTIMIZADO: Delega la lógica de actualización al servicio.
     */
    @PostMapping("/general/guardar")
    public String guardarGeneral(@ModelAttribute Configuracion configForm,
                                 @RequestParam(value = "fileLogo", required = false) MultipartFile fileLogo,
                                 RedirectAttributes attributes) {
        try {
            // 1. Procesamiento de Archivo (Logo) - Capa de Presentación
            if (fileLogo != null && !fileLogo.isEmpty()) {
                byte[] bytes = fileLogo.getBytes();
                String base64 = Base64.getEncoder().encodeToString(bytes);
                configForm.setLogoBase64(base64);
            }

            // 2. Delegar actualización al Servicio (Transaccional)
            // Ya no hacemos el mapeo manual aquí, el servicio se encarga.
            configuracionService.actualizarConfiguracion(configForm);
            
            attributes.addFlashAttribute("success", "Configuración actualizada correctamente");
        } catch (Exception e) {
            e.printStackTrace();
            attributes.addFlashAttribute("error", "Error al guardar: " + e.getMessage());
        }
        return "redirect:/configuracion/general";
    }

    @PostMapping("/general/colores-default")
    @ResponseBody
    public ResponseEntity<String> restaurarColores() {
        try {
            configuracionService.restaurarColoresPorDefecto();
            return ResponseEntity.ok("Colores restaurados correctamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}