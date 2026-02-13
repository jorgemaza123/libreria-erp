package com.libreria.sistema.controller;

import com.libreria.sistema.service.ConexionMovilService;
import com.libreria.sistema.util.NetworkUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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
     * Vista principal simplificada para el usuario final.
     */
    @GetMapping
    public String vistaConexionMovil(Model model) {
        Map<String, Object> info = conexionMovilService.obtenerInfoConexion();

        model.addAttribute("serverUrl", info.get("url"));
        model.addAttribute("serverIp", info.get("ip"));
        model.addAttribute("serverPort", info.get("port"));
        model.addAttribute("sslEnabled", info.get("sslEnabled"));
        model.addAttribute("allIps", info.get("allIps"));
        model.addAttribute("allUrls", info.get("allUrls"));
        model.addAttribute("interfaces", NetworkUtils.getAllNetworkInfo());

        // Verificación de puerto y firewall
        Map<String, Object> portCheck = conexionMovilService.verificarPuerto();
        model.addAttribute("portCheck", portCheck);

        return "configuracion/red";
    }

    /**
     * Genera imagen QR dinámicamente como PNG.
     * No requiere internet — generación local con ZXing.
     * No cachea — recalcula IP cada vez.
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
     * API REST: información de conexión completa.
     * Recalcula IP dinámicamente en cada llamada.
     */
    @GetMapping("/api/info")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiInfo() {
        try {
            Map<String, Object> info = conexionMovilService.obtenerInfoConexion();
            info.put("success", true);
            info.put("portCheck", conexionMovilService.verificarPuerto());
            info.put("interfaces", NetworkUtils.getAllNetworkInfo());
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
     * API REST: QR como Base64 data URI (para embeber directamente en HTML).
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

            // URLs alternativas
            result.put("allUrls", conexionMovilService.obtenerInfoConexion().get("allUrls"));

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
