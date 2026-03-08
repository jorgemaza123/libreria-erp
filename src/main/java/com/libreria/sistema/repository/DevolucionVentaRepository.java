package com.libreria.sistema.repository;

import com.libreria.sistema.model.DevolucionVenta;
import com.libreria.sistema.model.Venta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DevolucionVentaRepository extends JpaRepository<DevolucionVenta, Long> {

    List<DevolucionVenta> findByVentaOriginal(Venta venta);

    Page<DevolucionVenta> findByFechaEmisionBetween(LocalDate inicio, LocalDate fin, Pageable pageable);

    Page<DevolucionVenta> findByEstado(String estado, Pageable pageable);

    // CORRECCIÓN: Agregamos CAST(... as date) para que PostgreSQL entienda los nulos
    @Query("SELECT d FROM DevolucionVenta d WHERE " +
           "(:estado IS NULL OR d.estado = :estado) AND " +
           "(CAST(:fechaInicio AS date) IS NULL OR d.fechaEmision >= :fechaInicio) AND " +
           "(CAST(:fechaFin AS date) IS NULL OR d.fechaEmision <= :fechaFin) " +
           "ORDER BY d.fechaEmision DESC, d.id DESC")
    Page<DevolucionVenta> buscarConFiltros(
        @Param("estado") String estado,
        @Param("fechaInicio") LocalDate fechaInicio,
        @Param("fechaFin") LocalDate fechaFin,
        Pageable pageable
    );

    List<DevolucionVenta> findTop10ByOrderByFechaCreacionDesc();

    /**
     * Suma total devuelto en efectivo en un periodo
     */
    @Query("SELECT COALESCE(SUM(d.totalDevuelto), 0) FROM DevolucionVenta d " +
           "WHERE d.metodoReembolso = 'EFECTIVO' AND d.estado = 'PROCESADA' " +
           "AND d.fechaEmision BETWEEN :inicio AND :fin")
    java.math.BigDecimal sumDevolucionesEfectivoPorPeriodo(
           @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Cuenta devoluciones procesadas en efectivo en un periodo
     */
    @Query("SELECT COUNT(d) FROM DevolucionVenta d " +
           "WHERE d.metodoReembolso = 'EFECTIVO' AND d.estado = 'PROCESADA' " +
           "AND d.fechaEmision BETWEEN :inicio AND :fin")
    long countDevolucionesEfectivoPorPeriodo(
           @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Suma el total devuelto agrupado por ventaId para una lista de ventas (una sola query).
     * Retorna pares [ventaId, totalDevuelto].
     */
    @Query("SELECT d.ventaOriginal.id, COALESCE(SUM(d.totalDevuelto), 0) " +
           "FROM DevolucionVenta d " +
           "WHERE d.ventaOriginal.id IN :ventaIds AND d.estado != 'ANULADA' " +
           "GROUP BY d.ventaOriginal.id")
    List<Object[]> sumTotalesDevueltosPorVentas(@Param("ventaIds") List<Long> ventaIds);
}