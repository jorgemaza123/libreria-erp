package com.libreria.sistema.repository;

import com.libreria.sistema.model.OrdenServicio;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("SELECT DISTINCT o.tipoServicio FROM OrdenServicio o WHERE o.tipoServicio IS NOT NULL")
    List<String> findTiposServicio();
}
