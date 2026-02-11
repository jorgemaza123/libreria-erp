package com.libreria.sistema.repository;

import com.libreria.sistema.model.Amortizacion;
import com.libreria.sistema.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface AmortizacionRepository extends JpaRepository<Amortizacion, Long> {
    List<Amortizacion> findByVenta(Venta venta);

    /**
     * Suma amortizaciones en un periodo
     */
    @Query("SELECT COALESCE(SUM(a.monto), 0) FROM Amortizacion a " +
           "WHERE a.fechaPago BETWEEN :inicio AND :fin")
    BigDecimal sumByPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    /**
     * Cuenta amortizaciones en un periodo
     */
    @Query("SELECT COUNT(a) FROM Amortizacion a " +
           "WHERE a.fechaPago BETWEEN :inicio AND :fin")
    long countByPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}