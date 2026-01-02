package com.libreria.sistema.repository;

import com.libreria.sistema.model.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, Long> {
    // No necesitamos métodos especiales, solo usaremos findById(1L)
}