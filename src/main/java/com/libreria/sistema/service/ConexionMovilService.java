package com.libreria.sistema.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.libreria.sistema.util.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ConexionMovilService {

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    /**
     * Detecta la IP principal del servidor dinámicamente.
     */
    public String detectarIpPrincipal() {
        return NetworkUtils.getLocalIpAddress();
    }

    /**
     * Retorna todas las IPs candidatas para fallback.
     */
    public List<String> detectarTodasLasIps() {
        return NetworkUtils.getAllCandidateIps();
    }

    /**
     * Construye la URL completa del servidor.
     */
    public String generarServerUrl() {
        String ip = detectarIpPrincipal();
        String protocol = sslEnabled ? "https" : "http";
        return String.format("%s://%s:%d", protocol, ip, serverPort);
    }

    /**
     * Genera un código QR como imagen PNG en bytes.
     * Usa ZXing localmente — no requiere internet.
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

    /**
     * Verifica si el puerto del servidor está accesible.
     * Prueba desde localhost Y desde la IP LAN detectada.
     * En Windows, verifica reglas de firewall.
     */
    public Map<String, Object> verificarPuerto() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("port", serverPort);

        // Test 1: localhost
        boolean localOpen = testSocket("127.0.0.1", serverPort);
        result.put("localOpen", localOpen);

        // Test 2: desde IP LAN (simula acceso externo)
        String lanIp = detectarIpPrincipal();
        boolean lanOpen = false;
        if (!"localhost".equals(lanIp)) {
            lanOpen = testSocket(lanIp, serverPort);
        }
        result.put("lanOpen", lanOpen);

        // Test 3: verificar firewall Windows
        Map<String, Object> firewallInfo = verificarFirewall();
        result.put("firewall", firewallInfo);

        // Mensaje resumen
        if (localOpen && lanOpen) {
            result.put("status", "ok");
            result.put("message", "Puerto " + serverPort + " accesible desde LAN");
        } else if (localOpen && !lanOpen) {
            result.put("status", "firewall_blocked");
            result.put("message", "Puerto " + serverPort + " bloqueado por firewall. Los celulares no podrán conectarse.");
        } else {
            result.put("status", "error");
            result.put("message", "Puerto " + serverPort + " no accesible");
        }

        return result;
    }

    private boolean testSocket(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica si el firewall de Windows tiene regla que permita el puerto.
     * Ejecuta "netsh advfirewall firewall show rule" para buscar reglas.
     * En Linux, no aplica (retorna info básica).
     */
    private Map<String, Object> verificarFirewall() {
        Map<String, Object> info = new LinkedHashMap<>();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        info.put("os", isWindows ? "Windows" : "Linux");

        if (!isWindows) {
            info.put("checked", false);
            info.put("message", "Verificación de firewall solo disponible en Windows");
            return info;
        }

        try {
            // Buscar reglas de firewall que permitan el puerto
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd", "/c", "netsh", "advfirewall", "firewall", "show", "rule",
                    "name=all", "dir=in"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines()
                        .collect(java.util.stream.Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                info.put("checked", false);
                info.put("message", "Timeout verificando firewall");
                return info;
            }

            info.put("checked", true);

            // Buscar si hay alguna regla que mencione el puerto o "Java" o "SistemaERP"
            String portStr = String.valueOf(serverPort);
            boolean portRuleFound = false;
            boolean javaRuleFound = false;

            String[] lines = output.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].toLowerCase();
                // Buscar reglas habilitadas que permitan el puerto
                if (line.contains(portStr) && !line.contains("block")) {
                    portRuleFound = true;
                }
                if ((line.contains("java") || line.contains("sistemaerp") || line.contains("openjdk"))
                        && !line.contains("block")) {
                    javaRuleFound = true;
                }
            }

            if (portRuleFound || javaRuleFound) {
                info.put("allowed", true);
                info.put("message", "Firewall permite conexiones al puerto " + serverPort);
            } else {
                info.put("allowed", false);
                info.put("message", "No se encontró regla de firewall que permita el puerto " + serverPort
                        + ". Los celulares pueden no conectarse.");
            }

        } catch (Exception e) {
            log.debug("Error verificando firewall: {}", e.getMessage());
            info.put("checked", false);
            info.put("message", "No se pudo verificar firewall: " + e.getMessage());
        }

        return info;
    }

    /**
     * Retorna información completa de conexión para la vista y API.
     */
    public Map<String, Object> obtenerInfoConexion() {
        Map<String, Object> info = new LinkedHashMap<>();

        String ip = detectarIpPrincipal();
        String url = generarServerUrl();
        List<String> allIps = detectarTodasLasIps();

        info.put("ip", ip);
        info.put("port", serverPort);
        info.put("protocol", sslEnabled ? "https" : "http");
        info.put("url", url);
        info.put("sslEnabled", sslEnabled);
        info.put("allIps", allIps);

        // Generar URLs alternativas para fallback
        List<String> allUrls = new ArrayList<>();
        String protocol = sslEnabled ? "https" : "http";
        for (String candidateIp : allIps) {
            allUrls.add(String.format("%s://%s:%d", protocol, candidateIp, serverPort));
        }
        info.put("allUrls", allUrls);

        return info;
    }

    public int getServerPort() {
        return serverPort;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }
}
