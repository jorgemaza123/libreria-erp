package com.libreria.sistema.repository;

import com.libreria.sistema.model.LaminaCategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LaminaCategoriaRepository extends JpaRepository<LaminaCategoria, Long> {

    Optional<LaminaCategoria> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    List<LaminaCategoria> findByActivoTrueOrderByOrdenAscNombreAsc();

    List<LaminaCategoria> findAllByOrderByActivoDescOrdenAscNombreAsc();
}
