package com.libreria.sistema.repository;

import com.libreria.sistema.model.ConfiguracionSunat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracionSunatRepository extends JpaRepository<ConfiguracionSunat, Long> {

    /**
     * Obtiene la configuración activa de SUNAT
     * (Asumimos que solo hay una configuración en el sistema)
     */
    Optional<ConfiguracionSunat> findFirstByOrderByIdDesc();

    /**
     * Verifica si la facturación electrónica está activa
     */
    Optional<ConfiguracionSunat> findByFacturaElectronicaActiva(Boolean activa);
}
