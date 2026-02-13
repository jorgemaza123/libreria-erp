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

        if (ipGuardada.isPresent() && !ipGuardada.get().isBlank()) {
            log.info("IP de red cargada desde BD: {}", ipGuardada.get());
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
        return systemConfigService.getValue(KEY_HOST_IP).orElse("localhost");
    }

    /**
     * Ejecuta detección de IP y la guarda en BD.
     * Se invoca manualmente desde el panel de admin.
     * Retorna la nueva IP detectada.
     */
    @Transactional
    public String recalcularIp() {
        String detected = NetworkUtils.detectarIpLan();
        guardarIp(detected, true);
        log.info("IP recalculada por admin: {}", detected);
        return detected;
    }

    /**
     * El admin escribe una IP manualmente y se guarda.
     */
    @Transactional
    public void guardarIpManual(String ip) {
        guardarIp(ip.trim(), false);
        log.info("IP configurada manualmente por admin: {}", ip.trim());
    }

    /**
     * Construye la URL completa del servidor usando la IP guardada.
     */
    public String generarServerUrl() {
        String ip = obtenerIpConfigurada();
        String protocol = sslEnabled ? "https" : "http";
        return String.format("%s://%s:%d", protocol, ip, serverPort);
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

    private void guardarIp(String ip, boolean autoDetectada) {
        String desc = autoDetectada
                ? "IP LAN del host (auto-detectada)"
                : "IP LAN del host (configurada manualmente)";
        systemConfigService.setValue(KEY_HOST_IP, ip, desc);
    }
}
