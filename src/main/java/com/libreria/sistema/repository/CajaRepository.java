package com.libreria.sistema.repository;

import com.libreria.sistema.model.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CajaRepository extends JpaRepository<MovimientoCaja, Long> {
    
    // Para reportes: Buscar por rango de fechas
    List<MovimientoCaja> findByFechaBetween(LocalDateTime inicio, LocalDateTime fin);

    // Para el Dashboard: Sumar ingresos/egresos del día
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m WHERE m.tipo = :tipo AND m.fecha >= :inicio")
    BigDecimal sumarPorTipoDesde(String tipo, LocalDateTime inicio);
}