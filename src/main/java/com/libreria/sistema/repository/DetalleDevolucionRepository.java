package com.libreria.sistema.repository;

import com.libreria.sistema.model.DetalleDevolucion;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetalleDevolucionRepository extends JpaRepository<DetalleDevolucion, Long> {

    @Query("SELECT d FROM DetalleDevolucion d " +
           "JOIN FETCH d.devolucion dev " +
           "LEFT JOIN FETCH d.producto p " +
           "WHERE dev.ventaOriginal.id IN :ventaIds AND dev.estado != 'ANULADA'")
    List<DetalleDevolucion> findActivosByVentaOriginalIds(@Param("ventaIds") List<Long> ventaIds);
}
