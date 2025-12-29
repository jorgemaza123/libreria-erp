package com.libreria.sistema.repository;

import com.libreria.sistema.model.Correlativo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CorrelativoRepository extends JpaRepository<Correlativo, Long> {
    Optional<Correlativo> findByCodigoAndSerie(String codigo, String serie);
}