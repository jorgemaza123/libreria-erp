package com.libreria.sistema.repository;

import com.libreria.sistema.model.ReporteProblema;
import com.libreria.sistema.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReporteProblemaRepository extends JpaRepository<ReporteProblema, Long> {

    /**
     * Buscar reportes por estado
     */
    List<ReporteProblema> findByEstadoOrderByFechaReporteDesc(String estado);

    /**
     * Buscar reportes por estado con paginación
     */
    Page<ReporteProblema> findByEstadoOrderByFechaReporteDesc(String estado, Pageable pageable);

    /**
     * Buscar reportes por usuario que reportó
     */
    List<ReporteProblema> findByUsuarioReportaOrderByFechaReporteDesc(Usuario usuario);

    /**
     * Buscar reportes por tipo de problema
     */
    List<ReporteProblema> findByTipoProblemaOrderByFechaReporteDesc(String tipoProblema);

    /**
     * Buscar reportes por urgencia
     */
    List<ReporteProblema> findByUrgenciaOrderByFechaReporteDesc(String urgencia);

    /**
     * Contar reportes pendientes
     */
    long countByEstado(String estado);

    /**
     * Reportes pendientes ordenados por urgencia y fecha
     */
    @Query("SELECT r FROM ReporteProblema r WHERE r.estado = 'PENDIENTE' " +
           "ORDER BY CASE r.urgencia WHEN 'CRITICA' THEN 1 WHEN 'ALTA' THEN 2 WHEN 'MEDIA' THEN 3 ELSE 4 END, " +
           "r.fechaReporte DESC")
    List<ReporteProblema> findPendientesOrdenadosPorUrgencia();

    /**
     * Reportes por rango de fecha
     */
    @Query("SELECT r FROM ReporteProblema r WHERE r.fechaReporte BETWEEN :inicio AND :fin ORDER BY r.fechaReporte DESC")
    List<ReporteProblema> findByFechaReporteBetween(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    /**
     * Reportes de un producto específico
     */
    List<ReporteProblema> findByProductoIdOrderByFechaReporteDesc(Long productoId);

    /**
     * Listado paginado general ordenado por fecha
     */
    @Query("SELECT r FROM ReporteProblema r LEFT JOIN FETCH r.usuarioReporta LEFT JOIN FETCH r.producto ORDER BY r.fechaReporte DESC")
    Page<ReporteProblema> findAllWithDetails(Pageable pageable);

    /**
     * Estadísticas por tipo de problema
     */
    @Query("SELECT r.tipoProblema, COUNT(r) FROM ReporteProblema r GROUP BY r.tipoProblema")
    List<Object[]> contarPorTipo();

    /**
     * Estadísticas por estado
     */
    @Query("SELECT r.estado, COUNT(r) FROM ReporteProblema r GROUP BY r.estado")
    List<Object[]> contarPorEstado();
}
