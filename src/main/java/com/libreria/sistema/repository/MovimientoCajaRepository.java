package com.libreria.sistema.repository;

import com.libreria.sistema.model.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    // Listar movimientos del día (desde las 00:00 hasta ahora)
    List<MovimientoCaja> findByFechaAfterOrderByFechaDesc(LocalDateTime inicioDia);

    // Sumar Ingresos
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.tipo = 'INGRESO' AND m.fecha >= :inicio")
    BigDecimal sumarIngresos(LocalDateTime inicio);

    // Sumar Egresos
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.tipo = 'EGRESO' AND m.fecha >= :inicio")
    BigDecimal sumarEgresos(LocalDateTime inicio);
}