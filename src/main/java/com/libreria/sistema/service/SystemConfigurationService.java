package com.libreria.sistema.service;

import com.libreria.sistema.model.SystemConfiguration;
import com.libreria.sistema.repository.SystemConfigurationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class SystemConfigurationService {

    // Claves de configuración del sistema
    public static final String KEY_INSTALLATION_UUID = "INSTALLATION_UUID";
    public static final String KEY_LICENSE_HASH = "LICENSE_HASH";
    public static final String KEY_SUNAT_MODO = "SUNAT_MODO"; // ACTIVO / OFFLINE

    private final SystemConfigurationRepository repository;

    public SystemConfigurationService(SystemConfigurationRepository repository) {
        this.repository = repository;
    }

    /**
     * Obtiene el valor de una configuración por su clave.
     */
    public Optional<String> getValue(String key) {
        return repository.findByConfigKey(key)
                .map(SystemConfiguration::getConfigValue);
    }

    /**
     * Guarda o actualiza una configuración.
     */
    @Transactional
    public void setValue(String key, String value) {
        setValue(key, value, null);
    }

    /**
     * Guarda o actualiza una configuración con descripción.
     */
    @Transactional
    public void setValue(String key, String value, String descripcion) {
        SystemConfiguration config = repository.findByConfigKey(key)
                .orElse(new SystemConfiguration());

        config.setConfigKey(key);
        config.setConfigValue(value);
        if (descripcion != null) {
            config.setDescripcion(descripcion);
        }

        repository.save(config);
        log.info("Configuración guardada: {} = {}", key,
                key.contains("LICENSE") || key.contains("UUID") ? "[PROTEGIDO]" : value);
    }

    /**
     * Verifica si existe una configuración.
     */
    public boolean exists(String key) {
        return repository.existsByConfigKey(key);
    }

    /**
     * Elimina una configuración.
     */
    @Transactional
    public void delete(String key) {
        repository.deleteById(key);
    }

    /**
     * Obtiene o genera el UUID de instalación.
     * Este UUID es único para cada instalación y se usa para validar licencias.
     */
    @Transactional
    public String getOrCreateInstallationUUID() {
        Optional<String> existingUUID = getValue(KEY_INSTALLATION_UUID);

        if (existingUUID.isPresent()) {
            return existingUUID.get();
        }

        // Generar nuevo UUID único para esta instalación
        String newUUID = UUID.randomUUID().toString().toUpperCase();
        setValue(KEY_INSTALLATION_UUID, newUUID, "UUID único de instalación - NO MODIFICAR");

        log.info("UUID de instalación generado: {}", newUUID);
        return newUUID;
    }

    /**
     * Obtiene el hash de licencia guardado.
     */
    public Optional<String> getLicenseHash() {
        return getValue(KEY_LICENSE_HASH);
    }

    /**
     * Guarda el hash de licencia.
     */
    @Transactional
    public void saveLicenseHash(String licenseHash) {
        setValue(KEY_LICENSE_HASH, licenseHash, "Hash de licencia encriptado");
    }

    /**
     * Obtiene el modo SUNAT (ACTIVO / OFFLINE).
     */
    public String getSunatModo() {
        return getValue(KEY_SUNAT_MODO).orElse("OFFLINE");
    }

    /**
     * Guarda el modo SUNAT.
     */
    @Transactional
    public void setSunatModo(String modo) {
        setValue(KEY_SUNAT_MODO, modo, "Modo de operación SUNAT");
    }
}
