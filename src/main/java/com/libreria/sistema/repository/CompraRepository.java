package com.libreria.sistema.repository;
import com.libreria.sistema.model.Compra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    // Optimizado para listar compras sin problema N+1 (trae proveedor y detalles)
    @EntityGraph(attributePaths = {"proveedor", "detalles"})
    Page<Compra> findAll(Pageable pageable);

    /**
     * Suma total de compras no anuladas en un periodo
     */
    @Query("SELECT COALESCE(SUM(c.total), 0) FROM Compra c " +
           "WHERE c.fecha BETWEEN :inicio AND :fin AND c.estado != 'ANULADA'")
    BigDecimal sumTotalByPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    /**
     * Cuenta compras no anuladas en un periodo
     */
    @Query("SELECT COUNT(c) FROM Compra c " +
           "WHERE c.fecha BETWEEN :inicio AND :fin AND c.estado != 'ANULADA'")
    long countByPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    /**
     * Compras no anuladas en un periodo
     */
    @Query("SELECT c FROM Compra c WHERE c.fecha BETWEEN :inicio AND :fin AND c.estado != 'ANULADA'")
    List<Compra> findByPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}