package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrdenServicioPagoDTO {
    private BigDecimal monto;
    private String metodoPago;
    private String concepto;
}
