package com.libreria.sistema.repository;

import com.libreria.sistema.model.AuditoriaLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long> {

    // Búsqueda por usuario
    Page<AuditoriaLog> findByUsuario(String usuario, Pageable pageable);

    // Búsqueda por módulo
    Page<AuditoriaLog> findByModulo(String modulo, Pageable pageable);

    // Búsqueda por acción
    Page<AuditoriaLog> findByAccion(String accion, Pageable pageable);

    // Búsqueda por rango de fechas
    Page<AuditoriaLog> findByFechaHoraBetween(LocalDateTime inicio, LocalDateTime fin, Pageable pageable);

    // Búsqueda por usuario y módulo
    Page<AuditoriaLog> findByUsuarioAndModulo(String usuario, String modulo, Pageable pageable);

    // Búsqueda por entidad y ID (historial de un registro específico)
    List<AuditoriaLog> findByEntidadAndEntidadIdOrderByFechaHoraDesc(String entidad, Long entidadId);

    // Búsqueda avanzada con múltiples filtros
    @Query("SELECT a FROM AuditoriaLog a WHERE " +
           "(:usuario IS NULL OR a.usuario = :usuario) AND " +
           "(:modulo IS NULL OR a.modulo = :modulo) AND " +
           "(:accion IS NULL OR a.accion = :accion) AND " +
           "(:fechaInicio IS NULL OR a.fechaHora >= :fechaInicio) AND " +
           "(:fechaFin IS NULL OR a.fechaHora <= :fechaFin) " +
           "ORDER BY a.fechaHora DESC")
    Page<AuditoriaLog> buscarConFiltros(
        @Param("usuario") String usuario,
        @Param("modulo") String modulo,
        @Param("accion") String accion,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );

    // Obtener últimas auditorías
    List<AuditoriaLog> findTop50ByOrderByFechaHoraDesc();

    // Contar auditorías por usuario
    long countByUsuario(String usuario);

    // Contar auditorías por módulo
    long countByModulo(String modulo);
}
