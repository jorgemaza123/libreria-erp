package com.libreria.sistema.repository;
import com.libreria.sistema.model.OrdenServicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrdenServicioRepository extends JpaRepository<OrdenServicio, Long> {
    List<OrdenServicio> findByTipoServicio(String tipo); // Para filtrar por pestañas
    List<OrdenServicio> findByEstadoNot(String estado); // Para ver pendientes

    @Query("SELECT DISTINCT o.tipoServicio FROM OrdenServicio o WHERE o.tipoServicio IS NOT NULL")
    List<String> findTiposServicio();
}