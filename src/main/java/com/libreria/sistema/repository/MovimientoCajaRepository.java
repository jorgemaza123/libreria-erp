package com.libreria.sistema.repository;

import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.model.SesionCaja;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    // Listar movimientos desde una fecha (para ver los de hoy)
    List<MovimientoCaja> findByFechaAfterOrderByFechaDesc(LocalDateTime fecha);

    // CONSULTA 1: Sumar todos los INGRESOS desde una fecha
    // COALESCE sirve para que si no hay nada, devuelva 0 en vez de null
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.tipo = 'INGRESO' AND m.fecha >= :fecha")
    BigDecimal sumarIngresos(@Param("fecha") LocalDateTime fecha);

    // CONSULTA 2: Sumar todos los EGRESOS desde una fecha
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.tipo = 'EGRESO' AND m.fecha >= :fecha")
    BigDecimal sumarEgresos(@Param("fecha") LocalDateTime fecha);

    List<MovimientoCaja> findBySesionOrderByFechaDesc(SesionCaja sesion);
}