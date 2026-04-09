package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LaminaCargaMasivaDTO {

    private String laminaCategoria;
    private String laminaMarca;
    private String laminaContenedor;
    private Integer stockActual;
    private BigDecimal precioVenta;
    private String lineas;
}
