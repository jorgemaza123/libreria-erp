package com.libreria.sistema.service;

import com.libreria.sistema.repository.CompraRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CosteoSugerenciaService {

    private final CompraRepository compraRepository;

    public CosteoSugerenciaService(CompraRepository compraRepository) {
        this.compraRepository = compraRepository;
    }

    public BigDecimal obtenerFactorSugeridoGlobal() {
        BigDecimal promedio = compraRepository.promedioFactorIndirecto();
        return normalizarPct(promedio);
    }

    public BigDecimal obtenerFactorSugeridoProveedor(Long proveedorId) {
        if (proveedorId == null) {
            return obtenerFactorSugeridoGlobal();
        }
        BigDecimal promedio = compraRepository.promedioFactorIndirectoPorProveedor(proveedorId);
        if (promedio == null || promedio.compareTo(BigDecimal.ZERO) <= 0) {
            return obtenerFactorSugeridoGlobal();
        }
        return normalizarPct(promedio);
    }

    private BigDecimal normalizarPct(BigDecimal valor) {
        return valor != null && valor.compareTo(BigDecimal.ZERO) > 0
                ? valor.setScale(3, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    }
}
