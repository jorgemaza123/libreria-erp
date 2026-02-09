package com.libreria.sistema.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO para procesar venta parcial o total de una Lista Escolar.
 */
@Data
public class VentaListaEscolarDTO {

    @NotNull(message = "El ID de la lista es obligatorio")
    private Long listaEscolarId;

    // --- TIPO DE COMPROBANTE ---
    @NotBlank(message = "El tipo de comprobante es obligatorio")
    @Pattern(regexp = "BOLETA|FACTURA|NOTA_VENTA", message = "Tipo de comprobante inválido")
    private String tipoComprobante = "BOLETA";

    // --- DATOS CLIENTE ---
    @Size(max = 15)
    private String clienteDocumento;

    @Size(min = 3, max = 200)
    private String clienteNombre;

    @Size(max = 300)
    private String clienteDireccion;

    @Size(max = 20)
    private String clienteTelefono;

    // --- FORMA DE PAGO ---
    @NotBlank(message = "La forma de pago es obligatoria")
    @Pattern(regexp = "CONTADO|CREDITO", message = "Forma de pago inválida")
    private String formaPago = "CONTADO";

    @Pattern(regexp = "EFECTIVO|YAPE|PLIN|TARJETA|TRANSFERENCIA|MIXTO",
             message = "Método de pago inválido")
    private String metodoPago = "EFECTIVO";

    // Para crédito
    private BigDecimal montoInicial;

    @Min(value = 1, message = "Los días de crédito deben ser al menos 1")
    @Max(value = 90, message = "Los días de crédito no pueden exceder 90")
    private Integer diasCredito;

    // --- ITEMS A VENDER ---
    @NotEmpty(message = "Debe seleccionar al menos un item para vender")
    @Valid
    private List<ItemSeleccionadoDTO> itemsSeleccionados = new ArrayList<>();

    // --- OBSERVACIONES ---
    @Size(max = 500)
    private String observaciones;

    /**
     * Calcula el total de la venta sumando todos los items seleccionados.
     */
    public BigDecimal calcularTotal() {
        return itemsSeleccionados.stream()
            .map(ItemSeleccionadoDTO::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
