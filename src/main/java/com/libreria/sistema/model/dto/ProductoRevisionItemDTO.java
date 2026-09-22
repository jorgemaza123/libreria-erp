package com.libreria.sistema.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoRevisionItemDTO {
    private Long id;
    private String codigoInterno;
    private String codigoBarra;
    private String nombre;
    private String categoria;
    private String clasificacion;
    private String tipo;
    private String tags;
    private BigDecimal precioCompra;
    private BigDecimal precioVenta;
    private BigDecimal margenPct;
    private Integer stockActual;
    private Integer stockMinimo;
    private Boolean activo;
    private Boolean posRapido;
    private Boolean temporadaActiva;
    private Boolean esLamina;
    private String ubicacionResumen;
    private String estadoStock;
    private String badgeStock;
    private String estadoRevision;
    private List<String> alertas;
}
