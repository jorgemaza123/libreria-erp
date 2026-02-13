package com.libreria.sistema.config;

import com.libreria.sistema.service.ConexionMovilService;
import com.libreria.sistema.util.NetworkUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Servicio de configuración SSL que genera automáticamente un certificado
 * autofirmado si no existe, permitiendo conexiones HTTPS desde dispositivos móviles.
 *
 * La IP se lee desde BD (ConexionMovilService) — no se recalcula dinámicamente.
 */
@Service
@Slf4j
public class SslConfigService {

    @Value("${server.ssl.key-store:keystore.p12}")
    private String keystorePath;

    @Value("${server.ssl.key-store-password:sistemaerp}")
    private String keystorePassword;

    @Value("${server.ssl.key-alias:sistemaerp}")
    private String keyAlias;

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    @Autowired(required = false)
    private ConexionMovilService conexionMovilService;

    @PostConstruct
    public void init() {
        try {
            // Solo generar keystore si no existe
            File keystoreFile = new File(keystorePath);
            if (!keystoreFile.exists()) {
                log.info("Keystore no encontrado. Generando certificado SSL automáticamente...");
                generateKeystoreWithKeytool(keystoreFile);
            } else {
                log.info("Keystore existente encontrado: {}", keystoreFile.getAbsolutePath());
            }

            // Log informativo — IP se lee de BD (ya inicializada por ConexionMovilService)
            String currentIp = conexionMovilService != null
                    ? conexionMovilService.obtenerIpConfigurada()
                    : NetworkUtils.detectarIpLan();
            log.info("========================================");
            log.info("SERVIDOR HTTPS ACTIVO");
            log.info("IP configurada: {}", currentIp);
            log.info("URL de acceso: https://{}:{}", currentIp, serverPort);
            log.info("========================================");

        } catch (Exception e) {
            log.error("Error en configuración SSL: {}", e.getMessage(), e);
        }
    }

    /**
     * Genera el keystore usando keytool del JDK.
     * SAN incluye dns:localhost, ip:127.0.0.1 y TODAS las IPs privadas detectadas
     * al momento de generar. Esto minimiza errores NET::ERR_CERT_COMMON_NAME_INVALID
     * en navegadores móviles (especialmente Android 7+/Chrome).
     */
    private void generateKeystoreWithKeytool(File keystoreFile) {
        try {
            String javaHome = System.getProperty("java.home");
            String keytoolPath = javaHome + File.separator + "bin" + File.separator + "keytool";

            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                keytoolPath += ".exe";
            }

            File keytoolFile = new File(keytoolPath);
            if (!keytoolFile.exists()) {
                log.warn("keytool no encontrado en: {}. Intentando con PATH del sistema...", keytoolPath);
                keytoolPath = "keytool";
            }

            File parentDir = keystoreFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Construir SAN con TODAS las IPs privadas detectadas + localhost + 127.0.0.1
            String sanValue = buildSanWithAllIps();

            List<String> command = new ArrayList<>(List.of(
                    keytoolPath,
                    "-genkeypair",
                    "-alias", keyAlias,
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-storetype", "PKCS12",
                    "-keystore", keystoreFile.getAbsolutePath(),
                    "-storepass", keystorePassword,
                    "-keypass", keystorePassword,
                    "-validity", "3650",
                    "-dname", "CN=SistemaERP, OU=Desarrollo, O=Libreria, L=Lima, ST=Lima, C=PE",
                    "-ext", sanValue
            ));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.error("Timeout al ejecutar keytool");
                return;
            }

            int exitCode = process.exitValue();

            if (exitCode == 0 && keystoreFile.exists()) {
                log.info("Certificado SSL generado exitosamente:");
                log.info("  - Archivo: {}", keystoreFile.getAbsolutePath());
                log.info("  - Alias: {}", keyAlias);
                log.info("  - Validez: 10 años");
                log.info("  - Algoritmo: RSA 2048 bits");
                log.info("  - SAN: {}", sanValue);
            } else {
                log.error("Error ejecutando keytool (código {}): {}", exitCode, output.toString());
            }

        } catch (Exception e) {
            log.error("Error al ejecutar keytool: {}", e.getMessage(), e);
        }
    }

    /**
     * Construye el valor SAN incluyendo:
     * - dns:localhost (siempre)
     * - ip:127.0.0.1 (siempre)
     * - ip:X.X.X.X para cada IP privada detectada en el sistema
     *
     * Ejemplo resultado: "SAN=dns:localhost,ip:127.0.0.1,ip:192.168.1.100,ip:192.168.18.25"
     */
    private String buildSanWithAllIps() {
        List<String> sanEntries = new ArrayList<>();
        sanEntries.add("dns:localhost");
        sanEntries.add("ip:127.0.0.1");

        // Agregar la IP detectada/configurada
        String detectedIp = NetworkUtils.detectarIpLan();
        if (detectedIp != null && !detectedIp.equals("localhost")) {
            String entry = "ip:" + detectedIp;
            if (!sanEntries.contains(entry)) {
                sanEntries.add(entry);
            }
        }

        String san = "SAN=" + sanEntries.stream().collect(Collectors.joining(","));
        log.info("SAN generado para certificado: {}", san);
        return san;
    }

    /**
     * Obtiene la URL del servidor usando la IP guardada en BD.
     */
    public String getServerUrl() {
        String ip = getLocalIp();
        String protocol = sslEnabled ? "https" : "http";
        return String.format("%s://%s:%d", protocol, ip, serverPort);
    }

    /**
     * Obtiene la IP configurada desde BD.
     */
    public String getLocalIp() {
        if (conexionMovilService != null) {
            return conexionMovilService.obtenerIpConfigurada();
        }
        return NetworkUtils.detectarIpLan();
    }

    /**
     * Obtiene el puerto del servidor.
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * Verifica si SSL está habilitado.
     */
    public boolean isSslEnabled() {
        return sslEnabled && new File(keystorePath).exists();
    }
}
