package com.libreria.sistema;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.io.File;

@SpringBootApplication
@EnableScheduling
public class SistemaApplication {

    public static void main(String[] args) {
        verificarYGenerarKeystore();

        // 2. Iniciar la aplicación
        ConfigurableApplicationContext ctx = SpringApplication.run(SistemaApplication.class, args);
        String port = ctx.getEnvironment().getProperty("server.port", "8443");
        String protocol = Boolean.parseBoolean(ctx.getEnvironment().getProperty("server.ssl.enabled", "true")) ? "https" : "http";
        System.out.println("\n===========================================================");
        System.out.println(" SISTEMA LIBRERÍA INICIADO CON ÉXITO");
        System.out.println(" ACCESO: " + protocol + "://localhost:" + port);
        System.out.println("===========================================================\n");
    }

    /**
     * Genera el archivo keystore.p12 automáticamente si no existe.
     * Esto evita el error "FileNotFoundException" al arrancar en una PC nueva.
     */
    private static void verificarYGenerarKeystore() {
        File keystore = new File("keystore.p12");
        
        if (!keystore.exists()) {
            System.out.println(">>>  ALERTA: No se encontró 'keystore.p12'. Generando certificado SSL...");
            try {
                // Localizar la herramienta keytool del Java actual
                String javaHome = System.getProperty("java.home");
                String keytoolPath = javaHome + File.separator + "bin" + File.separator + "keytool";
                
                // Si estamos en Windows, asegurar la extensión .exe (aunque ProcessBuilder suele manejarlo)
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    keytoolPath += ".exe";
                }

                // Configuración del comando (Coincide con tu application.properties)
                ProcessBuilder pb = new ProcessBuilder(
                    keytoolPath,
                    "-genkeypair",
                    "-alias", "sistemaerp",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-storetype", "PKCS12",
                    "-keystore", "keystore.p12",
                    "-validity", "3650",     // 10 años
                    "-storepass", "sistemaerp", 
                    "-dname", "CN=Sistema Libreria, OU=IT, O=Chroma, L=Lima, S=Lima, C=PE"
                );
                
                // Redirigir salida para ver errores si los hay
                pb.inheritIO();
                
                // Ejecutar
                Process p = pb.start();
                int exitCode = p.waitFor();
                
                if (exitCode == 0) {
                    System.out.println(">>>  CERTIFICADO CREADO EXITOSAMENTE.");
                } else {
                    System.err.println(">>> ❌ ERROR: Keytool falló con código " + exitCode);
                }
                
            } catch (Exception e) {
                System.err.println(">>> ❌ ERROR CRÍTICO generando certificado: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println(">>>  Certificado SSL encontrado (keystore.p12). Iniciando sistema...");
        }
    }
}