package com.libreria.sistema.repository;

import com.libreria.sistema.model.Correlativo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CorrelativoRepository extends JpaRepository<Correlativo, Long> {
    Optional<Correlativo> findByCodigoAndSerie(String codigo, String serie);

    /**
     * Busca el correlativo con lock pesimista para evitar race conditions
     * en la generación de números de comprobantes
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Correlativo c WHERE c.codigo = :codigo AND c.serie = :serie")
    Optional<Correlativo> findByCodigoAndSerieWithLock(@Param("codigo") String codigo, @Param("serie") String serie);
}