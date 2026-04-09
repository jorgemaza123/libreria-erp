package com.libreria.sistema.repository;

import com.libreria.sistema.model.CategoriaAdicionalPersonalizado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaAdicionalPersonalizadoRepository extends JpaRepository<CategoriaAdicionalPersonalizado, Long> {
    Optional<CategoriaAdicionalPersonalizado> findByCodigo(String codigo);
    List<CategoriaAdicionalPersonalizado> findAllByOrderByOrdenAscNombreAsc();
    List<CategoriaAdicionalPersonalizado> findAllByOrderByActivoDescOrdenAscNombreAsc();
    List<CategoriaAdicionalPersonalizado> findByActivoTrueOrderByOrdenAscNombreAsc();
}
