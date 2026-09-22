package com.libreria.sistema.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.libreria.sistema.util.NetworkUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Servicio de conexión móvil / red.
 *
 * Arquitectura "detectar una vez, guardar en BD":
 *   - En el primer arranque detecta la IP LAN del host y la persiste en system_configurations.
 *   - En arranques posteriores usa la IP guardada (sin recalcular).
 *   - El admin puede recalcular manualmente o escribir una IP fija.
 */
@Service
@Slf4j
public class ConexionMovilService {

    public static final String KEY_HOST_IP = "HOST_IP";

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    @Value("${conexion-movil.http-diagnostico.enabled:true}")
    private boolean httpDiagnosticoEnabled;

    @Value("${conexion-movil.http-diagnostico.port:8081}")
    private int httpDiagnosticoPort;

    private final SystemConfigurationService systemConfigService;

    public ConexionMovilService(SystemConfigurationService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * Al iniciar la aplicación: si no hay IP guardada, detectar y persistir.
     */
    @PostConstruct
    public void inicializarIp() {
        Optional<String> ipGuardada = systemConfigService.getValue(KEY_HOST_IP);
        Optional<String> hostIpEnv = NetworkUtils.obtenerHostIpOverride();

        if (ipGuardada.isPresent() && !ipGuardada.get().isBlank()) {
            String ipActual = ipGuardada.get().trim();
            if (!NetworkUtils.esIpAccesibleDesdeMovil(ipActual) && hostIpEnv.isPresent()) {
                guardarIp(hostIpEnv.get(), true);
                log.warn("IP guardada no apta para celular ({}). Reemplazada por HOST_IP={}", ipActual, hostIpEnv.get());
            } else if (!NetworkUtils.esIpAccesibleDesdeMovil(ipActual)) {
                log.warn("IP de red cargada desde BD no apta para celular: {}. Configure HOST_IP o guarde la IP LAN manualmente.", ipActual);
            } else {
                log.info("IP de red cargada desde BD: {}", ipActual);
            }
        } else {
            // Primer arranque — detectar IP y guardarla
            String detected = NetworkUtils.detectarIpLan();
            guardarIp(detected, true);
            log.info("Primer arranque — IP detectada y guardada: {}", detected);
        }
    }

    /**
     * Retorna la IP configurada (desde BD). Nunca recalcula.
     */
    public String obtenerIpConfigurada() {
        String ipGuardada = obtenerIpGuardada();
        if (!NetworkUtils.esIpAccesibleDesdeMovil(ipGuardada)) {
            return NetworkUtils.obtenerHostIpOverride().orElse(ipGuardada);
        }
        return ipGuardada;
    }

    public String obtenerIpGuardada() {
        return systemConfigService.getValue(KEY_HOST_IP)
                .map(String::trim)
                .filter(ip -> !ip.isBlank())
                .orElse("localhost");
    }

    /**
     * Ejecuta detección de IP y la guarda en BD.
     * Se invoca manualmente desde el panel de admin.
     * Retorna la nueva IP detectada.
     */
    @Transactional
    public String recalcularIp() {
        String detected = NetworkUtils.detectarIpLan();
        validarIpParaMovil(detected);
        guardarIp(detected, true);
        log.info("IP recalculada por admin: {}", detected);
        return detected;
    }

    /**
     * El admin escribe una IP manualmente y se guarda.
     */
    @Transactional
    public void guardarIpManual(String ip) {
        String ipNormalizada = ip != null ? ip.trim() : "";
        validarIpParaMovil(ipNormalizada);
        guardarIp(ipNormalizada, false);
        log.info("IP configurada manualmente por admin: {}", ipNormalizada);
    }

    /**
     * Construye la URL completa del servidor usando la IP guardada.
     */
    public String generarServerUrl() {
        String ip = obtenerIpConfigurada();
        String protocol = sslEnabled ? "https" : "http";
        return String.format("%s://%s:%d", protocol, ip, serverPort);
    }

    public String generarAccesoMovilUrl() {
        return generarServerUrl() + "/conexion-movil/acceso";
    }

    public String generarPingUrl() {
        return generarServerUrl() + "/conexion-movil/ping";
    }

    public String generarPingTextoUrl() {
        return generarServerUrl() + "/conexion-movil/ping.txt";
    }

    public String generarHttpDiagnosticoPingUrl() {
        return generarHttpDiagnosticoBaseUrl() + "/conexion-movil/http-ping";
    }

    public String generarHttpDiagnosticoAccesoUrl() {
        return generarHttpDiagnosticoBaseUrl() + "/conexion-movil/http-acceso";
    }

    public String generarHttpDiagnosticoBaseUrl() {
        String ip = obtenerIpConfigurada();
        return String.format("http://%s:%d", ip, httpDiagnosticoPort);
    }

    /**
     * Genera un código QR como imagen PNG en bytes.
     */
    public byte[] generarQrCode(String contenido, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(contenido, BarcodeFormat.QR_CODE, size, size, hints);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);

            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            log.error("Error al generar QR: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Genera QR como string Base64 listo para embeber en HTML (data URI).
     */
    public String generarQrCodeBase64(String contenido, int size) {
        byte[] qrBytes = generarQrCode(contenido, size);
        if (qrBytes.length == 0) return "";
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(qrBytes);
    }

    public int getServerPort() {
        return serverPort;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public Map<String, Object> obtenerDiagnosticoConexion() {
        String ipGuardada = obtenerIpGuardada();
        String ipEfectiva = obtenerIpConfigurada();
        Optional<String> hostIpEnv = NetworkUtils.obtenerHostIpOverride();
        boolean ipGuardadaValida = NetworkUtils.esIpAccesibleDesdeMovil(ipGuardada);
        boolean ipEfectivaValida = NetworkUtils.esIpAccesibleDesdeMovil(ipEfectiva);

        Map<String, Object> diagnostico = new LinkedHashMap<>();
        diagnostico.put("docker", NetworkUtils.isRunningInDocker());
        diagnostico.put("ipGuardada", ipGuardada);
        diagnostico.put("ipEfectiva", ipEfectiva);
        diagnostico.put("ipGuardadaValida", ipGuardadaValida);
        diagnostico.put("ipEfectivaValida", ipEfectivaValida);
        diagnostico.put("ipGuardadaInternaDocker", NetworkUtils.esIpInternaDocker(ipGuardada));
        diagnostico.put("hostIpEnv", hostIpEnv.orElse(""));
        diagnostico.put("requiereCorreccion", !ipEfectivaValida);
        diagnostico.put("serverPort", serverPort);
        diagnostico.put("serverUrl", generarServerUrl());
        diagnostico.put("accesoMovilUrl", generarAccesoMovilUrl());
        diagnostico.put("pingUrl", generarPingUrl());
        diagnostico.put("pingTextoUrl", generarPingTextoUrl());
        diagnostico.put("httpDiagnosticoEnabled", httpDiagnosticoEnabled);
        diagnostico.put("httpDiagnosticoPort", httpDiagnosticoPort);
        diagnostico.put("httpDiagnosticoPingUrl", generarHttpDiagnosticoPingUrl());
        diagnostico.put("httpDiagnosticoAccesoUrl", generarHttpDiagnosticoAccesoUrl());
        diagnostico.put("comandoPruebaPuerto", construirComandoPruebaPuerto(ipEfectiva));
        diagnostico.put("comandoPruebaPuertoHttp", construirComandoPruebaPuertoHttp(ipEfectiva));
        diagnostico.put("comandoVerFirewallWindows", construirComandoVerFirewallWindows());
        diagnostico.put("comandoFirewallWindows", construirComandoFirewallWindows());
        diagnostico.put("comandoDockerPuertos", construirComandoDockerPuertos());
        diagnostico.put("comandoDockerRecrear", construirComandoDockerRecrear());
        diagnostico.put("mensajePuerto", construirMensajePuerto(ipEfectivaValida));
        diagnostico.put("mensaje", construirMensajeDiagnostico(ipGuardada, ipEfectiva, hostIpEnv, ipEfectivaValida));
        return diagnostico;
    }

    public boolean esIpAptaParaMovil(String ip) {
        return NetworkUtils.esIpAccesibleDesdeMovil(ip);
    }

    private void validarIpParaMovil(String ip) {
        if (!NetworkUtils.esIpv4Valida(ip)) {
            throw new IllegalArgumentException("Formato de IP inválido. Use un IPv4 real, por ejemplo 192.168.18.11.");
        }
        if (!NetworkUtils.isPrivateIp(ip)) {
            throw new IllegalArgumentException("Use la IP local de Windows en su WiFi o red LAN, no una IP pública.");
        }
        if (NetworkUtils.esIpInternaDocker(ip)) {
            throw new IllegalArgumentException("Esa IP pertenece a Docker/virtualización y el celular no podrá entrar. Use la IP de Windows que sale en ipconfig.");
        }
        if (!NetworkUtils.esIpAccesibleDesdeMovil(ip)) {
            throw new IllegalArgumentException("Esa IP no parece accesible desde un celular en la misma red. Use una IP tipo 192.168.x.x o 10.x.x.x de Windows.");
        }
    }

    private String construirMensajeDiagnostico(String ipGuardada,
                                               String ipEfectiva,
                                               Optional<String> hostIpEnv,
                                               boolean ipEfectivaValida) {
        if (ipEfectivaValida && hostIpEnv.isPresent() && !Objects.equals(ipGuardada, hostIpEnv.get())) {
            return "El QR está usando HOST_IP=" + hostIpEnv.get() + " porque la IP guardada no era apta para celular.";
        }
        if (ipEfectivaValida) {
            return "La URL móvil apunta a una IP LAN válida.";
        }
        if (NetworkUtils.esIpInternaDocker(ipGuardada)) {
            return "La IP guardada pertenece a Docker Desktop. En Windows debe configurar la IPv4 real de la computadora.";
        }
        return "No se encontró una IP LAN válida. Configure HOST_IP en .env o guarde manualmente la IPv4 de Windows.";
    }

    private String construirMensajePuerto(boolean ipEfectivaValida) {
        if (!ipEfectivaValida) {
            return "Primero corrija la IP. Luego pruebe el puerto 8443 desde Windows y desde el celular.";
        }
        return "La IP es correcta. Si el celular se queda cargando, revise firewall, red invitada o aislamiento entre WiFi y Ethernet.";
    }

    private String construirComandoPruebaPuerto(String ip) {
        String ipSegura = NetworkUtils.esIpv4Valida(ip) ? ip : "192.168.18.10";
        return "Test-NetConnection " + ipSegura + " -Port " + serverPort;
    }

    private String construirComandoPruebaPuertoHttp(String ip) {
        String ipSegura = NetworkUtils.esIpv4Valida(ip) ? ip : "192.168.18.10";
        return "Test-NetConnection " + ipSegura + " -Port " + httpDiagnosticoPort;
    }

    private String construirComandoVerFirewallWindows() {
        return "netsh advfirewall firewall show rule name=\"Sistema Libreria Movil\"";
    }

    private String construirComandoFirewallWindows() {
        String puertos = httpDiagnosticoEnabled
                ? serverPort + "," + httpDiagnosticoPort
                : String.valueOf(serverPort);
        return "netsh advfirewall firewall add rule name=\"Sistema Libreria Movil\" dir=in action=allow protocol=TCP localport="
                + puertos + " profile=any";
    }

    private String construirComandoDockerPuertos() {
        return "docker ps --format \"table {{.Names}}\\t{{.Ports}}\"";
    }

    private String construirComandoDockerRecrear() {
        return "docker compose up -d --force-recreate app";
    }

    private void guardarIp(String ip, boolean autoDetectada) {
        String desc = autoDetectada
                ? "IP LAN del host (auto-detectada)"
                : "IP LAN del host (configurada manualmente)";
        systemConfigService.setValue(KEY_HOST_IP, ip, desc);
    }
}
