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
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    public ConfiguracionController(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    /**
     * Redirige a la vista de configuración general
     */
    @GetMapping
    public String index() {
        return "redirect:/configuracion/general";
    }

    /**
     * Nueva vista con tabs (configuración general completa)
     */
    @GetMapping("/general")
    public String general(Model model) {
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "configuracion/general";
    }

    /**
     * Guardar configuración completa
     */
    @PostMapping("/general/guardar")
    public String guardarGeneral(Configuracion config,
                                 @RequestParam(value = "fileLogo", required = false) MultipartFile fileLogo,
                                 RedirectAttributes attributes) {
        try {
            // Recuperar config actual para no perder el logo si no suben uno nuevo
            Configuracion actual = configuracionService.obtenerConfiguracion();

            if (fileLogo != null && !fileLogo.isEmpty()) {
                // Convertir imagen a Base64
                byte[] bytes = fileLogo.getBytes();
                String base64 = Base64.getEncoder().encodeToString(bytes);
                config.setLogoBase64(base64);
            } else {
                // Mantener el logo anterior
                config.setLogoBase64(actual.getLogoBase64());
            }

            configuracionService.guardarConfiguracion(config);
            attributes.addFlashAttribute("success", "Configuración guardada exitosamente");
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "Error al guardar configuración: " + e.getMessage());
        }
        return "redirect:/configuracion/general";
    }

    /**
     * Upload de logo (AJAX)
     */
    @PostMapping("/general/logo")
    @ResponseBody
    public ResponseEntity<?> uploadLogo(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("No se seleccionó archivo");
            }

            byte[] bytes = file.getBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);

            Configuracion config = configuracionService.obtenerConfiguracion();
            config.setLogoBase64(base64);
            configuracionService.guardarConfiguracion(config);

            return ResponseEntity.ok().body("Logo actualizado correctamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Restaurar colores por defecto
     */
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

    /**
     * Método antiguo - mantener compatibilidad
     */
    @PostMapping("/guardar")
    public String guardar(Configuracion config,
                          @RequestParam(value = "fileLogo", required = false) MultipartFile fileLogo,
                          RedirectAttributes attributes) {
        try {
            Configuracion actual = configuracionService.obtenerConfiguracion();

            if (fileLogo != null && !fileLogo.isEmpty()) {
                byte[] bytes = fileLogo.getBytes();
                String base64 = Base64.getEncoder().encodeToString(bytes);
                config.setLogoBase64(base64);
            } else {
                config.setLogoBase64(actual.getLogoBase64());
            }

            configuracionService.guardarConfiguracion(config);
            attributes.addFlashAttribute("success", "Datos y Logo actualizados");
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "Error al subir logo: " + e.getMessage());
        }
        return "redirect:/configuracion";
    }
}