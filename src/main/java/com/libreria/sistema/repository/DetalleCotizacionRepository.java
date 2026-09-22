package com.libreria.sistema.repository;

import com.libreria.sistema.model.DetalleCotizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DetalleCotizacionRepository extends JpaRepository<DetalleCotizacion, Long> {

    @Query("SELECT d.cotizacion.id, d.id, d.tipoItem " +
           "FROM DetalleCotizacion d " +
           "WHERE d.cotizacion.id IN :cotizacionIds " +
           "ORDER BY d.cotizacion.id ASC, d.id ASC")
    List<Object[]> findResumenTiposByCotizacionIds(@Param("cotizacionIds") List<Long> cotizacionIds);
}
