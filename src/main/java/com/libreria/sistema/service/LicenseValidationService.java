package com.libreria.sistema.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@Slf4j
public class LicenseValidationService {

    // Llave secreta compartida para AES (32 caracteres = 256 bits)
    private static final String SECRET_KEY = "adminlicencejlmr-1995-2026-KEY32";
    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
    private static final int DIAS_GRACIA = 5;

    private final SystemConfigurationService configService;

    public LicenseValidationService(SystemConfigurationService configService) {
        this.configService = configService;
    }

    /**
     * Enum para los estados de licencia.
     */
    public enum EstadoLicencia {
        ACTIVO,      // Licencia válida y vigente
        EN_GRACIA,   // Licencia vencida pero dentro del periodo de gracia (5 días)
        BLOQUEADO,   // Licencia vencida y fuera del periodo de gracia
        SIN_LICENCIA, // No hay licencia registrada
        INVALIDA     // La licencia no corresponde a esta instalación
    }

    /**
     * DTO con la información de la licencia.
     */
    @Data
    public static class LicenseInfo {
        private EstadoLicencia estado;
        private String hardwareId;
        private LocalDate fechaVencimiento;
        private boolean sunatActivo;
        private String nivelPlan;
        private int diasRestantes; // Positivo si vigente, negativo si vencido
        private String mensaje;
        private boolean alertaPago; // Para mostrar advertencia en header

        public static LicenseInfo sinLicencia() {
            LicenseInfo info = new LicenseInfo();
            info.estado = EstadoLicencia.SIN_LICENCIA;
            info.mensaje = "No hay licencia registrada. Ingrese su código de activación.";
            info.alertaPago = false;
            info.sunatActivo = false;
            return info;
        }

        public static LicenseInfo invalida(String mensaje) {
            LicenseInfo info = new LicenseInfo();
            info.estado = EstadoLicencia.INVALIDA;
            info.mensaje = mensaje;
            info.alertaPago = false;
            info.sunatActivo = false;
            return info;
        }
    }

    /**
     * Valida la licencia actual del sistema.
     * Este es el método principal que debe llamarse para verificar el estado.
     */
    public LicenseInfo validarLicencia() {
        try {
            // 1. Obtener el hash de licencia guardado
            String licenseHash = configService.getLicenseHash().orElse(null);

            if (licenseHash == null || licenseHash.isBlank()) {
                log.warn("No hay licencia registrada en el sistema");
                return LicenseInfo.sinLicencia();
            }

            // 2. Desencriptar la licencia
            String licenciaPlana = desencriptar(licenseHash);

            if (licenciaPlana == null || licenciaPlana.isBlank()) {
                log.error("Error al desencriptar la licencia");
                return LicenseInfo.invalida("Error al procesar la licencia. Contacte soporte.");
            }

            // 3. Parsear: HARDWARE_ID|FECHA_VENCIMIENTO|SUNAT_ACTIVO|NIVEL_PLAN
            String[] partes = licenciaPlana.split("\\|");

            if (partes.length < 4) {
                log.error("Formato de licencia inválido: {}", licenciaPlana);
                return LicenseInfo.invalida("Formato de licencia inválido.");
            }

            String hardwareId = partes[0];
            LocalDate fechaVencimiento = LocalDate.parse(partes[1], DateTimeFormatter.ISO_DATE);
            boolean sunatActivo = "1".equals(partes[2]) || "true".equalsIgnoreCase(partes[2]);
            String nivelPlan = partes[3];

            // 4. Validación Anti-Copia: Comparar HARDWARE_ID con INSTALLATION_UUID
            String installationUUID = configService.getOrCreateInstallationUUID();

            if (!hardwareId.equalsIgnoreCase(installationUUID)) {
                log.error("Licencia no corresponde a esta instalación. UUID esperado: {}, UUID en licencia: {}",
                        installationUUID, hardwareId);
                return LicenseInfo.invalida("Esta licencia no corresponde a esta instalación. UUID: " + installationUUID);
            }

            // 5. Calcular días restantes
            long diasRestantes = ChronoUnit.DAYS.between(LocalDate.now(), fechaVencimiento);

            // 6. Determinar estado
            LicenseInfo info = new LicenseInfo();
            info.setHardwareId(hardwareId);
            info.setFechaVencimiento(fechaVencimiento);
            info.setSunatActivo(sunatActivo);
            info.setNivelPlan(nivelPlan);
            info.setDiasRestantes((int) diasRestantes);

            if (diasRestantes >= 0) {
                // ACTIVO: La fecha de vencimiento es futura o es hoy
                info.setEstado(EstadoLicencia.ACTIVO);
                info.setAlertaPago(diasRestantes <= 7); // Alerta si quedan 7 días o menos
                info.setMensaje("Licencia activa. " + (diasRestantes == 0 ? "Vence hoy." :
                        "Vence en " + diasRestantes + " día(s)."));

            } else if (Math.abs(diasRestantes) <= DIAS_GRACIA) {
                // EN_GRACIA: Venció hace menos de 5 días
                info.setEstado(EstadoLicencia.EN_GRACIA);
                info.setAlertaPago(true);
                info.setMensaje("Licencia vencida hace " + Math.abs(diasRestantes) +
                        " día(s). Tiene " + (DIAS_GRACIA - Math.abs(diasRestantes)) +
                        " día(s) de gracia restantes.");

            } else {
                // BLOQUEADO: Venció hace más de 5 días
                info.setEstado(EstadoLicencia.BLOQUEADO);
                info.setAlertaPago(true);
                info.setSunatActivo(false); // Forzar SUNAT desactivado
                info.setMensaje("Licencia vencida. El sistema está bloqueado. Renueve su licencia para continuar.");
            }

            log.info("Validación de licencia: Estado={}, Días={}, Plan={}",
                    info.getEstado(), diasRestantes, nivelPlan);

            return info;

        } catch (Exception e) {
            log.error("Error validando licencia: {}", e.getMessage(), e);
            return LicenseInfo.invalida("Error al validar licencia: " + e.getMessage());
        }
    }

    /**
     * Registra una nueva licencia en el sistema.
     */
    public LicenseInfo registrarLicencia(String licenseHash) {
        try {
            // Validar que el hash sea desencriptable antes de guardar
            String licenciaPlana = desencriptar(licenseHash);

            if (licenciaPlana == null || licenciaPlana.isBlank()) {
                return LicenseInfo.invalida("El código de licencia no es válido.");
            }

            String[] partes = licenciaPlana.split("\\|");
            if (partes.length < 4) {
                return LicenseInfo.invalida("Formato de licencia inválido.");
            }

            // Verificar que el hardware ID coincida
            String hardwareId = partes[0];
            String installationUUID = configService.getOrCreateInstallationUUID();

            if (!hardwareId.equalsIgnoreCase(installationUUID)) {
                return LicenseInfo.invalida("Esta licencia fue generada para otra instalación. " +
                        "Su UUID es: " + installationUUID);
            }

            // Guardar la licencia
            configService.saveLicenseHash(licenseHash);
            log.info("Licencia registrada correctamente");

            // Retornar el estado actualizado
            return validarLicencia();

        } catch (Exception e) {
            log.error("Error registrando licencia: {}", e.getMessage(), e);
            return LicenseInfo.invalida("Error al registrar licencia: " + e.getMessage());
        }
    }

    /**
     * Obtiene el UUID de instalación para mostrarlo al usuario.
     */
    public String getInstallationUUID() {
        return configService.getOrCreateInstallationUUID();
    }

    /**
     * Desactiva SUNAT y permite trabajar en modo offline.
     */
    public void desactivarSunat() {
        configService.setSunatModo("OFFLINE");
        log.info("SUNAT desactivado - Modo OFFLINE activado");
    }

    /**
     * Reactiva SUNAT (solo si la licencia lo permite).
     */
    public boolean activarSunat() {
        LicenseInfo info = validarLicencia();
        if (info.isSunatActivo() && info.getEstado() != EstadoLicencia.BLOQUEADO) {
            configService.setSunatModo("ACTIVO");
            log.info("SUNAT activado");
            return true;
        }
        return false;
    }

    /**
     * Verifica si SUNAT está activo según la licencia y configuración.
     */
    public boolean isSunatActivo() {
        LicenseInfo info = validarLicencia();
        if (info.getEstado() == EstadoLicencia.BLOQUEADO) {
            return false;
        }
        String modo = configService.getSunatModo();
        return "ACTIVO".equals(modo) && info.isSunatActivo();
    }

    // ============================================
    // MÉTODOS PRIVADOS DE ENCRIPTACIÓN
    // ============================================

    /**
     * Desencripta un hash de licencia usando AES/ECB/PKCS5Padding.
     */
    private String desencriptar(String encryptedText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error al desencriptar: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Encripta un texto plano (útil para generar licencias de prueba).
     * Formato: HARDWARE_ID|FECHA_VENCIMIENTO|SUNAT_ACTIVO|NIVEL_PLAN
     */
    public String encriptar(String plainText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);

        } catch (Exception e) {
            log.error("Error al encriptar: {}", e.getMessage());
            return null;
        }
    }
}
