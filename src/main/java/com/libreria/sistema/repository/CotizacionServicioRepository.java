package com.libreria.sistema.repository;

import com.libreria.sistema.model.CotizacionServicio;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CotizacionServicioRepository extends JpaRepository<CotizacionServicio, Long> {

    boolean existsByCodigoPublico(String codigoPublico);

    @Override
    @EntityGraph(attributePaths = {"secciones", "secciones.items"})
    List<CotizacionServicio> findAll();

    @EntityGraph(attributePaths = {"secciones", "secciones.items"})
    @Query("SELECT c FROM CotizacionServicio c WHERE c.id = :id")
    Optional<CotizacionServicio> findDetalleById(@Param("id") Long id);
}
