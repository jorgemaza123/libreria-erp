package com.libreria.sistema.repository;

import com.libreria.sistema.model.AuditoriaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long>, JpaSpecificationExecutor<AuditoriaLog> {

    // Búsqueda por entidad y ID (historial de un registro específico)
    List<AuditoriaLog> findByEntidadAndEntidadIdOrderByFechaHoraDesc(String entidad, Long entidadId);

    // Obtener últimas auditorías
    List<AuditoriaLog> findTop50ByOrderByFechaHoraDesc();

    // Contar auditorías por usuario
    long countByUsuario(String usuario);

    // Contar auditorías por módulo
    long countByModulo(String modulo);
}