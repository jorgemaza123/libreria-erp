package com.libreria.sistema.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO para creación de ventas con validaciones.
 * Soporta múltiples métodos de pago: EFECTIVO, YAPE, PLIN, TARJETA, TRANSFERENCIA
 */
@Data
public class VentaDTO {

    // =====================================================
    //  DATOS DEL CLIENTE
    // =====================================================

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(min = 3, max = 200, message = "El nombre debe tener entre 3 y 200 caracteres")
    private String clienteNombre;

    @NotBlank(message = "El documento del cliente es obligatorio")
    @Pattern(regexp = "^[0-9]{8}$|^[0-9]{11}$", message = "Documento inválido. Debe ser DNI (8 dígitos) o RUC (11 dígitos)")
    private String clienteDocumento;

    @Size(max = 300, message = "La dirección no puede exceder 300 caracteres")
    private String clienteDireccion;

    @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
    private String clienteTelefono;

    // =====================================================
    //  TIPO DE COMPROBANTE
    // =====================================================

    @NotBlank(message = "El tipo de comprobante es obligatorio")
    @Pattern(regexp = "BOLETA|FACTURA|NOTA_VENTA", message = "Tipo de comprobante inválido")
    private String tipoComprobante;

    // =====================================================
    //  DATOS DE PAGO
    // =====================================================

    @NotBlank(message = "La forma de pago es obligatoria")
    @Pattern(regexp = "CONTADO|CREDITO", message = "Forma de pago inválida")
    private String formaPago;

    /**
     * Método de pago utilizado.
     * Valores permitidos: EFECTIVO, YAPE, PLIN, TARJETA, TRANSFERENCIA, MIXTO
     * Por defecto: EFECTIVO
     */
    @Pattern(regexp = "EFECTIVO|YAPE|PLIN|TARJETA|TRANSFERENCIA|MIXTO",
             message = "Método de pago inválido. Valores permitidos: EFECTIVO, YAPE, PLIN, TARJETA, TRANSFERENCIA, MIXTO")
    private String metodoPago = "EFECTIVO";

    @DecimalMin(value = "0.0", inclusive = true, message = "El monto inicial no puede ser negativo")
    private BigDecimal montoInicial;

    @Min(value = 1, message = "Los días de crédito deben ser al menos 1")
    @Max(value = 90, message = "Los días de crédito no pueden exceder 90")
    private Integer diasCredito;

    // =====================================================
    //  ITEMS DE LA VENTA
    // =====================================================

    @NotEmpty(message = "Debe incluir al menos un producto en la venta")
    @Valid
    private List<DetalleDTO> items;

    /**
     * DTO interno para los detalles de venta
     */
    @Data
    public static class DetalleDTO {
        @NotNull(message = "El ID del producto es obligatorio")
        @Min(value = 1, message = "El ID del producto debe ser mayor a 0")
        private Long productoId;

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.01", message = "La cantidad debe ser mayor a 0")
        @DecimalMax(value = "10000", message = "La cantidad no puede exceder 10000")
        private BigDecimal cantidad;

        @NotNull(message = "El precio de venta es obligatorio")
        @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
        @DecimalMax(value = "999999.99", message = "El precio no puede exceder 999999.99")
        private BigDecimal precioVenta;
    }

    // =====================================================
    //  MÉTODOS DE UTILIDAD
    // =====================================================

    /**
     * Obtiene el método de pago, con EFECTIVO como valor por defecto
     */
    public String getMetodoPago() {
        return metodoPago != null && !metodoPago.trim().isEmpty() ? metodoPago : "EFECTIVO";
    }

    /**
     * Verifica si es una venta a crédito
     */
    public boolean esCredito() {
        return "CREDITO".equalsIgnoreCase(formaPago);
    }

    /**
     * Verifica si es una venta al contado
     */
    public boolean esContado() {
        return "CONTADO".equalsIgnoreCase(formaPago);
    }
}
