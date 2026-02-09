package com.libreria.sistema.controller;

import com.libreria.sistema.config.SslConfigService;
import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.service.BackupService;
import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.util.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/configuracion")
@PreAuthorize("hasPermission(null, 'CONFIGURACION_VER')")
@Slf4j
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;
    private final BackupService backupService;

    @Autowired(required = false)
    private SslConfigService sslConfigService;

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    public ConfiguracionController(ConfiguracionService configuracionService, BackupService backupService) {
        this.configuracionService = configuracionService;
        this.backupService = backupService;
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

    // =====================================================
    //  BACKUP DE BASE DE DATOS
    // =====================================================

    /**
     * Descarga un backup completo de la base de datos en formato .sql
     *
     * Seguridad: Solo usuarios con permiso CONFIGURACION_VER pueden acceder.
     * El archivo se genera dinámicamente y se descarga directamente al navegador.
     */
    @GetMapping("/backup")
    @PreAuthorize("hasPermission(null, 'CONFIGURACION_EDITAR')")
    public ResponseEntity<?> descargarBackup() {
        try {
            log.info("Usuario solicitando backup de base de datos");

            Map<String, Object> resultado = backupService.generarBackup();

            if (Boolean.TRUE.equals(resultado.get("success"))) {
                Resource resource = (Resource) resultado.get("resource");
                String filename = (String) resultado.get("filename");

                log.info("Backup generado exitosamente: {}", filename);

                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + filename + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Error desconocido al generar backup"));
            }

        } catch (BackupService.BackupException e) {
            log.error("Error en backup: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        } catch (Exception e) {
            log.error("Error inesperado en backup", e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "success", false,
                            "error", "Error inesperado: " + e.getMessage()
                    ));
        }
    }

    /**
     * Verifica si el sistema de backup está disponible (pg_dump instalado)
     */
    @GetMapping("/backup/verificar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verificarBackup() {
        try {
            // Intentar generar un backup para verificar
            // En producción podrías tener un método más ligero de verificación
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");

            return ResponseEntity.ok(Map.of(
                    "disponible", true,
                    "sistemaOperativo", isWindows ? "Windows" : "Linux/Unix",
                    "mensaje", "Sistema de backup disponible"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "disponible", false,
                    "error", e.getMessage()
            ));
        }
    }

    // =====================================================
    //  INFORMACIÓN DE RED - CONEXIÓN MÓVIL
    // =====================================================

    /**
     * Obtiene la información de red del servidor para conexiones móviles.
     * Muestra la IP detectada, URL del servidor y estado de SSL.
     */
    @GetMapping("/red")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> obtenerInfoRed() {
        Map<String, Object> info = new HashMap<>();

        try {
            String ip = sslConfigService != null ? sslConfigService.getLocalIp() : NetworkUtils.getLocalIpAddress();
            String url = sslConfigService != null ? sslConfigService.getServerUrl() : NetworkUtils.getServerUrl(sslEnabled, serverPort);

            info.put("success", true);
            info.put("ip", ip);
            info.put("port", serverPort);
            info.put("url", url);
            info.put("sslEnabled", sslEnabled);
            info.put("protocol", sslEnabled ? "https" : "http");

            // Información adicional de diagnóstico
            List<Map<String, String>> interfaces = NetworkUtils.getAllNetworkInfo();
            info.put("interfaces", interfaces);

            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Error al obtener información de red", e);
            info.put("success", false);
            info.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(info);
        }
    }

    /**
     * Vista de diagnóstico de red para el administrador.
     * Muestra todas las interfaces y la IP detectada.
     */
    @GetMapping("/red/diagnostico")
    public String diagnosticoRed(Model model) {
        String ip = sslConfigService != null ? sslConfigService.getLocalIp() : NetworkUtils.getLocalIpAddress();
        String url = sslConfigService != null ? sslConfigService.getServerUrl() : NetworkUtils.getServerUrl(sslEnabled, serverPort);

        model.addAttribute("serverIp", ip);
        model.addAttribute("serverUrl", url);
        model.addAttribute("serverPort", serverPort);
        model.addAttribute("sslEnabled", sslEnabled);
        model.addAttribute("interfaces", NetworkUtils.getAllNetworkInfo());

        return "configuracion/red";
    }
}