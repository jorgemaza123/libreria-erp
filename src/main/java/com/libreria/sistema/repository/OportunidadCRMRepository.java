package com.libreria.sistema.repository;

import com.libreria.sistema.model.Cliente;
import com.libreria.sistema.model.OportunidadCRM;
import com.libreria.sistema.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface OportunidadCRMRepository extends JpaRepository<OportunidadCRM, Long> {

    List<OportunidadCRM> findByEtapaOrderByOrdenAsc(String etapa);

    @Query("SELECT o FROM OportunidadCRM o LEFT JOIN FETCH o.cliente LEFT JOIN FETCH o.vendedor " +
           "WHERE o.etapa NOT IN ('GANADO', 'PERDIDO') ORDER BY o.etapa, o.orden")
    List<OportunidadCRM> findActivasConRelaciones();

    List<OportunidadCRM> findByClienteOrderByFechaCreacionDesc(Cliente cliente);

    List<OportunidadCRM> findByVendedorOrderByFechaCreacionDesc(Usuario vendedor);

    @Query("SELECT o FROM OportunidadCRM o LEFT JOIN FETCH o.cliente LEFT JOIN FETCH o.vendedor " +
           "ORDER BY o.fechaCreacion DESC")
    Page<OportunidadCRM> findAllConRelaciones(Pageable pageable);

    // Metricas
    @Query("SELECT COUNT(o) FROM OportunidadCRM o WHERE o.etapa = :etapa")
    long countByEtapa(@Param("etapa") String etapa);

    @Query("SELECT COALESCE(SUM(o.valorEstimado), 0) FROM OportunidadCRM o WHERE o.etapa NOT IN ('GANADO', 'PERDIDO')")
    BigDecimal sumarValorPipeline();

    @Query("SELECT COALESCE(SUM(o.valorReal), 0) FROM OportunidadCRM o WHERE o.etapa = 'GANADO' " +
           "AND o.fechaCierreReal BETWEEN :inicio AND :fin")
    BigDecimal sumarGanadasEnPeriodo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    @Query("SELECT COUNT(o) FROM OportunidadCRM o WHERE o.etapa = 'GANADO' " +
           "AND o.fechaCierreReal BETWEEN :inicio AND :fin")
    long countGanadasEnPeriodo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    @Query("SELECT COUNT(o) FROM OportunidadCRM o WHERE o.etapa = 'PERDIDO' " +
           "AND o.fechaCierreReal BETWEEN :inicio AND :fin")
    long countPerdidasEnPeriodo(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    @Query("SELECT COUNT(o) FROM OportunidadCRM o WHERE o.etapa NOT IN ('GANADO', 'PERDIDO')")
    long countActivas();

    @Query("SELECT o.etapa, COUNT(o), COALESCE(SUM(o.valorEstimado), 0) FROM OportunidadCRM o " +
           "GROUP BY o.etapa ORDER BY o.etapa")
    List<Object[]> estadisticasPorEtapa();

    @Query("SELECT o FROM OportunidadCRM o WHERE o.fechaCierreEstimada < :fecha " +
           "AND o.etapa NOT IN ('GANADO', 'PERDIDO')")
    List<OportunidadCRM> findVencidas(@Param("fecha") LocalDate fecha);
}
