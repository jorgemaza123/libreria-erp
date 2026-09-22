package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductoRevisionUpdateDTO {
    private String codigoInterno;
    private String codigoBarra;
    private String nombre;
    private String categoria;
    private String clasificacion;
    private String tipo;
    private String tags;
    private BigDecimal precioCompra;
    private BigDecimal precioVenta;
    private Integer stockActual;
    private Integer stockMinimo;
    private Boolean activo;
    private Boolean posRapido;
    private Boolean temporadaActiva;
}
