package com.libreria.sistema.repository;

import com.libreria.sistema.model.ServicioCategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServicioCategoriaRepository extends JpaRepository<ServicioCategoria, Long> {

    List<ServicioCategoria> findByActivaTrueOrderByOrdenAsc();

    Optional<ServicioCategoria> findByCodigo(String codigo);
}
