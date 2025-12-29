package com.libreria.sistema.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ReporteDTO {
    private String etiqueta; // Nombre del producto o fecha
    private BigDecimal valor; // Cantidad o Monto
}