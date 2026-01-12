package com.libreria.sistema.repository;

import com.libreria.sistema.model.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, String> {

    Optional<SystemConfiguration> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);
}
