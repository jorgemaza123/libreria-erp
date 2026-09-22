package com.libreria.sistema.controller;

import com.libreria.sistema.service.ConexionMovilService;
import com.libreria.sistema.service.ConexionMovilAccessTracker;
import com.libreria.sistema.config.SslConfigService;
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
import java.util.Objects;

@Controller
@RequestMapping("/conexion-movil")
@PreAuthorize("hasPermission(null, 'CONFIGURACION_VER')")
@RequiredArgsConstructor
@Slf4j
public class ConexionMovilController {

    private final ConexionMovilService conexionMovilService;
    private final ConexionMovilAccessTracker accessTracker;
    private final SslConfigService sslConfigService;

    /**
     * Vista principal — muestra la IP guardada en BD (no recalcula).
     */
    @GetMapping
    public String vistaConexionMovil(Model model) {
        String ip = conexionMovilService.obtenerIpConfigurada();
        String serverUrl = conexionMovilService.generarServerUrl();
        String accesoMovilUrl = conexionMovilService.generarAccesoMovilUrl();
        String pingUrl = conexionMovilService.generarPingUrl();
        String pingTextoUrl = conexionMovilService.generarPingTextoUrl();
        Map<String, Object> diagnostico = conexionMovilService.obtenerDiagnosticoConexion();

        model.addAttribute("serverUrl", serverUrl);
        model.addAttribute("accesoMovilUrl", accesoMovilUrl);
        model.addAttribute("pingUrl", pingUrl);
        model.addAttribute("pingTextoUrl", pingTextoUrl);
        model.addAttribute("qrCacheKey", System.currentTimeMillis());
        model.addAttribute("httpDiagnosticoEnabled", diagnostico.get("httpDiagnosticoEnabled"));
        model.addAttribute("httpDiagnosticoPort", diagnostico.get("httpDiagnosticoPort"));
        model.addAttribute("httpDiagnosticoPingUrl", diagnostico.get("httpDiagnosticoPingUrl"));
        model.addAttribute("httpDiagnosticoAccesoUrl", diagnostico.get("httpDiagnosticoAccesoUrl"));
        model.addAttribute("serverIp", ip);
        model.addAttribute("ipGuardada", diagnostico.get("ipGuardada"));
        model.addAttribute("serverPort", conexionMovilService.getServerPort());
        model.addAttribute("sslEnabled", conexionMovilService.isSslEnabled());
        model.addAttribute("networkDiagnostic", diagnostico);
        model.addAttribute("ipMovilValida", diagnostico.get("ipEfectivaValida"));
        model.addAttribute("ipGuardadaInternaDocker", diagnostico.get("ipGuardadaInternaDocker"));
        model.addAttribute("dockerDetected", diagnostico.get("docker"));
        model.addAttribute("hostIpEnv", diagnostico.get("hostIpEnv"));
        model.addAttribute("mensajeDiagnostico", diagnostico.get("mensaje"));
        model.addAttribute("mensajePuerto", diagnostico.get("mensajePuerto"));
        model.addAttribute("comandoPruebaPuerto", diagnostico.get("comandoPruebaPuerto"));
        model.addAttribute("comandoPruebaPuertoHttp", diagnostico.get("comandoPruebaPuertoHttp"));
        model.addAttribute("comandoVerFirewallWindows", diagnostico.get("comandoVerFirewallWindows"));
        model.addAttribute("comandoFirewallWindows", diagnostico.get("comandoFirewallWindows"));
        model.addAttribute("comandoDockerPuertos", diagnostico.get("comandoDockerPuertos"));
        model.addAttribute("comandoDockerRecrear", diagnostico.get("comandoDockerRecrear"));

        return "configuracion/red";
    }

    /**
     * Genera imagen QR con la URL fija guardada en BD.
     */
    @GetMapping("/qr.png")
    @ResponseBody
    public ResponseEntity<byte[]> generarQrImage() {
        String url = conexionMovilService.generarAccesoMovilUrl();
        byte[] qrBytes = conexionMovilService.generarQrCode(url, 300);

        if (qrBytes.length == 0) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(qrBytes);
    }

    @GetMapping("/qr-ping.png")
    @ResponseBody
    public ResponseEntity<byte[]> generarQrPingImage() {
        String url = conexionMovilService.generarPingUrl();
        byte[] qrBytes = conexionMovilService.generarQrCode(url, 260);

        if (qrBytes.length == 0) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(qrBytes);
    }

    @GetMapping("/qr-http-ping.png")
    @ResponseBody
    public ResponseEntity<byte[]> generarQrHttpPingImage() {
        String url = conexionMovilService.generarHttpDiagnosticoAccesoUrl();
        byte[] qrBytes = conexionMovilService.generarQrCode(url, 260);

        if (qrBytes.length == 0) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore().mustRevalidate())
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
            String ipAnterior = conexionMovilService.obtenerIpConfigurada();
            String nuevaIp = conexionMovilService.recalcularIp();
            agregarAvisoCertificado(result, ipAnterior, nuevaIp);
            result.put("success", true);
            result.put("ip", nuevaIp);
            result.put("url", conexionMovilService.generarServerUrl());
            result.put("message", "IP detectada y guardada: " + nuevaIp);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
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

        try {
            String ipAnterior = conexionMovilService.obtenerIpConfigurada();
            conexionMovilService.guardarIpManual(ip);
            String nuevaIp = ip.trim();
            agregarAvisoCertificado(result, ipAnterior, nuevaIp);
            result.put("success", true);
            result.put("ip", nuevaIp);
            result.put("url", conexionMovilService.generarServerUrl());
            result.put("message", "IP guardada correctamente: " + nuevaIp);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
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
            info.put("accesoMovilUrl", conexionMovilService.generarAccesoMovilUrl());
            info.put("ip", conexionMovilService.obtenerIpConfigurada());
            info.put("port", conexionMovilService.getServerPort());
            info.put("sslEnabled", conexionMovilService.isSslEnabled());
            info.put("diagnostico", conexionMovilService.obtenerDiagnosticoConexion());
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
            String url = conexionMovilService.generarAccesoMovilUrl();
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

    @GetMapping("/api/ultimo-acceso")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiUltimoAcceso() {
        return ResponseEntity.ok(accessTracker.obtenerResumen());
    }

    private void agregarAvisoCertificado(Map<String, Object> result, String ipAnterior, String nuevaIp) {
        boolean cambioIp = !Objects.equals(ipAnterior, nuevaIp);
        boolean requiereReinicio = cambioIp && conexionMovilService.isSslEnabled();
        boolean certificadoPreparado = requiereReinicio && sslConfigService.prepararCertificadoParaIp(nuevaIp);
        result.put("ipAnterior", ipAnterior);
        result.put("requiereReinicioHttps", requiereReinicio);
        result.put("certificadoPreparado", certificadoPreparado);
        if (requiereReinicio) {
            result.put("restartHint", "La IP cambió. Reinicie Docker para que HTTPS cargue el certificado nuevo.");
        }
    }
}
