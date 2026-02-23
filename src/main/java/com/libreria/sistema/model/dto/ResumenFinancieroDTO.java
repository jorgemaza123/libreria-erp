package com.libreria.sistema.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * DTO para resumen financiero de una lista escolar.
 * Calcula ganancia bruta y margen por nivel de cotizacion.
 * No modifica entidades - todo se calcula en memoria.
 */
@Data
public class ResumenFinancieroDTO {

    // --- POR NIVEL ---
    private NivelFinanciero economico = new NivelFinanciero();
    private NivelFinanciero medio = new NivelFinanciero();
    private NivelFinanciero premium = new NivelFinanciero();

    // --- ITEMS SIN COSTO REGISTRADO ---
    private int itemsSinCosto = 0;
    private int itemsTotal = 0;

    @Data
    public static class NivelFinanciero {
        private BigDecimal totalVenta = BigDecimal.ZERO;
        private BigDecimal totalCosto = BigDecimal.ZERO;
        private BigDecimal ganancia = BigDecimal.ZERO;
        private BigDecimal margen = BigDecimal.ZERO;
        private BigDecimal gananciaPromedioPorItem = BigDecimal.ZERO;
        private int itemsCotizados = 0;

        public void calcular() {
            this.ganancia = totalVenta.subtract(totalCosto);
            if (totalVenta.compareTo(BigDecimal.ZERO) > 0) {
                this.margen = ganancia.multiply(BigDecimal.valueOf(100))
                        .divide(totalVenta, 1, RoundingMode.HALF_UP);
            }
            if (itemsCotizados > 0) {
                this.gananciaPromedioPorItem = ganancia
                        .divide(BigDecimal.valueOf(itemsCotizados), 2, RoundingMode.HALF_UP);
            }
        }
    }
}
