package com.libreria.sistema.repository;

import com.libreria.sistema.model.DetalleVenta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
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
     * Cantidad vendida por producto en un periodo, para reposicion y rotacion.
     * Retorna [productoId, sumCantidad].
     */
    @Query("SELECT d.producto.id, COALESCE(SUM(d.cantidad), 0) " +
           "FROM DetalleVenta d JOIN d.venta v " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "AND COALESCE(v.entregaPendiente, false) = false " +
           "AND COALESCE(d.producto.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' " +
           "GROUP BY d.producto.id")
    List<Object[]> cantidadVendidaPorProducto(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    @Query("SELECT p.id, COALESCE(SUM(d.cantidad), 0), MAX(v.fechaEmision) " +
           "FROM DetalleVenta d JOIN d.venta v JOIN d.producto p " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "AND COALESCE(v.entregaPendiente, false) = false " +
           "AND p.activo = true " +
           "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' " +
           "AND COALESCE(p.esLamina, false) = false " +
           "AND COALESCE(p.clasificacion, 'MERCADERIA') NOT IN ('INSUMO', 'INACTIVO', 'DESCONOCIDO') " +
           "AND (p.codigoInterno IS NULL OR p.codigoInterno NOT IN :codigosReservados) " +
           "GROUP BY p.id")
    List<Object[]> ventasRecientesProductosPos(@Param("inicio") LocalDate inicio,
                                               @Param("fin") LocalDate fin,
                                               @Param("codigosReservados") Collection<String> codigosReservados);

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

    @Query("SELECT d.producto.id, SUM(d.cantidad), SUM(d.subtotal) " +
           "FROM DetalleVenta d " +
           "WHERE d.producto.id IN :productoIds " +
           "AND d.venta.fechaEmision >= :fecha " +
           "AND d.venta.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "GROUP BY d.producto.id")
    List<Object[]> resumenVentasPorProductoDesde(@Param("productoIds") Collection<Long> productoIds,
                                                 @Param("fecha") LocalDate fecha);

    @Query("SELECT COALESCE(SUM(d.cantidad), 0) " +
           "FROM DetalleVenta d JOIN d.venta v " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "AND COALESCE(v.entregaPendiente, false) = false")
    BigDecimal sumarCantidadVendidaPorPeriodo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    @Query("SELECT COALESCE(SUM(d.cantidad), 0) " +
           "FROM DetalleVenta d JOIN d.venta v JOIN d.producto p " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "AND COALESCE(v.entregaPendiente, false) = false " +
           "AND LOWER(COALESCE(p.categoria, '')) = LOWER(:categoria)")
    BigDecimal sumarCantidadVendidaPorCategoria(@Param("inicio") LocalDate inicio,
                                                @Param("fin") LocalDate fin,
                                                @Param("categoria") String categoria);

    @Query("SELECT COALESCE(p.categoria, ''), COALESCE(SUM(d.cantidad), 0) " +
           "FROM DetalleVenta d JOIN d.venta v JOIN d.producto p " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "AND COALESCE(v.entregaPendiente, false) = false " +
           "GROUP BY COALESCE(p.categoria, '')")
    List<Object[]> sumarCantidadVendidaAgrupadaCategoria(@Param("inicio") LocalDate inicio,
                                                         @Param("fin") LocalDate fin);

    @Query("SELECT COALESCE(SUM(d.subtotal), 0), " +
           "COALESCE(SUM(CASE WHEN d.montoReposicionTotal IS NOT NULL THEN d.montoReposicionTotal ELSE COALESCE(d.costoUnitario, 0) * d.cantidad END), 0), " +
           "COALESCE(SUM(d.utilidadTotal), 0), " +
           "COALESCE(SUM(CASE WHEN d.costoIndirectoUnitario IS NOT NULL THEN d.costoIndirectoUnitario * d.cantidad ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN d.utilidadNetaTotal IS NOT NULL THEN d.utilidadNetaTotal ELSE COALESCE(d.utilidadTotal, 0) END), 0), " +
           "COUNT(DISTINCT v.id) " +
           "FROM DetalleVenta d JOIN d.venta v " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL')")
    Object[] resumenCosteoVentas(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    @Query("SELECT p.id, p.codigoInterno, p.nombre, p.categoria, " +
           "COALESCE(SUM(d.cantidad), 0), " +
           "COALESCE(SUM(d.subtotal), 0), " +
           "COALESCE(SUM(CASE WHEN d.montoReposicionTotal IS NOT NULL THEN d.montoReposicionTotal ELSE COALESCE(d.costoUnitario, 0) * d.cantidad END), 0), " +
           "COALESCE(SUM(d.utilidadTotal), 0), " +
           "COALESCE(SUM(CASE WHEN d.costoIndirectoUnitario IS NOT NULL THEN d.costoIndirectoUnitario * d.cantidad ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN d.utilidadNetaTotal IS NOT NULL THEN d.utilidadNetaTotal ELSE COALESCE(d.utilidadTotal, 0) END), 0), " +
           "MAX(v.fechaEmision) " +
           "FROM DetalleVenta d JOIN d.venta v JOIN d.producto p " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "GROUP BY p.id, p.codigoInterno, p.nombre, p.categoria " +
           "ORDER BY COALESCE(SUM(d.cantidad), 0) DESC, COALESCE(SUM(CASE WHEN d.utilidadNetaTotal IS NOT NULL THEN d.utilidadNetaTotal ELSE COALESCE(d.utilidadTotal, 0) END), 0) DESC")
    List<Object[]> resumenCosteoVentasPorProducto(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    @Query("SELECT p.id, p.codigoInterno, p.nombre, p.categoria, " +
           "COALESCE(SUM(d.cantidad), 0), " +
           "COALESCE(SUM(d.subtotal), 0), " +
           "COALESCE(SUM(CASE WHEN d.utilidadNetaTotal IS NOT NULL THEN d.utilidadNetaTotal ELSE COALESCE(d.utilidadTotal, 0) END), 0), " +
           "MIN(d.precioUnitario), " +
           "MAX(COALESCE(d.precioMinimoSnapshot, p.precioMinimoEmpresarial, d.costoTotalUnitario, d.costoUnitario, 0)), " +
           "COUNT(d) " +
           "FROM DetalleVenta d JOIN d.venta v JOIN d.producto p " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "AND d.precioUnitario < COALESCE(d.precioMinimoSnapshot, p.precioMinimoEmpresarial, d.costoTotalUnitario, d.costoUnitario, 0) " +
           "GROUP BY p.id, p.codigoInterno, p.nombre, p.categoria " +
           "ORDER BY COALESCE(SUM(CASE WHEN d.utilidadNetaTotal IS NOT NULL THEN d.utilidadNetaTotal ELSE COALESCE(d.utilidadTotal, 0) END), 0) ASC")
    List<Object[]> productosVendidosDebajoMinimo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);
}
