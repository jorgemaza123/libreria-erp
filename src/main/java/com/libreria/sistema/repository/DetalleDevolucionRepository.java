package com.libreria.sistema.repository;

import com.libreria.sistema.model.DetalleDevolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetalleDevolucionRepository extends JpaRepository<DetalleDevolucion, Long> {
}
