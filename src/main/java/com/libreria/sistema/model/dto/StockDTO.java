package com.libreria.sistema.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDTO {
    private Long id;
    private String codigoInterno;
    private String codigoBarra;
    private String nombre;
    private String categoria;
    private Integer stockActual;
    private Integer stockMinimo;
    private Integer stockMaximo;
    private Boolean temporadaActiva;
    private Integer stockObjetivoTemporada;
    private Boolean posRapido;
    private Integer posRapidoOrden;
    private String estado;       // SIN_STOCK, CRITICO, BAJO, OK
    private String badgeClass;   // badge-dark, badge-danger, badge-warning, badge-success
    private Boolean enLiquidacion;
    private BigDecimal precioOriginal;
    private BigDecimal precioCompra;
    private BigDecimal valorStock; // stockActual * precioCompra
    private LocalDateTime fechaActualizacion;
}
