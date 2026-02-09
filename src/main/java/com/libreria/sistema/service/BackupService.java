package com.libreria.sistema.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de Backup de Base de Datos PostgreSQL.
 *
 * Características:
 * - Detecta automáticamente Windows/Linux
 * - Usa pg_dump para generar backups .sql
 * - Toma credenciales dinámicamente de application.properties
 * - Maneja errores amigables si pg_dump no está disponible
 */
@Service
@Slf4j
public class BackupService {

    // Credenciales de BD obtenidas dinámicamente
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    // Timeout para el proceso de backup (5 minutos)
    private static final int BACKUP_TIMEOUT_MINUTES = 5;

    /**
     * Genera un backup completo de la base de datos.
     *
     * @return Map con "resource" (archivo), "filename" (nombre) y "success" (boolean)
     * @throws BackupException si hay errores en el proceso
     */
    public Map<String, Object> generarBackup() throws BackupException {
        log.info("Iniciando proceso de backup de base de datos...");

        // 1. Parsear URL de conexión para obtener host, puerto y nombre de BD
        DatabaseConnectionInfo connInfo = parseDatabaseUrl(datasourceUrl);
        log.info("Base de datos: {} en {}:{}", connInfo.database, connInfo.host, connInfo.port);

        // 2. Detectar sistema operativo
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        log.info("Sistema operativo detectado: {}", isWindows ? "Windows" : "Linux/Unix");

        // 3. Verificar que pg_dump está disponible
        String pgDumpPath = findPgDump(isWindows);
        if (pgDumpPath == null) {
            throw new BackupException(
                "Error: No se encontró la herramienta de respaldo (pg_dump). " +
                "Asegúrese de tener PostgreSQL instalado y pg_dump en el PATH del sistema. " +
                "Contacte soporte técnico si el problema persiste."
            );
        }

        // 4. Crear archivo temporal para el backup
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "backup_" + connInfo.database + "_" + timestamp + ".sql";

        Path tempFile;
        try {
            tempFile = Files.createTempFile("backup_", ".sql");
        } catch (IOException e) {
            throw new BackupException("Error al crear archivo temporal: " + e.getMessage());
        }

        // 5. Construir y ejecutar comando pg_dump
        try {
            List<String> command = buildPgDumpCommand(pgDumpPath, connInfo, tempFile);
            log.info("Ejecutando pg_dump...");

            ProcessBuilder pb = new ProcessBuilder(command);

            // Configurar variable de entorno PGPASSWORD para evitar prompt de contraseña
            Map<String, String> env = pb.environment();
            env.put("PGPASSWORD", dbPassword);

            // Redirigir errores para capturarlos
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Capturar salida de error
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            // Esperar a que termine con timeout
            boolean finished = process.waitFor(BACKUP_TIMEOUT_MINUTES, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                throw new BackupException("El proceso de backup excedió el tiempo límite de " +
                        BACKUP_TIMEOUT_MINUTES + " minutos.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorMsg = errorOutput.toString().trim();
                log.error("pg_dump falló con código {}: {}", exitCode, errorMsg);
                throw new BackupException("Error al generar backup: " +
                        (errorMsg.isEmpty() ? "Código de error " + exitCode : errorMsg));
            }

            // 6. Leer el archivo generado
            byte[] backupData = Files.readAllBytes(tempFile);

            // 7. Verificar que el archivo no esté vacío
            if (backupData.length == 0) {
                throw new BackupException("El archivo de backup está vacío. Verifique los permisos de la base de datos.");
            }

            log.info("Backup generado exitosamente: {} ({} bytes)", filename, backupData.length);

            // 8. Crear Resource para descarga
            ByteArrayResource resource = new ByteArrayResource(backupData);

            return Map.of(
                "success", true,
                "resource", resource,
                "filename", filename,
                "size", backupData.length
            );

        } catch (IOException e) {
            throw new BackupException("Error de E/S durante el backup: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackupException("El proceso de backup fue interrumpido.");
        } finally {
            // Limpiar archivo temporal
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("No se pudo eliminar archivo temporal: {}", tempFile);
            }
        }
    }

    /**
     * Parsea la URL de conexión JDBC para extraer host, puerto y nombre de BD.
     * Formato esperado: jdbc:postgresql://host:port/database
     */
    private DatabaseConnectionInfo parseDatabaseUrl(String url) throws BackupException {
        try {
            // Remover prefijo jdbc:
            String cleanUrl = url.replace("jdbc:", "");
            URI uri = new URI(cleanUrl);

            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432; // Puerto por defecto de PostgreSQL
            String database = uri.getPath().substring(1); // Remover "/" inicial

            // Manejar parámetros de query si existen
            if (database.contains("?")) {
                database = database.substring(0, database.indexOf("?"));
            }

            return new DatabaseConnectionInfo(host, port, database);

        } catch (Exception e) {
            throw new BackupException("Error al parsear URL de base de datos: " + url);
        }
    }

    /**
     * Busca el ejecutable pg_dump en el sistema.
     *
     * @param isWindows true si es Windows
     * @return ruta al ejecutable o null si no se encuentra
     */
    private String findPgDump(boolean isWindows) {
        // 1. Primero intentar encontrar en PATH
        String pgDumpName = isWindows ? "pg_dump.exe" : "pg_dump";

        try {
            ProcessBuilder pb = new ProcessBuilder(
                isWindows ? "where" : "which",
                pgDumpName
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String path = reader.readLine();
                if (path != null && !path.isEmpty() && !path.contains("not found")) {
                    log.info("pg_dump encontrado en PATH: {}", path);
                    return pgDumpName; // Usar nombre simple si está en PATH
                }
            }

            process.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("No se pudo verificar pg_dump en PATH: {}", e.getMessage());
        }

        // 2. Buscar en ubicaciones comunes (Windows)
        if (isWindows) {
            String[] windowsPaths = {
                "C:\\Program Files\\PostgreSQL\\16\\bin\\pg_dump.exe",
                "C:\\Program Files\\PostgreSQL\\15\\bin\\pg_dump.exe",
                "C:\\Program Files\\PostgreSQL\\14\\bin\\pg_dump.exe",
                "C:\\Program Files\\PostgreSQL\\13\\bin\\pg_dump.exe",
                "C:\\Program Files\\PostgreSQL\\12\\bin\\pg_dump.exe",
                "C:\\Program Files (x86)\\PostgreSQL\\16\\bin\\pg_dump.exe",
                "C:\\Program Files (x86)\\PostgreSQL\\15\\bin\\pg_dump.exe"
            };

            for (String path : windowsPaths) {
                if (new File(path).exists()) {
                    log.info("pg_dump encontrado en: {}", path);
                    return path;
                }
            }
        } else {
            // 3. Buscar en ubicaciones comunes (Linux)
            String[] linuxPaths = {
                "/usr/bin/pg_dump",
                "/usr/local/bin/pg_dump",
                "/usr/pgsql-16/bin/pg_dump",
                "/usr/pgsql-15/bin/pg_dump",
                "/usr/pgsql-14/bin/pg_dump"
            };

            for (String path : linuxPaths) {
                if (new File(path).exists()) {
                    log.info("pg_dump encontrado en: {}", path);
                    return path;
                }
            }
        }

        log.error("No se encontró pg_dump en el sistema");
        return null;
    }

    /**
     * Construye el comando pg_dump con los parámetros necesarios.
     */
    private List<String> buildPgDumpCommand(String pgDumpPath, DatabaseConnectionInfo connInfo, Path outputFile) {
        List<String> command = new ArrayList<>();
        command.add(pgDumpPath);
        command.add("-h");
        command.add(connInfo.host);
        command.add("-p");
        command.add(String.valueOf(connInfo.port));
        command.add("-U");
        command.add(dbUsername);
        command.add("-d");
        command.add(connInfo.database);
        command.add("-f");
        command.add(outputFile.toAbsolutePath().toString());
        command.add("--no-password"); // Usamos PGPASSWORD env var
        command.add("--verbose");     // Para logs detallados

        return command;
    }

    /**
     * Clase interna para información de conexión.
     */
    private static class DatabaseConnectionInfo {
        final String host;
        final int port;
        final String database;

        DatabaseConnectionInfo(String host, int port, String database) {
            this.host = host;
            this.port = port;
            this.database = database;
        }
    }

    /**
     * Excepción personalizada para errores de backup.
     */
    public static class BackupException extends Exception {
        public BackupException(String message) {
            super(message);
        }
    }
}
