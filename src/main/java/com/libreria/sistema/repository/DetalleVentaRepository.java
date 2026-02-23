package com.libreria.sistema.repository;

import com.libreria.sistema.model.DetalleVenta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {

    /**
     * Suma la utilidad total de ventas válidas en un rango de fechas.
     * Filtra utilidadTotal IS NOT NULL para ignorar ventas anteriores al cambio.
     */
    @Query("SELECT COALESCE(SUM(d.utilidadTotal), 0) FROM DetalleVenta d " +
           "WHERE d.venta.fechaEmision BETWEEN :inicio AND :fin " +
           "AND d.venta.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "AND d.utilidadTotal IS NOT NULL")
    BigDecimal sumarUtilidadPorPeriodo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Utilidad agrupada por año-mes (portable: YEAR/MONTH en vez de TO_CHAR).
     * Retorna [year, month, sumUtilidad]
     */
    @Query("SELECT YEAR(v.fechaEmision), MONTH(v.fechaEmision), COALESCE(SUM(d.utilidadTotal), 0) " +
           "FROM DetalleVenta d JOIN d.venta v " +
           "WHERE v.fechaEmision >= :inicio " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "AND d.utilidadTotal IS NOT NULL " +
           "GROUP BY YEAR(v.fechaEmision), MONTH(v.fechaEmision) " +
           "ORDER BY YEAR(v.fechaEmision), MONTH(v.fechaEmision)")
    List<Object[]> utilidadMensualAgrupada(@Param("inicio") LocalDate inicio);

    /**
     * Top productos por utilidad total (descendente).
     * Retorna [productoId, nombre, categoria, sumCantidad, sumUtilidad, sumIngreso]
     */
    @Query("SELECT d.producto.id, d.producto.nombre, d.producto.categoria, " +
           "SUM(d.cantidad), SUM(d.utilidadTotal), SUM(d.subtotal) " +
           "FROM DetalleVenta d " +
           "WHERE d.venta.fechaEmision BETWEEN :inicio AND :fin " +
           "AND d.venta.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "AND d.utilidadTotal IS NOT NULL " +
           "GROUP BY d.producto.id, d.producto.nombre, d.producto.categoria " +
           "ORDER BY SUM(d.utilidadTotal) DESC")
    List<Object[]> topProductosPorUtilidad(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin, Pageable pageable);

    /**
     * Productos vendidos con margen porcentual menor al mínimo configurado.
     * Retorna [productoId, nombre, sumIngreso, sumUtilidad, margenPorcentaje]
     */
    @Query("SELECT d.producto.id, d.producto.nombre, " +
           "SUM(d.subtotal), SUM(d.utilidadTotal), " +
           "CASE WHEN SUM(d.subtotal) > 0 THEN (SUM(d.utilidadTotal) / SUM(d.subtotal)) * 100 ELSE 0 END " +
           "FROM DetalleVenta d " +
           "WHERE d.venta.fechaEmision BETWEEN :inicio AND :fin " +
           "AND d.venta.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "AND d.utilidadTotal IS NOT NULL " +
           "GROUP BY d.producto.id, d.producto.nombre " +
           "HAVING SUM(d.subtotal) > 0 " +
           "AND (SUM(d.utilidadTotal) / SUM(d.subtotal)) * 100 < :margenMinimo " +
           "ORDER BY (SUM(d.utilidadTotal) / SUM(d.subtotal)) * 100 ASC")
    List<Object[]> productosConMargenBajo(@Param("inicio") LocalDate inicio,
                                          @Param("fin") LocalDate fin,
                                          @Param("margenMinimo") BigDecimal margenMinimo);
}
