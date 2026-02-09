package com.libreria.sistema.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO para un item seleccionado para venta en una Lista Escolar.
 */
@Data
public class ItemSeleccionadoDTO {

    @NotNull(message = "El ID del detalle es obligatorio")
    private Long detalleListaId;

    @NotNull(message = "El ID del producto es obligatorio")
    private Long productoId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad mínima es 1")
    @Max(value = 10000, message = "La cantidad máxima es 10000")
    private Integer cantidad;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio mínimo es 0.01")
    @DecimalMax(value = "999999.99", message = "El precio máximo es 999999.99")
    private BigDecimal precioUnitario;

    // Nivel seleccionado (ECONOMICO, MEDIO, PREMIUM)
    private String nivelSeleccionado;

    /**
     * Calcula el subtotal del item.
     */
    public BigDecimal getSubtotal() {
        if (precioUnitario == null || cantidad == null) {
            return BigDecimal.ZERO;
        }
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }
}
