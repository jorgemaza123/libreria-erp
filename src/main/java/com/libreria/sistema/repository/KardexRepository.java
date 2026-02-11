package com.libreria.sistema.repository;

import com.libreria.sistema.model.Kardex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface KardexRepository extends JpaRepository<Kardex, Long> {

    @EntityGraph(attributePaths = {"producto"})
    Page<Kardex> findAll(Pageable pageable);

    // NUEVOS MÉTODOS DE CONTEO RÁPIDO
    long countByTipo(String tipo); // Cuenta cuántos "ENTRADA" o "SALIDA" hay

    // STOCK MODULE
    @Query("SELECT k FROM Kardex k WHERE k.producto.id = :productoId ORDER BY k.fecha DESC")
    Page<Kardex> findByProductoId(@Param("productoId") Long productoId, Pageable pageable);

    @Query("SELECT k FROM Kardex k WHERE k.producto.id = :productoId AND k.fecha >= :desde ORDER BY k.fecha ASC")
    List<Kardex> findByProductoIdAndFechaAfter(@Param("productoId") Long productoId, @Param("desde") LocalDateTime desde);

    long countByProductoId(Long productoId);
}