package com.libreria.sistema.repository;

import com.libreria.sistema.model.ReposicionPendiente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ReposicionPendienteRepository extends JpaRepository<ReposicionPendiente, Long> {

    @EntityGraph(attributePaths = {"producto"})
    Optional<ReposicionPendiente> findByProductoId(Long productoId);

    @EntityGraph(attributePaths = {"producto"})
    List<ReposicionPendiente> findByEstadoOrderByPrioridadAscUltimaVentaDesc(String estado);

    @EntityGraph(attributePaths = {"producto"})
    List<ReposicionPendiente> findAllByOrderByEstadoAscPrioridadAscUltimaVentaDesc();

    @Query("SELECT COALESCE(SUM(r.cantidadPendiente), 0), " +
           "COALESCE(SUM(r.montoReposicionPendiente), 0), " +
           "COALESCE(SUM(r.montoVentaAcumulado), 0), " +
           "COALESCE(SUM(r.utilidadBrutaAcumulada), 0), " +
           "COALESCE(SUM(r.costoIndirectoAcumulado), 0), " +
           "COALESCE(SUM(r.utilidadNetaEstimadaAcumulada), 0) " +
           "FROM ReposicionPendiente r WHERE r.estado = :estado")
    Object[] resumenPorEstado(@Param("estado") String estado);

    @Query("SELECT COALESCE(SUM(r.montoReposicionPendiente), 0) FROM ReposicionPendiente r WHERE r.estado = 'PENDIENTE'")
    BigDecimal totalMontoPendiente();
}
