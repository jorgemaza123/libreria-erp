package com.libreria.sistema.repository;

import com.libreria.sistema.model.Venta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    // =====================================================
    //  CONSULTAS OPTIMIZADAS PARA REPORTES (FILTRO EN BD)
    // =====================================================

    /**
     * Obtiene ventas filtradas por rango de fechas - PARA REPORTES
     * Evita traer toda la historia a memoria (N+1 Problem)
     */
    @Query("SELECT v FROM Venta v WHERE v.fechaEmision BETWEEN :inicio AND :fin ORDER BY v.fechaEmision DESC")
    List<Venta> findByFechaEmisionBetween(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Obtiene ventas filtradas por rango de fechas con paginación
     */
    @Query("SELECT v FROM Venta v WHERE v.fechaEmision BETWEEN :inicio AND :fin ORDER BY v.fechaEmision DESC")
    Page<Venta> findByFechaEmisionBetweenPaginated(
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin,
            Pageable pageable);

    /**
     * Obtiene ventas desde una fecha específica (útil para reportes de periodo abierto)
     */
    @Query("SELECT v FROM Venta v WHERE v.fechaEmision >= :inicio ORDER BY v.fechaEmision DESC")
    List<Venta> findByFechaEmisionGreaterThanEqual(@Param("inicio") LocalDate inicio);

    /**
     * Obtiene ventas hasta una fecha específica
     */
    @Query("SELECT v FROM Venta v WHERE v.fechaEmision <= :fin ORDER BY v.fechaEmision DESC")
    List<Venta> findByFechaEmisionLessThanEqual(@Param("fin") LocalDate fin);

    /**
     * Suma total de ventas por rango de fechas (para resumen de reportes)
     */
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.fechaEmision BETWEEN :inicio AND :fin AND v.estado != 'ANULADO'")
    BigDecimal sumTotalByFechaEmisionBetween(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Cuenta ventas por rango de fechas
     */
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.fechaEmision BETWEEN :inicio AND :fin")
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
           "GROUP BY v.fechaEmision ORDER BY v.fechaEmision ASC")
    List<com.libreria.sistema.model.dto.ReporteDTO> obtenerVentasUltimaSemana(@Param("fechaInicio") LocalDate fechaInicio);

    /**
     * Deudas pendientes por DNI del cliente
     */
    @Query("SELECT v FROM Venta v WHERE v.clienteNumeroDocumento = :dni AND v.saldoPendiente > 0 AND v.estado != 'ANULADO'")
    List<Venta> findDeudasPorDni(@Param("dni") String dni);

    /**
     * Buscar ventas para devolución por serie-número, cliente o documento
     */
    @Query("SELECT v FROM Venta v LEFT JOIN FETCH v.clienteEntity c " +
           "WHERE (CONCAT(v.serie, '-', v.numero) LIKE %:termino% " +
           "OR LOWER(v.clienteDenominacion) LIKE LOWER(CONCAT('%', :termino, '%')) " +
           "OR v.clienteNumeroDocumento LIKE %:termino%) " +
           "AND v.estado != 'ANULADO' " +
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
           "GROUP BY v.metodoPago")
    List<Object[]> resumenPorMetodoPago(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Ventas a crédito pendientes de cobro
     */
    @Query("SELECT v FROM Venta v WHERE v.formaPago = 'CREDITO' AND v.saldoPendiente > 0 AND v.estado != 'ANULADO' " +
           "ORDER BY v.fechaVencimiento ASC")
    List<Venta> findVentasCreditoPendientes();

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
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.clienteEntity.id = :clienteId AND v.estado != 'ANULADO'")
    BigDecimal sumTotalByClienteId(@Param("clienteId") Long clienteId);

    /**
     * Cantidad de compras de un cliente
     */
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.clienteEntity.id = :clienteId AND v.estado != 'ANULADO'")
    long countByClienteId(@Param("clienteId") Long clienteId);

    /**
     * Deudas pendientes de un cliente específico
     */
    @Query("SELECT v FROM Venta v WHERE v.clienteEntity.id = :clienteId AND v.saldoPendiente > 0 AND v.estado != 'ANULADO' ORDER BY v.fechaVencimiento ASC")
    List<Venta> findDeudasPorClienteId(@Param("clienteId") Long clienteId);

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
           "AND v.estado != 'ANULADO'")
    long countComprobantesElectronicosByPeriodo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    /**
     * Cuenta comprobantes por tipo en un periodo.
     */
    @Query("SELECT v.tipoComprobante, COUNT(v) FROM Venta v " +
           "WHERE v.fechaEmision BETWEEN :inicio AND :fin " +
           "AND v.estado != 'ANULADO' " +
           "GROUP BY v.tipoComprobante")
    List<Object[]> countByTipoComprobanteAndPeriodo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);
}
