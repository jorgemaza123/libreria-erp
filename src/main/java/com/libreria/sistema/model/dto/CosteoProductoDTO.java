package com.libreria.sistema.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CosteoProductoDTO {
    private Long productoId;
    private String codigoInterno;
    private String nombre;
    private String categoria;
    private String tipo;
    private Integer stockActual;
    private String reglaCosteo;
    private BigDecimal unidadesEstimadasMes;
    private BigDecimal minutosTrabajoUnidad;
    private BigDecimal impresionesEquivalentesUnidad;
    private Boolean usarReglaManualPrecio;
    private BigDecimal costoDirectoUnitario;
    private BigDecimal costoIndirectoUnitario;
    private BigDecimal costoTotalUnitario;
    private BigDecimal gananciaMinimaPct;
    private BigDecimal gananciaObjetivoPct;
    private BigDecimal precioMinimo;
    private BigDecimal precioSugerido;
    private BigDecimal precioVentaActual;
    private BigDecimal diferenciaPrecio;
    private BigDecimal margenActualPct;
    private String estadoPrecio;
    private String recomendacion;
    private String reglaResumen;
    private String datosFaltantes;
    private LocalDateTime fechaUltimoCosteo;
}
