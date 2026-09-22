package com.libreria.sistema.repository;

import com.libreria.sistema.model.ServicioCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ServicioCategoriaRepository extends JpaRepository<ServicioCategoria, Long> {

    List<ServicioCategoria> findByActivaTrueOrderByOrdenAsc();

    Optional<ServicioCategoria> findByCodigo(String codigo);

    Optional<ServicioCategoria> findByNombreIgnoreCase(String nombre);

    @Query("SELECT COALESCE(MAX(c.orden), 0) FROM ServicioCategoria c")
    int findMaxOrden();
}
