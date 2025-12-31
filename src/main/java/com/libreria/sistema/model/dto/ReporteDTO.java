package com.libreria.sistema.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReporteDTO {
    private String etiqueta; // Ej: "2025-01-01" o "Lapicero"
    private BigDecimal valor; // Ej: 500.00 o 50

    // Constructor auxiliar por si la consulta devuelve Long (cantidad) en vez de BigDecimal
    public ReporteDTO(String etiqueta, Long valor) {
        this.etiqueta = etiqueta;
        this.valor = BigDecimal.valueOf(valor);
    }
}