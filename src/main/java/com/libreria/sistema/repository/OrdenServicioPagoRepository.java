package com.libreria.sistema.repository;

import com.libreria.sistema.model.OrdenServicioPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrdenServicioPagoRepository extends JpaRepository<OrdenServicioPago, Long> {
    List<OrdenServicioPago> findByOrdenServicioIdOrderByFechaAscIdAsc(Long ordenServicioId);

    @Query("SELECT COALESCE(SUM(p.monto), 0) FROM OrdenServicioPago p WHERE p.ordenServicio.id = :ordenId")
    BigDecimal sumByOrdenServicioId(@Param("ordenId") Long ordenId);

    @Query("SELECT COALESCE(SUM(p.monto), 0) FROM OrdenServicioPago p WHERE p.fecha BETWEEN :inicio AND :fin")
    BigDecimal sumByPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}
