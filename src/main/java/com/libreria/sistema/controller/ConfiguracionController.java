package com.libreria.sistema.controller;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.service.ConfiguracionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.util.Base64;

@Controller
@RequestMapping("/configuracion")
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    public ConfiguracionController(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "configuracion/formulario";
    }

    // MODIFICAR EL MÉTODO GUARDAR
@PostMapping("/guardar")
public String guardar(Configuracion config, 
                      @RequestParam("fileLogo") MultipartFile fileLogo, // Nuevo parámetro
                      RedirectAttributes attributes) {
    try {
        // Recuperar config actual para no perder el logo si no suben uno nuevo
        Configuracion actual = configuracionService.obtenerConfiguracion();
        
        if (!fileLogo.isEmpty()) {
            // Convertir imagen a Base64
            byte[] bytes = fileLogo.getBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            config.setLogoBase64(base64);
        } else {
            // Mantener el logo anterior
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