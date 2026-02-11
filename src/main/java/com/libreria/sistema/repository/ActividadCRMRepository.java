package com.libreria.sistema.repository;

import com.libreria.sistema.model.ActividadCRM;
import com.libreria.sistema.model.Cliente;
import com.libreria.sistema.model.OportunidadCRM;
import com.libreria.sistema.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActividadCRMRepository extends JpaRepository<ActividadCRM, Long> {

    List<ActividadCRM> findByClienteOrderByFechaCreacionDesc(Cliente cliente);

    List<ActividadCRM> findByOportunidadOrderByFechaCreacionDesc(OportunidadCRM oportunidad);

    @Query("SELECT a FROM ActividadCRM a LEFT JOIN FETCH a.cliente LEFT JOIN FETCH a.usuario " +
           "WHERE (a.asignado = :usuario OR a.usuario = :usuario) AND a.estado = 'PENDIENTE' " +
           "ORDER BY a.fechaProgramada ASC NULLS LAST")
    List<ActividadCRM> findTareasPendientesDeUsuario(@Param("usuario") Usuario usuario);

    @Query("SELECT a FROM ActividadCRM a LEFT JOIN FETCH a.cliente LEFT JOIN FETCH a.usuario " +
           "WHERE a.estado = 'PENDIENTE' ORDER BY a.fechaProgramada ASC NULLS LAST")
    List<ActividadCRM> findTodasPendientes();

    @Query("SELECT a FROM ActividadCRM a WHERE a.estado = 'PENDIENTE' " +
           "AND a.fechaProgramada IS NOT NULL AND a.fechaProgramada < :fecha")
    List<ActividadCRM> findVencidas(@Param("fecha") LocalDateTime fecha);

    @Query("SELECT a FROM ActividadCRM a WHERE a.estado = 'PENDIENTE' " +
           "AND a.fechaProgramada BETWEEN :inicio AND :fin")
    List<ActividadCRM> findProgramadasEntre(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("SELECT a FROM ActividadCRM a LEFT JOIN FETCH a.cliente LEFT JOIN FETCH a.usuario " +
           "ORDER BY a.fechaCreacion DESC")
    Page<ActividadCRM> findAllConRelaciones(Pageable pageable);

    // Timeline: actividades de un cliente
    @Query("SELECT a FROM ActividadCRM a LEFT JOIN FETCH a.usuario " +
           "WHERE a.cliente = :cliente ORDER BY a.fechaCreacion DESC")
    List<ActividadCRM> findTimelineCliente(@Param("cliente") Cliente cliente);

    // Contadores
    @Query("SELECT COUNT(a) FROM ActividadCRM a WHERE a.estado = 'PENDIENTE' " +
           "AND (a.asignado = :usuario OR a.usuario = :usuario)")
    long countPendientesDeUsuario(@Param("usuario") Usuario usuario);

    @Query("SELECT COUNT(a) FROM ActividadCRM a WHERE a.estado = 'PENDIENTE'")
    long countPendientes();

    @Query("SELECT a.tipo, COUNT(a) FROM ActividadCRM a WHERE a.fechaCreacion >= :desde GROUP BY a.tipo")
    List<Object[]> contarPorTipoDesde(@Param("desde") LocalDateTime desde);

    @Query("SELECT COUNT(a) FROM ActividadCRM a WHERE a.estado = 'PENDIENTE' " +
           "AND a.fechaProgramada IS NOT NULL AND a.fechaProgramada < :fecha")
    long countVencidas(@Param("fecha") LocalDateTime fecha);
}
