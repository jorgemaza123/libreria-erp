package com.libreria.sistema.repository;

import com.libreria.sistema.model.OrdenServicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrdenServicioRepository extends JpaRepository<OrdenServicio, Long> {
    @Query("SELECT o FROM OrdenServicio o WHERE o.clienteDocumento = :documento ORDER BY o.fechaRecepcion DESC")
    List<OrdenServicio> findByClienteDocumentoOrderByFechaRecepcionDesc(@Param("documento") String documento);

    List<OrdenServicio> findByTipoServicio(String tipo);
    List<OrdenServicio> findByEstadoNot(String estado);
    Optional<OrdenServicio> findByCotizacionId(Long cotizacionId);
    List<OrdenServicio> findByCotizacionIdIn(List<Long> cotizacionIds);

    @Query("SELECT DISTINCT o.tipoServicio FROM OrdenServicio o WHERE o.tipoServicio IS NOT NULL AND TRIM(o.tipoServicio) <> '' ORDER BY o.tipoServicio")
    List<String> findTiposServicio();

    @Override
    @EntityGraph(attributePaths = {"items", "pagos", "pagos.movimientoCaja", "pagos.usuario"})
    List<OrdenServicio> findAll();

    @EntityGraph(attributePaths = {"items", "pagos", "pagos.movimientoCaja", "pagos.usuario"})
    @Query("""
            SELECT o FROM OrdenServicio o
            ORDER BY
                CASE WHEN o.estado = 'PENDIENTE' OR o.estado = 'EN_PROCESO' OR o.estado = 'LISTO' THEN 0 ELSE 1 END,
                CASE WHEN (o.estado = 'PENDIENTE' OR o.estado = 'EN_PROCESO' OR o.estado = 'LISTO')
                    AND o.recordatorioActivo = true
                    AND o.fechaRecordatorio IS NOT NULL
                    AND o.fechaRecordatorio <= CURRENT_DATE THEN 0 ELSE 1 END,
                CASE o.prioridad
                    WHEN 'URGENTE' THEN 0
                    WHEN 'ALTA' THEN 1
                    WHEN 'NORMAL' THEN 2
                    WHEN 'BAJA' THEN 3
                    ELSE 4
                END,
                CASE WHEN o.fechaEntregaEstimada IS NULL THEN 1 ELSE 0 END,
                o.fechaEntregaEstimada ASC,
                o.fechaRecepcion DESC
            """)
    List<OrdenServicio> findAllOrdenadasParaTablero();

    @EntityGraph(attributePaths = {"items", "pagos", "pagos.movimientoCaja", "pagos.usuario"})
    @Query("""
            SELECT o FROM OrdenServicio o
            ORDER BY
                CASE WHEN o.fechaRecepcion IS NULL THEN 1 ELSE 0 END,
                o.fechaRecepcion DESC,
                o.id DESC
            """)
    List<OrdenServicio> findAllOrdenadasPorMasReciente();

    @EntityGraph(attributePaths = {"items", "pagos", "pagos.movimientoCaja", "pagos.usuario"})
    @Query("SELECT o FROM OrdenServicio o WHERE o.id = :id")
    Optional<OrdenServicio> findDetalleCompletoById(@Param("id") Long id);
}
