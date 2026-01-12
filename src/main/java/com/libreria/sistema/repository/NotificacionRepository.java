package com.libreria.sistema.repository;

import com.libreria.sistema.model.Notificacion;
import com.libreria.sistema.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    /**
     * Notificaciones no leídas para un usuario o globales
     */
    @Query("SELECT n FROM Notificacion n WHERE (n.usuario = :usuario OR n.usuario IS NULL) " +
           "AND n.leida = false ORDER BY n.fechaCreacion DESC")
    List<Notificacion> findNoLeidasParaUsuario(@Param("usuario") Usuario usuario);

    /**
     * Contar notificaciones no leídas
     */
    @Query("SELECT COUNT(n) FROM Notificacion n WHERE (n.usuario = :usuario OR n.usuario IS NULL) " +
           "AND n.leida = false")
    long countNoLeidasParaUsuario(@Param("usuario") Usuario usuario);

    /**
     * Notificaciones no leídas globales (para admins)
     */
    @Query("SELECT n FROM Notificacion n WHERE n.leida = false ORDER BY n.fechaCreacion DESC")
    List<Notificacion> findNoLeidas();

    /**
     * Contar no leídas globales
     */
    long countByLeidaFalse();

    /**
     * Últimas N notificaciones para el dropdown
     */
    @Query("SELECT n FROM Notificacion n WHERE (n.usuario = :usuario OR n.usuario IS NULL) " +
           "ORDER BY n.fechaCreacion DESC")
    List<Notificacion> findUltimasParaUsuario(@Param("usuario") Usuario usuario, Pageable pageable);

    /**
     * Todas las notificaciones paginadas
     */
    @Query("SELECT n FROM Notificacion n ORDER BY n.fechaCreacion DESC")
    Page<Notificacion> findAllOrdenadas(Pageable pageable);

    /**
     * Notificaciones por tipo
     */
    List<Notificacion> findByTipoOrderByFechaCreacionDesc(String tipo);

    /**
     * Notificaciones no resueltas
     */
    @Query("SELECT n FROM Notificacion n WHERE n.resuelta = false ORDER BY " +
           "CASE n.prioridad WHEN 'URGENTE' THEN 1 WHEN 'ALTA' THEN 2 WHEN 'NORMAL' THEN 3 ELSE 4 END, " +
           "n.fechaCreacion DESC")
    List<Notificacion> findNoResueltasOrdenadas();

    /**
     * Buscar por entidad relacionada (para evitar duplicados)
     */
    Optional<Notificacion> findByEntidadRelacionadaAndResueltaFalse(String entidadRelacionada);

    /**
     * Verificar si existe notificación activa para una entidad
     */
    boolean existsByEntidadRelacionadaAndResueltaFalse(String entidadRelacionada);

    /**
     * Marcar como leídas todas las de un usuario
     */
    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true, n.fechaLectura = :fecha " +
           "WHERE (n.usuario = :usuario OR n.usuario IS NULL) AND n.leida = false")
    int marcarTodasComoLeidas(@Param("usuario") Usuario usuario, @Param("fecha") LocalDateTime fecha);

    /**
     * Marcar como resuelta por entidad relacionada
     */
    @Modifying
    @Query("UPDATE Notificacion n SET n.resuelta = true, n.fechaResolucion = :fecha " +
           "WHERE n.entidadRelacionada = :entidad AND n.resuelta = false")
    int marcarResueltaPorEntidad(@Param("entidad") String entidad, @Param("fecha") LocalDateTime fecha);

    /**
     * Eliminar notificaciones antiguas resueltas (limpieza)
     */
    @Modifying
    @Query("DELETE FROM Notificacion n WHERE n.resuelta = true AND n.fechaResolucion < :fecha")
    int eliminarAntiguasResueltas(@Param("fecha") LocalDateTime fecha);

    /**
     * Notificaciones de stock bajo no resueltas
     */
    @Query("SELECT n FROM Notificacion n WHERE n.tipo IN ('STOCK_BAJO', 'STOCK_AGOTADO') " +
           "AND n.resuelta = false ORDER BY n.fechaCreacion DESC")
    List<Notificacion> findNotificacionesStock();

    /**
     * Estadísticas por tipo
     */
    @Query("SELECT n.tipo, COUNT(n) FROM Notificacion n WHERE n.resuelta = false GROUP BY n.tipo")
    List<Object[]> contarPorTipo();
}
