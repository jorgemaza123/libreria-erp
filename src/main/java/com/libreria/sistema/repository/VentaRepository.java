package com.libreria.sistema.repository;

import com.libreria.sistema.model.Venta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    // =====================================================
    //  CONSULTAS CON @EntityGraph PARA EVITAR N+1
    // =====================================================

    /**
     * Lista ventas con items y productos pre-cargados (evita N+1).
     * Usar para listados donde se necesita acceder a los detalles.
     */
    @EntityGraph(attributePaths = {"items", "items.producto", "clienteEntity"})
    @Query("SELECT v FROM Venta v WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL) " +
           "ORDER BY v.fechaEmision DESC")
    List<Venta> findByFechaEmisionBetweenWithDetalles(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Lista ventas con items y productos para reportes paginados (evita N+1).
     */
    @EntityGraph(attributePaths = {"items", "items.producto", "clienteEntity"})
    @Query("SELECT v FROM Venta v ORDER BY v.fechaEmision DESC, v.id DESC")
    Page<Venta> findAllWithDetallesPaginated(Pageable pageable);

    // =====================================================
    //  CONSULTAS OPTIMIZADAS PARA REPORTES (FILTRO EN BD)
    // =====================================================

    /**
     * Obtiene ventas filtradas por rango de fechas - PARA REPORTES
     * Evita traer toda la historia a memoria (N+1 Problem)
     */
    @Query("SELECT v FROM Venta v WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL) " +
           "ORDER BY v.fechaEmision DESC")
    List<Venta> findByFechaEmisionBetween(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Obtiene ventas filtradas por rango de fechas con paginación
     */
    @Query("SELECT v FROM Venta v WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL) " +
           "ORDER BY v.fechaEmision DESC")
    Page<Venta> findByFechaEmisionBetweenPaginated(
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin,
            Pageable pageable);

    /**
     * Obtiene ventas desde una fecha específica (útil para reportes de periodo abierto)
     */
    @Query("SELECT v FROM Venta v WHERE v.fechaEmision >= :inicio " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL) " +
           "ORDER BY v.fechaEmision DESC")
    List<Venta> findByFechaEmisionGreaterThanEqual(@Param("inicio") LocalDate inicio);

    /**
     * Obtiene ventas hasta una fecha específica
     */
    @Query("SELECT v FROM Venta v WHERE v.fechaEmision <= :fin " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL) " +
           "ORDER BY v.fechaEmision DESC")
    List<Venta> findByFechaEmisionLessThanEqual(@Param("fin") LocalDate fin);

    /**
     * Suma total de ventas por rango de fechas (para resumen de reportes)
     */
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado != 'ANULADO' AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL)")
    BigDecimal sumTotalByFechaEmisionBetween(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Cuenta ventas por rango de fechas
     */
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL)")
    long countByFechaEmisionBetween(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Ventas por estado y rango de fechas
     */
    @Query("SELECT v FROM Venta v WHERE v.estado = :estado AND v.fechaEmision BETWEEN :inicio AND :fin ORDER BY v.fechaEmision DESC")
    List<Venta> findByEstadoAndFechaEmisionBetween(
            @Param("estado") String estado,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    // =====================================================
    //  CONSULTAS CON PAGINACIÓN GENERAL
    // =====================================================

    /**
     * Listado paginado de todas las ventas (para UI de listados)
     */
    @Query("SELECT v FROM Venta v ORDER BY v.fechaEmision DESC, v.id DESC")
    Page<Venta> findAllPaginated(Pageable pageable);

    /**
     * Listado paginado por estado
     */
    @Query("SELECT v FROM Venta v WHERE v.estado = :estado ORDER BY v.fechaEmision DESC")
    Page<Venta> findByEstadoPaginated(@Param("estado") String estado, Pageable pageable);

    /**
     * Búsqueda paginada por cliente
     */
    @Query("SELECT v FROM Venta v WHERE v.clienteNumeroDocumento = :documento ORDER BY v.fechaEmision DESC")
    Page<Venta> findByClienteDocumentoPaginated(@Param("documento") String documento, Pageable pageable);

    /**
     * Búsqueda general paginada por término (serie-numero, cliente, documento)
     */
    @Query("SELECT v FROM Venta v WHERE " +
           "CONCAT(v.serie, '-', CAST(v.numero AS string)) LIKE %:termino% " +
           "OR LOWER(v.clienteDenominacion) LIKE LOWER(CONCAT('%', :termino, '%')) " +
           "OR v.clienteNumeroDocumento LIKE %:termino% " +
           "ORDER BY v.fechaCreacion DESC")
    Page<Venta> buscarPorTermino(@Param("termino") String termino, Pageable pageable);

    // =====================================================
    //  MÉTODOS EXISTENTES (MANTENIDOS)
    // =====================================================

    /**
     * Obtener el último número de serie para correlativos.
     *
     * ADVERTENCIA: Este método puede devolver NULL si no existen ventas para la serie/tipo especificada.
     * Se recomienda usar el sistema de Correlativos (CorrelativoRepository) en lugar de este método,
     * ya que maneja automáticamente el caso de series nuevas sin registros previos.
     *
     * @deprecated Usar CorrelativoRepository.findByCodigoAndSerieWithLock() que maneja series nuevas automáticamente
     */
    @Query("SELECT MAX(v.numero) FROM Venta v WHERE v.serie = :serie AND v.tipoComprobante = :tipo")
    Integer obtenerUltimoNumero(@Param("serie") String serie, @Param("tipo") String tipo);

    /**
     * Ventas de los últimos 7 días (para gráfico lineal del dashboard)
     */
    @Query("SELECT new com.libreria.sistema.model.dto.ReporteDTO(CAST(v.fechaEmision AS string), SUM(v.total)) " +
           "FROM Venta v WHERE v.estado != 'ANULADO' AND v.fechaEmision >= :fechaInicio " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL) " +
           "GROUP BY v.fechaEmision ORDER BY v.fechaEmision ASC")
    List<com.libreria.sistema.model.dto.ReporteDTO> obtenerVentasUltimaSemana(@Param("fechaInicio") LocalDate fechaInicio);

    /**
     * Deudas pendientes por DNI del cliente
     */
    @Query("SELECT v FROM Venta v WHERE v.clienteNumeroDocumento = :dni AND v.saldoPendiente > 0 AND v.estado != 'ANULADO'")
    List<Venta> findDeudasPorDni(@Param("dni") String dni);

    /**
     * U-5: Buscar deudas por documento O nombre del cliente (búsqueda flexible)
     */
    @Query("SELECT v FROM Venta v WHERE (v.clienteNumeroDocumento LIKE %:termino% OR LOWER(v.clienteDenominacion) LIKE LOWER(CONCAT('%', :termino, '%'))) AND v.saldoPendiente > 0 AND v.estado != 'ANULADO'")
    List<Venta> findDeudasPorTermino(@Param("termino") String termino);

    /**
     * Buscar ventas para devolución por serie-número, cliente o documento
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Venta v SET v.estado = :estado WHERE v.id = :id")
    void actualizarEstado(@Param("id") Long id, @Param("estado") String estado);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Venta v SET v.saldoPendiente = :saldo WHERE v.id = :id")
    void actualizarSaldoPendiente(@Param("id") Long id, @Param("saldo") BigDecimal saldo);

    @Query("SELECT v FROM Venta v LEFT JOIN FETCH v.clienteEntity c " +
           "WHERE (CONCAT(v.serie, '-', CAST(v.numero AS string)) LIKE %:termino% " +
           "OR LOWER(v.clienteDenominacion) LIKE LOWER(CONCAT('%', :termino, '%')) " +
           "OR v.clienteNumeroDocumento LIKE %:termino%) " +
           "AND v.estado != 'ANULADO' " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL) " +
           "ORDER BY v.fechaEmision DESC")
    List<Venta> buscarParaDevolucion(@Param("termino") String termino);

    /**
     * Obtener venta con sus detalles (JOIN FETCH para evitar LazyInitializationException)
     */
    @Query("SELECT v FROM Venta v " +
           "LEFT JOIN FETCH v.items d " +
           "LEFT JOIN FETCH d.producto p " +
           "LEFT JOIN FETCH v.clienteEntity c " +
           "WHERE v.id = :id")
    Optional<Venta> findByIdWithDetalles(@Param("id") Long id);

    // =====================================================
    //  CONSULTAS ADICIONALES PARA REPORTES FINANCIEROS
    // =====================================================

    /**
     * Resumen de ventas por método de pago en un periodo
     */
    @Query("SELECT v.metodoPago, COUNT(v), SUM(v.total) FROM Venta v " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin AND v.estado != 'ANULADO' " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL) " +
           "GROUP BY v.metodoPago")
    List<Object[]> resumenPorMetodoPago(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Ventas a crédito pendientes de cobro
     */
    @Query("SELECT v FROM Venta v WHERE v.formaPago = 'CREDITO' AND v.saldoPendiente > 0 AND v.estado != 'ANULADO' " +
           "ORDER BY v.fechaVencimiento ASC")
    List<Venta> findVentasCreditoPendientes();

    // FIX ERROR-11: reemplaza el findAll().stream().filter() en CajaService.contarCreditosPendientesHoy()
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.fechaEmision = :fecha " +
           "AND v.formaPago = 'CREDITO' AND v.saldoPendiente > 0 AND v.estado != 'ANULADO'")
    long countCreditosPendientesPorFecha(@Param("fecha") LocalDate fecha);

    @Query("SELECT COALESCE(SUM(v.saldoPendiente), 0) FROM Venta v WHERE v.fechaEmision = :fecha " +
           "AND v.formaPago = 'CREDITO' AND v.saldoPendiente > 0 AND v.estado != 'ANULADO'")
    BigDecimal sumSaldoPendienteCreditosPorFecha(@Param("fecha") LocalDate fecha);

    /**
     * Ventas a crédito vencidas
     */
    @Query("SELECT v FROM Venta v WHERE v.formaPago = 'CREDITO' AND v.saldoPendiente > 0 " +
           "AND v.fechaVencimiento < :fechaActual AND v.estado != 'ANULADO' " +
           "ORDER BY v.fechaVencimiento ASC")
    List<Venta> findVentasCreditoVencidas(@Param("fechaActual") LocalDate fechaActual);

    // =====================================================
    //  CONSULTAS POR CLIENTE
    // =====================================================

    /**
     * Historial de compras de un cliente específico
     */
    @Query("SELECT v FROM Venta v WHERE v.clienteEntity.id = :clienteId ORDER BY v.fechaEmision DESC")
    List<Venta> findByClienteEntityIdOrderByFechaEmisionDesc(@Param("clienteId") Long clienteId);

    /**
     * Historial de compras de un cliente con paginación
     */
    @Query("SELECT v FROM Venta v WHERE v.clienteEntity.id = :clienteId ORDER BY v.fechaEmision DESC")
    Page<Venta> findByClienteEntityIdPaginated(@Param("clienteId") Long clienteId, Pageable pageable);

    /**
     * Total de compras de un cliente
     */
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.clienteEntity.id = :clienteId " +
           "AND v.estado != 'ANULADO' AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL)")
    BigDecimal sumTotalByClienteId(@Param("clienteId") Long clienteId);

    /**
     * Cantidad de compras de un cliente
     */
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.clienteEntity.id = :clienteId " +
           "AND v.estado != 'ANULADO' AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL)")
    long countByClienteId(@Param("clienteId") Long clienteId);

    /**
     * Deudas pendientes de un cliente específico
     */
    @Query("SELECT v FROM Venta v WHERE v.clienteEntity.id = :clienteId AND v.saldoPendiente > 0 AND v.estado != 'ANULADO' ORDER BY v.fechaVencimiento ASC")
    List<Venta> findDeudasPorClienteId(@Param("clienteId") Long clienteId);

    // =====================================================
    //  CONSULTAS OPTIMIZADAS PARA DASHBOARD (FASE 1+2)
    // =====================================================

    /**
     * Suma ventas con estados validos KPI en un periodo.
     * Excluye ANULADO y DEVUELTO_TOTAL.
     */
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL')")
    BigDecimal sumVentasValidasByPeriodo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Cuenta ventas con estados validos KPI en un periodo
     */
    @Query("SELECT COUNT(v) FROM Venta v " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL')")
    long countVentasValidasByPeriodo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Suma ventas válidas usando fecha/hora real de registro.
     * Se usa para el dashboard intradía sin alterar los reportes contables por fecha.
     */
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v " +
           "WHERE v.fechaCreacion BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL')")
    BigDecimal sumVentasValidasByFechaCreacionPeriodo(@Param("inicio") LocalDateTime inicio,
                                                      @Param("fin") LocalDateTime fin);

    /**
     * Cuenta ventas válidas usando fecha/hora real de registro.
     */
    @Query("SELECT COUNT(v) FROM Venta v " +
           "WHERE v.fechaCreacion BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL')")
    long countVentasValidasByFechaCreacionPeriodo(@Param("inicio") LocalDateTime inicio,
                                                  @Param("fin") LocalDateTime fin);

    /**
     * Obtiene la primera venta válida registrada dentro del rango horario.
     */
    @Query("SELECT MIN(v.fechaCreacion) FROM Venta v " +
           "WHERE v.fechaCreacion BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL')")
    LocalDateTime findPrimeraVentaValidaByFechaCreacionPeriodo(@Param("inicio") LocalDateTime inicio,
                                                               @Param("fin") LocalDateTime fin);

    /**
     * Total creditos pendientes (saldo > 0, no anulados)
     */
    @Query("SELECT COALESCE(SUM(v.saldoPendiente), 0) FROM Venta v " +
           "WHERE v.saldoPendiente > 0 AND v.estado != 'ANULADO'")
    BigDecimal sumCreditosPendientes();

    @Query("SELECT COALESCE(SUM(v.saldoPendiente), 0) FROM Venta v " +
           "WHERE v.clienteEntity.id = :clienteId AND v.formaPago = 'CREDITO' " +
           "AND v.saldoPendiente > 0 AND v.estado != 'ANULADO' " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL)")
    BigDecimal sumSaldoPendienteCreditoRealByClienteId(@Param("clienteId") Long clienteId);

    /**
     * Conteo de ventas por forma de pago (CONTADO vs CREDITO)
     */
    @Query("SELECT v.formaPago, COUNT(v) FROM Venta v " +
           "WHERE v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "GROUP BY v.formaPago")
    List<Object[]> countByFormaPago();

    /**
     * Ventas agrupadas por mes (ultimos N meses) para grafico de lineas
     */
    @Query("SELECT FUNCTION('TO_CHAR', v.fechaEmision, 'YYYY-MM'), COALESCE(SUM(v.total), 0) " +
           "FROM Venta v " +
           "WHERE v.fechaEmision >= :inicio " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "GROUP BY FUNCTION('TO_CHAR', v.fechaEmision, 'YYYY-MM') " +
           "ORDER BY FUNCTION('TO_CHAR', v.fechaEmision, 'YYYY-MM')")
    List<Object[]> ventasMensualesAgrupadas(@Param("inicio") LocalDate inicio);

    /**
     * Top productos vendidos en un periodo (para dashboard del mes)
     */
    @Query("SELECT new com.libreria.sistema.model.dto.ReporteDTO(p.nombre, SUM(d.cantidad)) " +
           "FROM DetalleVenta d JOIN d.producto p JOIN d.venta v " +
           "WHERE v.fechaEmision >= :inicio " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "GROUP BY p.nombre ORDER BY SUM(d.cantidad) DESC")
    List<com.libreria.sistema.model.dto.ReporteDTO> obtenerTopProductosMes(
           @Param("inicio") LocalDate inicio, Pageable pageable);

    /**
     * Ventas agrupadas por canal de venta en un periodo
     */
    @Query("SELECT COALESCE(v.canalVenta, 'LOCAL'), COUNT(v), COALESCE(SUM(v.total), 0) " +
           "FROM Venta v " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado IN ('EMITIDO', 'PAGADO_TOTAL', 'DEVUELTO_PARCIAL') " +
           "GROUP BY COALESCE(v.canalVenta, 'LOCAL')")
    List<Object[]> ventasPorCanal(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    // =====================================================
    //  CONSULTAS PARA CONTADOR DE COMPROBANTES SUNAT
    // =====================================================

    /**
     * Cuenta comprobantes electrónicos (BOLETA y FACTURA) en un periodo.
     * Excluye NOTA_VENTA y comprobantes anulados.
     */
    @Query("SELECT COUNT(v) FROM Venta v " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.tipoComprobante IN ('BOLETA', 'FACTURA') " +
           "AND v.estado != 'ANULADO' " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL)")
    long countComprobantesElectronicosByPeriodo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    
    @Query("SELECT v.tipoComprobante, COUNT(v) FROM Venta v " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado != 'ANULADO' " +
           "AND (v.entregaPendiente = false OR v.entregaPendiente IS NULL) " +
           "AND v.sunatEstado IS NOT NULL " +
           "GROUP BY v.tipoComprobante")
    List<Object[]> countByTipoComprobanteAndPeriodo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);
}
