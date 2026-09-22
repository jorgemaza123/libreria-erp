package com.libreria.sistema.repository;

import com.libreria.sistema.model.HistorialPrecioProducto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialPrecioProductoRepository extends JpaRepository<HistorialPrecioProducto, Long> {

    List<HistorialPrecioProducto> findTop20ByProductoIdOrderByFechaDesc(Long productoId);
}
