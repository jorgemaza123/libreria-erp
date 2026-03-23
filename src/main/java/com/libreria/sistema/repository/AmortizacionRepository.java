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

    List<Amortizacion> findByVentaOrderByFechaPagoDesc(Venta venta);

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

    @Query("SELECT a FROM Amortizacion a " +
           "JOIN FETCH a.venta v " +
           "WHERE a.fechaPago BETWEEN :inicio AND :fin " +
           "ORDER BY a.fechaPago DESC, a.id DESC")
    List<Amortizacion> findByFechaPagoBetweenWithVenta(@Param("inicio") LocalDateTime inicio,
                                                       @Param("fin") LocalDateTime fin);

    @Query("SELECT a FROM Amortizacion a " +
           "JOIN FETCH a.venta v " +
           "WHERE a.fechaPago BETWEEN :inicio AND :fin " +
           "AND v.clienteNumeroDocumento = :documento " +
           "ORDER BY a.fechaPago DESC, a.id DESC")
    List<Amortizacion> findByFechaPagoBetweenAndClienteDocumentoWithVenta(@Param("inicio") LocalDateTime inicio,
                                                                          @Param("fin") LocalDateTime fin,
                                                                          @Param("documento") String documento);

    @Query("SELECT a FROM Amortizacion a " +
           "JOIN FETCH a.venta v " +
           "WHERE a.fechaPago BETWEEN :inicio AND :fin " +
           "AND (v.clienteNumeroDocumento LIKE %:termino% " +
           "OR LOWER(v.clienteDenominacion) LIKE LOWER(CONCAT('%', :termino, '%'))) " +
           "ORDER BY a.fechaPago DESC, a.id DESC")
    List<Amortizacion> findByFechaPagoBetweenAndClienteTerminoWithVenta(@Param("inicio") LocalDateTime inicio,
                                                                        @Param("fin") LocalDateTime fin,
                                                                        @Param("termino") String termino);
}
