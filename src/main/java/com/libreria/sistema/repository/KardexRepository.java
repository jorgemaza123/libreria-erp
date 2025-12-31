package com.libreria.sistema.repository;

import com.libreria.sistema.model.Kardex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KardexRepository extends JpaRepository<Kardex, Long> {

    @EntityGraph(attributePaths = {"producto"})
    Page<Kardex> findAll(Pageable pageable);

    // NUEVOS MÉTODOS DE CONTEO RÁPIDO
    long countByTipo(String tipo); // Cuenta cuántos "ENTRADA" o "SALIDA" hay
}