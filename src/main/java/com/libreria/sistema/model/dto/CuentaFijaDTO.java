package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CuentaFijaDTO {
    private Long id;
    private String nombre;
    private BigDecimal montoMensual;
    private String categoria;
    private String tipoCosto;
    private String reglaReparto;
    private String categoriaObjetivo;
    private BigDecimal baseMensual;
    private BigDecimal porcentajeUsoCosteo;
    private Boolean incluirEnCosteo;
    private String notas;
    private Boolean activa;
    private Integer orden;
}
