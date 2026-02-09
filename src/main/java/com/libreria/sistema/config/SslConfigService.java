package com.libreria.sistema.config;

import com.libreria.sistema.util.NetworkUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de configuración SSL que genera automáticamente un certificado
 * autofirmado si no existe, permitiendo conexiones HTTPS desde dispositivos móviles.
 *
 * Usa keytool (incluido en JDK) para generar el certificado, evitando dependencias
 * de paquetes internos de Java que no son accesibles en Java 9+.
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

    private String serverUrl;
    private String localIp;

    @PostConstruct
    public void init() {
        try {
            // Detectar IP local
            this.localIp = NetworkUtils.getLocalIpAddress();
            this.serverUrl = NetworkUtils.getServerUrl(true, serverPort);

            log.info("========================================");
            log.info("CONFIGURACIÓN DE RED DEL SERVIDOR");
            log.info("========================================");
            log.info("IP Local detectada: {}", localIp);
            log.info("URL del servidor: {}", serverUrl);
            log.info("Puerto HTTPS: {}", serverPort);

            // Verificar y generar keystore si no existe
            File keystoreFile = new File(keystorePath);
            if (!keystoreFile.exists()) {
                log.info("Keystore no encontrado. Generando certificado SSL automáticamente...");
                generateKeystoreWithKeytool(keystoreFile);
            } else {
                log.info("Keystore existente encontrado: {}", keystoreFile.getAbsolutePath());
            }

            log.info("========================================");
            log.info("ACCESO MÓVIL HABILITADO");
            log.info("Conecte dispositivos a: {}", serverUrl);
            log.info("========================================");

        } catch (Exception e) {
            log.error("Error en configuración SSL: {}", e.getMessage(), e);
        }
    }

    /**
     * Genera el keystore usando el comando keytool del JDK.
     * Este método es compatible con todas las versiones de Java (8+).
     */
    private void generateKeystoreWithKeytool(File keystoreFile) {
        try {
            log.info("Generando keystore con keytool...");

            // Detectar ruta de Java
            String javaHome = System.getProperty("java.home");
            String keytoolPath = javaHome + File.separator + "bin" + File.separator + "keytool";

            // En Windows agregar .exe
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                keytoolPath += ".exe";
            }

            // Verificar que keytool existe
            File keytoolFile = new File(keytoolPath);
            if (!keytoolFile.exists()) {
                log.warn("keytool no encontrado en: {}. Intentando con PATH del sistema...", keytoolPath);
                keytoolPath = "keytool"; // Intentar con PATH del sistema
            }

            // Crear directorio padre si no existe
            File parentDir = keystoreFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Construir comando keytool
            ProcessBuilder pb = new ProcessBuilder(
                    keytoolPath,
                    "-genkeypair",
                    "-alias", keyAlias,
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-storetype", "PKCS12",
                    "-keystore", keystoreFile.getAbsolutePath(),
                    "-storepass", keystorePassword,
                    "-keypass", keystorePassword,
                    "-validity", "3650", // 10 años
                    "-dname", "CN=SistemaERP, OU=Desarrollo, O=Libreria, L=Lima, ST=Lima, C=PE",
                    "-ext", "SAN=ip:" + localIp + ",dns:localhost" // Subject Alternative Names
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Leer salida del proceso
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Esperar a que termine el proceso (máximo 30 segundos)
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
                log.info("  - SAN: ip:{}, dns:localhost", localIp);
            } else {
                log.error("Error ejecutando keytool (código {}): {}", exitCode, output.toString());
                log.info("Para generar el certificado manualmente, ejecute:");
                log.info("keytool -genkeypair -alias {} -keyalg RSA -keysize 2048 -storetype PKCS12 " +
                         "-keystore {} -storepass {} -validity 3650 -dname \"CN=SistemaERP\"",
                         keyAlias, keystorePath, keystorePassword);
            }

        } catch (Exception e) {
            log.error("Error al ejecutar keytool: {}", e.getMessage(), e);
            log.info("Para generar el certificado manualmente, ejecute:");
            log.info("keytool -genkeypair -alias {} -keyalg RSA -keysize 2048 -storetype PKCS12 " +
                     "-keystore {} -storepass {} -validity 3650 -dname \"CN=SistemaERP\"",
                     keyAlias, keystorePath, keystorePassword);
        }
    }

    /**
     * Obtiene la URL completa del servidor para conexiones móviles.
     */
    public String getServerUrl() {
        if (serverUrl == null) {
            serverUrl = NetworkUtils.getServerUrl(true, serverPort);
        }
        return serverUrl;
    }

    /**
     * Obtiene la IP local detectada.
     */
    public String getLocalIp() {
        if (localIp == null) {
            localIp = NetworkUtils.getLocalIpAddress();
        }
        return localIp;
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
        return new File(keystorePath).exists();
    }
}
