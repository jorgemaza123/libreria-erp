package com.libreria.sistema.repository;

import com.libreria.sistema.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByNumeroDocumento(String numeroDocumento);

    // =====================================================
    //  BÚSQUEDAS BÁSICAS
    // =====================================================

    /**
     * Clientes activos
     */
    List<Cliente> findByActivoTrue();

    /**
     * Clientes activos paginados
     */
    Page<Cliente> findByActivoTrueOrderByNombreRazonSocialAsc(Pageable pageable);

    /**
     * Búsqueda inteligente por nombre, documento o teléfono
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND " +
           "(LOWER(c.nombreRazonSocial) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "c.numeroDocumento LIKE %:termino% OR " +
           "c.telefono LIKE %:termino% OR " +
           "c.email LIKE %:termino%)")
    List<Cliente> buscarInteligente(@Param("termino") String termino);

    /**
     * Búsqueda inteligente paginada
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND " +
           "(LOWER(c.nombreRazonSocial) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "c.numeroDocumento LIKE %:termino% OR " +
           "c.telefono LIKE %:termino% OR " +
           "c.email LIKE %:termino%) " +
           "ORDER BY c.nombreRazonSocial ASC")
    Page<Cliente> buscarInteligentePaginated(@Param("termino") String termino, Pageable pageable);

    // =====================================================
    //  BÚSQUEDAS DE CRÉDITO
    // =====================================================

    /**
     * Clientes con crédito habilitado
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND c.tieneCredito = true ORDER BY c.nombreRazonSocial")
    List<Cliente> findClientesConCredito();

    /**
     * Clientes con deuda pendiente (morosos)
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND c.saldoDeudor > 0 ORDER BY c.saldoDeudor DESC")
    List<Cliente> findClientesConDeuda();

    /**
     * Clientes con deuda pendiente paginados
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND c.saldoDeudor > 0 ORDER BY c.saldoDeudor DESC")
    Page<Cliente> findClientesConDeudaPaginated(Pageable pageable);

    /**
     * Clientes morosos (deuda mayor al límite)
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND c.tieneCredito = true " +
           "AND c.saldoDeudor >= c.limiteCredito ORDER BY c.saldoDeudor DESC")
    List<Cliente> findClientesMorosos();

    /**
     * Total de deuda de todos los clientes
     */
    @Query("SELECT COALESCE(SUM(c.saldoDeudor), 0) FROM Cliente c WHERE c.activo = true")
    BigDecimal sumarDeudaTotal();

    // =====================================================
    //  ESTADÍSTICAS Y CLASIFICACIÓN
    // =====================================================

    /**
     * Clientes por categoría
     */
    List<Cliente> findByCategoriaAndActivoTrue(String categoria);

    /**
     * Top clientes por total de compras
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true ORDER BY c.totalComprasHistorico DESC")
    List<Cliente> findTopClientes(Pageable pageable);

    /**
     * Clientes frecuentes (más de N compras)
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND c.cantidadCompras >= :minCompras ORDER BY c.cantidadCompras DESC")
    List<Cliente> findClientesFrecuentes(@Param("minCompras") Integer minCompras);

    /**
     * Clientes nuevos (sin compras aún)
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND (c.cantidadCompras IS NULL OR c.cantidadCompras = 0)")
    List<Cliente> findClientesNuevos();

    // =====================================================
    //  CONTADORES
    // =====================================================

    /**
     * Contar clientes activos
     */
    long countByActivoTrue();

    /**
     * Contar clientes con crédito
     */
    @Query("SELECT COUNT(c) FROM Cliente c WHERE c.activo = true AND c.tieneCredito = true")
    long countClientesConCredito();

    /**
     * Contar clientes con deuda
     */
    @Query("SELECT COUNT(c) FROM Cliente c WHERE c.activo = true AND c.saldoDeudor > 0")
    long countClientesConDeuda();

    // =====================================================
    //  PARA SELECT EN FORMULARIOS
    // =====================================================

    /**
     * Lista simplificada para select (solo nombre y documento)
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true ORDER BY c.nombreRazonSocial")
    List<Cliente> findAllParaSelect();

    /**
     * Buscar para autocompletado en ventas
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND " +
           "(LOWER(c.nombreRazonSocial) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "c.numeroDocumento LIKE %:termino%) ORDER BY c.nombreRazonSocial")
    List<Cliente> buscarParaAutocompletado(@Param("termino") String termino);
}