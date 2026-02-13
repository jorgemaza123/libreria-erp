package com.libreria.sistema.controller;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.service.ConfiguracionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
@RequiredArgsConstructor
public class PwaController {

    private final ConfiguracionService configuracionService;

    /**
     * Manifest dinámico: usa nombre de empresa y color primario de la configuración.
     * Servido como JSON con Content-Type application/manifest+json.
     */
    @GetMapping("/manifest.json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> manifest() {
        Configuracion config = configuracionService.obtenerConfiguracion();

        String nombre = config.getNombreEmpresa() != null ? config.getNombreEmpresa() : "Sistema ERP";
        String color = config.getColorPrimario() != null ? config.getColorPrimario() : "#007bff";

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", nombre);
        manifest.put("short_name", nombre.length() > 12 ? nombre.substring(0, 12) : nombre);
        manifest.put("description", "Sistema de gestión empresarial");
        manifest.put("start_url", "/");
        manifest.put("scope", "/");
        manifest.put("display", "standalone");
        manifest.put("orientation", "any");
        manifest.put("theme_color", color);
        manifest.put("background_color", "#ffffff");

        // Iconos usando el logo existente
        manifest.put("icons", List.of(
                Map.of("src", "/images/logo.png", "sizes", "192x192", "type", "image/png", "purpose", "any maskable"),
                Map.of("src", "/favicon.ico", "sizes", "32x32", "type", "image/x-icon")
        ));

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/manifest+json"))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(manifest);
    }

    /**
     * Página offline auto-contenida (sin Thymeleaf, sin dependencias externas).
     */
    @GetMapping("/offline.html")
    public String offlinePage() {
        return "pwa/offline";
    }
}
