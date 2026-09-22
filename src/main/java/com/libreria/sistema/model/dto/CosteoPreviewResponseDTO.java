package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CosteoPreviewResponseDTO {
    private BigDecimal cantidadComprada = BigDecimal.ZERO;
    private BigDecimal totalPagado = BigDecimal.ZERO;
    private BigDecimal costoBaseUnitario = BigDecimal.ZERO;
    private BigDecimal factorIndirectoSugeridoPct = BigDecimal.ZERO;
    private BigDecimal factorIndirectoAplicadoPct = BigDecimal.ZERO;
    private BigDecimal costoRealUnitario = BigDecimal.ZERO;
    private BigDecimal gananciaObjetivoPct = BigDecimal.ZERO;
    private BigDecimal gananciaMinimaPct = BigDecimal.ZERO;
    private BigDecimal precioMinimo = BigDecimal.ZERO;
    private BigDecimal precioSugerido = BigDecimal.ZERO;
    private BigDecimal precioSugeridoFinal = BigDecimal.ZERO;
    private BigDecimal utilidadPorUnidad = BigDecimal.ZERO;
    private BigDecimal margenActualPct = BigDecimal.ZERO;
    private String estado = "SIN_DATOS";
    private String mensajeEstado = "Completa cantidad y total pagado.";
}
