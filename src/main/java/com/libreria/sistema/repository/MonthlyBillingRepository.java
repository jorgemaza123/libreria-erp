package com.libreria.sistema.repository;

import com.libreria.sistema.model.MonthlyBilling;
import com.libreria.sistema.model.MonthlyBilling.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyBillingRepository extends JpaRepository<MonthlyBilling, Long> {

    /**
     * Busca el registro de un mes especifico
     */
    Optional<MonthlyBilling> findByMesAnio(String mesAnio);

    /**
     * Verifica si existe registro para un mes
     */
    boolean existsByMesAnio(String mesAnio);

    /**
     * Obtiene todos los meses con deuda pendiente
     */
    @Query("SELECT m FROM MonthlyBilling m WHERE m.estadoPago = 'PENDIENTE' AND m.montoCalculado > 0 ORDER BY m.mesAnio DESC")
    List<MonthlyBilling> findMesesConDeudaPendiente();

    /**
     * Obtiene meses por estado de pago
     */
    List<MonthlyBilling> findByEstadoPagoOrderByMesAnioDesc(EstadoPago estadoPago);

    /**
     * Obtiene el historial ordenado por mes (mas reciente primero)
     */
    List<MonthlyBilling> findAllByOrderByMesAnioDesc();

    /**
     * Cuenta cuantos meses tienen deuda pendiente
     */
    @Query("SELECT COUNT(m) FROM MonthlyBilling m WHERE m.estadoPago = 'PENDIENTE' AND m.montoCalculado > 0")
    long countMesesConDeuda();

    /**
     * Suma total de deuda pendiente
     */
    @Query("SELECT COALESCE(SUM(m.montoCalculado), 0) FROM MonthlyBilling m WHERE m.estadoPago = 'PENDIENTE' AND m.montoCalculado > 0")
    java.math.BigDecimal sumDeudaTotal();

    /**
     * Obtiene los ultimos N meses de historial
     */
    @Query("SELECT m FROM MonthlyBilling m ORDER BY m.mesAnio DESC")
    List<MonthlyBilling> findUltimosMeses(org.springframework.data.domain.Pageable pageable);
}
