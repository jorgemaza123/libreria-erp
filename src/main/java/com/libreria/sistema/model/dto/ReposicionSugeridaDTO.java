package com.libreria.sistema.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReposicionSugeridaDTO {
    private Long productoId;
    private String codigoInterno;
    private String nombre;
    private String categoria;
    private Integer stockActual;
    private Integer stockMinimo;
    private Integer stockMaximo;
    private Boolean temporadaActiva;
    private Integer stockObjetivoTemporada;
    private BigDecimal vendido7d;
    private BigDecimal vendido30d;
    private BigDecimal promedioDiario30d;
    private BigDecimal diasCobertura;
    private Integer cantidadSugerida;
    private BigDecimal costoUnitario;
    private BigDecimal costoReposicion;
    private String prioridad;
    private String motivo;
    private String badgeClass;
}
