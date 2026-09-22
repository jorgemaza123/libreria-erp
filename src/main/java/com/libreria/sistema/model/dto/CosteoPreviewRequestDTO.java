package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CosteoPreviewRequestDTO {
    private BigDecimal cantidadComprada;
    private BigDecimal totalPagado;
    private BigDecimal factorIndirectoManualPct;
    private BigDecimal gananciaObjetivoPct;
    private BigDecimal gananciaMinimaPct;
    private BigDecimal precioVentaActual;
    private Long productoId;
}
