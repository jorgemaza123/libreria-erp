package com.libreria.sistema.repository;
import com.libreria.sistema.model.Compra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    // Optimizado para listar compras sin problema N+1 (trae proveedor y detalles)
    @EntityGraph(attributePaths = {"proveedor", "detalles"})
    Page<Compra> findAll(Pageable pageable);
}