package com.libreria.sistema.controller;

import com.libreria.sistema.service.ConexionMovilService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/conexion-movil")
@PreAuthorize("hasPermission(null, 'CONFIGURACION_VER')")
@RequiredArgsConstructor
@Slf4j
public class ConexionMovilController {

    private final ConexionMovilService conexionMovilService;

    /**
     * Vista principal — muestra la IP guardada en BD (no recalcula).
     */
    @GetMapping
    public String vistaConexionMovil(Model model) {
        String ip = conexionMovilService.obtenerIpConfigurada();
        String serverUrl = conexionMovilService.generarServerUrl();

        model.addAttribute("serverUrl", serverUrl);
        model.addAttribute("serverIp", ip);
        model.addAttribute("serverPort", conexionMovilService.getServerPort());
        model.addAttribute("sslEnabled", conexionMovilService.isSslEnabled());

        return "configuracion/red";
    }

    /**
     * Genera imagen QR con la URL fija guardada en BD.
     */
    @GetMapping("/qr.png")
    @ResponseBody
    public ResponseEntity<byte[]> generarQrImage() {
        String url = conexionMovilService.generarServerUrl();
        byte[] qrBytes = conexionMovilService.generarQrCode(url, 300);

        if (qrBytes.length == 0) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noCache())
                .body(qrBytes);
    }

    /**
     * Recalcular IP automáticamente y guardar en BD.
     */
    @PostMapping("/recalcular-ip")
    @PreAuthorize("hasPermission(null, 'CONFIGURACION_EDITAR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recalcularIp() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String nuevaIp = conexionMovilService.recalcularIp();
            result.put("success", true);
            result.put("ip", nuevaIp);
            result.put("url", conexionMovilService.generarServerUrl());
            result.put("message", "IP detectada y guardada: " + nuevaIp);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error al recalcular IP", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Guardar IP manualmente escrita por el admin.
     */
    @PostMapping("/guardar-ip")
    @PreAuthorize("hasPermission(null, 'CONFIGURACION_EDITAR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> guardarIpManual(@RequestParam String ip) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Validación básica de formato IPv4
        if (ip == null || !ip.trim().matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            result.put("success", false);
            result.put("error", "Formato de IP inválido. Use el formato: 192.168.1.100");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            conexionMovilService.guardarIpManual(ip);
            result.put("success", true);
            result.put("ip", ip.trim());
            result.put("url", conexionMovilService.generarServerUrl());
            result.put("message", "IP guardada correctamente: " + ip.trim());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error al guardar IP manual", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * API REST: información de conexión.
     */
    @GetMapping("/api/info")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiInfo() {
        try {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("success", true);
            info.put("url", conexionMovilService.generarServerUrl());
            info.put("ip", conexionMovilService.obtenerIpConfigurada());
            info.put("port", conexionMovilService.getServerPort());
            info.put("sslEnabled", conexionMovilService.isSslEnabled());
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Error al obtener info de conexión", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * API REST: QR como Base64 data URI.
     */
    @GetMapping("/api/qr-base64")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiQrBase64() {
        try {
            String url = conexionMovilService.generarServerUrl();
            String qrBase64 = conexionMovilService.generarQrCodeBase64(url, 300);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("url", url);
            result.put("qrBase64", qrBase64);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error al generar QR base64", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
