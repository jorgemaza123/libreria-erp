package com.libreria.sistema.repository;

import com.libreria.sistema.model.MovimientoCaja;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CajaRepository extends JpaRepository<MovimientoCaja, Long> {

    // =====================================================
    //  CONSULTAS OPTIMIZADAS PARA REPORTES (FILTRO EN BD)
    // =====================================================

    /**
     * Buscar movimientos por rango de fechas (LocalDateTime) - PARA REPORTES
     * Evita traer toda la historia a memoria
     */
    List<MovimientoCaja> findByFechaBetween(LocalDateTime inicio, LocalDateTime fin);

    /**
     * Buscar movimientos por rango de fechas usando LocalDate
     * Convierte automáticamente a inicio y fin del día
     */
    @Query("SELECT m FROM MovimientoCaja m WHERE CAST(m.fecha AS LocalDate) BETWEEN :inicio AND :fin ORDER BY m.fecha DESC")
    List<MovimientoCaja> findByFechaBetweenDates(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Buscar movimientos por rango de fechas con paginación
     */
    @Query("SELECT m FROM MovimientoCaja m WHERE CAST(m.fecha AS LocalDate) BETWEEN :inicio AND :fin ORDER BY m.fecha DESC")
    Page<MovimientoCaja> findByFechaBetweenDatesPaginated(
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin,
            Pageable pageable);

    /**
     * Buscar movimientos por tipo y rango de fechas
     */
    @Query("SELECT m FROM MovimientoCaja m WHERE m.tipo = :tipo AND CAST(m.fecha AS LocalDate) BETWEEN :inicio AND :fin ORDER BY m.fecha DESC")
    List<MovimientoCaja> findByTipoAndFechaBetween(
            @Param("tipo") String tipo,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    // =====================================================
    //  CONSULTAS PARA DASHBOARD Y RESÚMENES
    // =====================================================

    /**
     * Sumar montos por tipo desde una fecha específica
     */
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.tipo = :tipo AND m.fecha >= :inicio")
    BigDecimal sumarPorTipoDesde(@Param("tipo") String tipo, @Param("inicio") LocalDateTime inicio);

    /**
     * Sumar ingresos en un rango de fechas
     */
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.tipo = 'INGRESO' AND CAST(m.fecha AS LocalDate) BETWEEN :inicio AND :fin")
    BigDecimal sumarIngresosPorFechas(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Sumar egresos en un rango de fechas
     */
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.tipo = 'EGRESO' AND CAST(m.fecha AS LocalDate) BETWEEN :inicio AND :fin")
    BigDecimal sumarEgresosPorFechas(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Contar movimientos por rango de fechas
     */
    @Query("SELECT COUNT(m) FROM MovimientoCaja m WHERE CAST(m.fecha AS LocalDate) BETWEEN :inicio AND :fin")
    long countByFechaBetween(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    // =====================================================
    //  CONSULTAS POR SESIÓN Y USUARIO
    // =====================================================

    /**
     * Movimientos de una sesión específica
     */
    @Query("SELECT m FROM MovimientoCaja m WHERE m.sesion.id = :sesionId ORDER BY m.fecha DESC")
    List<MovimientoCaja> findBySesionId(@Param("sesionId") Long sesionId);

    /**
     * Movimientos de un usuario en un rango de fechas
     */
    @Query("SELECT m FROM MovimientoCaja m WHERE m.usuario.id = :usuarioId AND CAST(m.fecha AS LocalDate) BETWEEN :inicio AND :fin ORDER BY m.fecha DESC")
    List<MovimientoCaja> findByUsuarioAndFechaBetween(
            @Param("usuarioId") Long usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    // =====================================================
    //  CONSULTAS CON PAGINACIÓN GENERAL
    // =====================================================

    /**
     * Listado paginado de todos los movimientos
     */
    @Query("SELECT m FROM MovimientoCaja m ORDER BY m.fecha DESC")
    Page<MovimientoCaja> findAllPaginated(Pageable pageable);

    /**
     * Listado paginado por tipo de movimiento
     */
    @Query("SELECT m FROM MovimientoCaja m WHERE m.tipo = :tipo ORDER BY m.fecha DESC")
    Page<MovimientoCaja> findByTipoPaginated(@Param("tipo") String tipo, Pageable pageable);

    // =====================================================
    //  CONSULTAS PARA REPORTES FINANCIEROS
    // =====================================================

    /**
     * Resumen diario de movimientos (para gráficos)
     */
    @Query("SELECT CAST(m.fecha AS LocalDate), m.tipo, SUM(m.monto) FROM MovimientoCaja m " +
           "WHERE CAST(m.fecha AS LocalDate) BETWEEN :inicio AND :fin " +
           "GROUP BY CAST(m.fecha AS LocalDate), m.tipo ORDER BY CAST(m.fecha AS LocalDate)")
    List<Object[]> resumenDiarioPorTipo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Movimientos relacionados con ventas en un periodo
     */
    @Query("SELECT m FROM MovimientoCaja m WHERE m.concepto LIKE 'VENTA%' AND CAST(m.fecha AS LocalDate) BETWEEN :inicio AND :fin ORDER BY m.fecha DESC")
    List<MovimientoCaja> findMovimientosVentas(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);
}
