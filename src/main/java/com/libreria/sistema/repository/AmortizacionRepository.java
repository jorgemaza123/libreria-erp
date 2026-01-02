package com.libreria.sistema.repository;

import com.libreria.sistema.model.Amortizacion;
import com.libreria.sistema.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AmortizacionRepository extends JpaRepository<Amortizacion, Long> {
    List<Amortizacion> findByVenta(Venta venta);
}