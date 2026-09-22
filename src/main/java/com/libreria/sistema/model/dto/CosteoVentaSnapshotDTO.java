package com.libreria.sistema.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CosteoVentaSnapshotDTO {
    private BigDecimal costoDirectoUnitario;
    private BigDecimal costoIndirectoUnitario;
    private BigDecimal costoTotalUnitario;
    private BigDecimal precioMinimoSnapshot;
    private BigDecimal precioSugeridoSnapshot;
    private BigDecimal montoReposicionTotal;
    private BigDecimal utilidadBrutaUnitaria;
    private BigDecimal utilidadBrutaTotal;
    private BigDecimal utilidadNetaUnitaria;
    private BigDecimal utilidadNetaTotal;
    private BigDecimal margenBrutoPct;
    private BigDecimal margenNetoPct;
    private String reglaResumen;
}
