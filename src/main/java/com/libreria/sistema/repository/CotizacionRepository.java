package com.libreria.sistema.repository;

import com.libreria.sistema.model.Cotizacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {

    long countByEstado(String estado);

    @Query("SELECT COUNT(c) FROM Cotizacion c WHERE c.estado IN :estados")
    long countByEstadoIn(@Param("estados") List<String> estados);

    @Query("SELECT COUNT(c) FROM Cotizacion c WHERE c.fechaCreacion BETWEEN :desde AND :hasta")
    long countByFechaCreacionBetween(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("SELECT COUNT(c) FROM Cotizacion c WHERE c.estado IN :estados AND c.fechaCreacion BETWEEN :desde AND :hasta")
    long countByEstadoInAndFechaCreacionBetween(@Param("estados") List<String> estados,
                                                 @Param("desde") LocalDateTime desde,
                                                 @Param("hasta") LocalDateTime hasta);

    @Query("SELECT COALESCE(SUM(c.total), 0) FROM Cotizacion c WHERE c.fechaCreacion BETWEEN :desde AND :hasta")
    BigDecimal sumTotalByFechaCreacionBetween(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("SELECT COALESCE(SUM(c.total), 0) FROM Cotizacion c WHERE c.estado IN :estados AND c.fechaCreacion BETWEEN :desde AND :hasta")
    BigDecimal sumTotalByEstadoInAndFechaCreacionBetween(@Param("estados") List<String> estados,
                                                          @Param("desde") LocalDateTime desde,
                                                          @Param("hasta") LocalDateTime hasta);

    // Vencimiento automático
    List<Cotizacion> findByFechaVencimientoBeforeAndEstadoIn(LocalDate fecha, List<String> estados);

    // Búsqueda filtrada
    @Query(value = "SELECT * FROM cotizaciones c WHERE 1=1 " +
           "AND (:termino IS NULL OR LOWER(c.cliente_nombre) LIKE LOWER(CONCAT('%', :termino, '%')) " +
           "    OR CAST(c.numero AS TEXT) LIKE CONCAT('%', :termino, '%') " +
           "    OR LOWER(c.cliente_documento) LIKE LOWER(CONCAT('%', :termino, '%'))) " +
           "AND (:estado IS NULL OR c.estado = :estado) " +
           "AND (:desde IS NULL OR c.fecha_creacion >= CAST(:desde AS TIMESTAMP)) " +
           "AND (:hasta IS NULL OR c.fecha_creacion <= CAST(:hasta AS TIMESTAMP)) ",
           countQuery = "SELECT COUNT(*) FROM cotizaciones c WHERE 1=1 " +
           "AND (:termino IS NULL OR LOWER(c.cliente_nombre) LIKE LOWER(CONCAT('%', :termino, '%')) " +
           "    OR CAST(c.numero AS TEXT) LIKE CONCAT('%', :termino, '%') " +
           "    OR LOWER(c.cliente_documento) LIKE LOWER(CONCAT('%', :termino, '%'))) " +
           "AND (:estado IS NULL OR c.estado = :estado) " +
           "AND (:desde IS NULL OR c.fecha_creacion >= CAST(:desde AS TIMESTAMP)) " +
           "AND (:hasta IS NULL OR c.fecha_creacion <= CAST(:hasta AS TIMESTAMP)) ",
           nativeQuery = true)
    Page<Cotizacion> buscarFiltrado(@Param("termino") String termino,
                                     @Param("estado") String estado,
                                     @Param("desde") String desde,
                                     @Param("hasta") String hasta,
                                     Pageable pageable);

    // Rentabilidad por categoría de servicio (cotizaciones convertidas) — incluye margen
    @Query(value = "SELECT dc.categoria_servicio as categoria, " +
           "COALESCE(SUM(dc.subtotal), 0) as ingresos, " +
           "COALESCE(SUM(dc.costo_estimado * dc.cantidad), 0) as costos, " +
           "COALESCE(SUM(dc.subtotal) - SUM(COALESCE(dc.costo_estimado, 0) * dc.cantidad), 0) as margen, " +
           "COUNT(dc.id) as cantidad " +
           "FROM detalle_cotizaciones dc " +
           "JOIN cotizaciones c ON dc.cotizacion_id = c.id " +
           "WHERE dc.tipo_item = 'SERVICIO' " +
           "AND c.estado IN ('CONVERTIDA', 'CONVERTIDO_VENTA') " +
           "AND c.fecha_creacion BETWEEN CAST(:desde AS TIMESTAMP) AND CAST(:hasta AS TIMESTAMP) " +
           "AND dc.categoria_servicio IS NOT NULL " +
           "GROUP BY dc.categoria_servicio " +
           "ORDER BY ingresos DESC",
           nativeQuery = true)
    List<Object[]> rentabilidadPorCategoriaServicio(@Param("desde") String desde, @Param("hasta") String hasta);

    // Rentabilidad desde DetalleVenta (ventas reales, incluye productos convertidos desde cotización)
    @Query(value = "SELECT dv.categoria_servicio as categoria, " +
           "COALESCE(SUM(dv.subtotal), 0) as ingresos, " +
           "COUNT(dv.id) as cantidad " +
           "FROM detalle_ventas dv " +
           "JOIN ventas v ON dv.venta_id = v.id " +
           "WHERE dv.tipo_item = 'SERVICIO' " +
           "AND dv.categoria_servicio IS NOT NULL " +
           "AND v.estado NOT IN ('ANULADO', 'DEVUELTO_TOTAL') " +
           "AND v.fecha_emision BETWEEN :desde AND :hasta " +
           "GROUP BY dv.categoria_servicio " +
           "ORDER BY ingresos DESC",
           nativeQuery = true)
    List<Object[]> rentabilidadServiciosVentas(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    // Tendencia mensual de cotizaciones (12 meses)
    @Query(value = "SELECT TO_CHAR(c.fecha_creacion, 'YYYY-MM') as mes, " +
           "COUNT(c.id) as total_cotizaciones, " +
           "COALESCE(SUM(c.total), 0) as monto_total, " +
           "SUM(CASE WHEN c.estado IN ('CONVERTIDA', 'CONVERTIDO_VENTA') THEN 1 ELSE 0 END) as convertidas " +
           "FROM cotizaciones c " +
           "WHERE c.fecha_creacion >= CAST(:desde AS TIMESTAMP) " +
           "GROUP BY TO_CHAR(c.fecha_creacion, 'YYYY-MM') " +
           "ORDER BY mes ASC",
           nativeQuery = true)
    List<Object[]> tendenciaMensual(@Param("desde") String desde);

    // Conversión por categoría de servicio
    @Query(value = "SELECT dc.categoria_servicio as categoria, " +
           "COUNT(DISTINCT c.id) as total_cotizaciones, " +
           "SUM(CASE WHEN c.estado IN ('CONVERTIDA', 'CONVERTIDO_VENTA') THEN 1 ELSE 0 END) as convertidas " +
           "FROM detalle_cotizaciones dc " +
           "JOIN cotizaciones c ON dc.cotizacion_id = c.id " +
           "WHERE dc.tipo_item = 'SERVICIO' " +
           "AND dc.categoria_servicio IS NOT NULL " +
           "AND c.fecha_creacion BETWEEN CAST(:desde AS TIMESTAMP) AND CAST(:hasta AS TIMESTAMP) " +
           "GROUP BY dc.categoria_servicio " +
           "ORDER BY convertidas DESC",
           nativeQuery = true)
    List<Object[]> conversionPorCategoria(@Param("desde") String desde, @Param("hasta") String hasta);
}
