package com.libreria.sistema.model.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO para items de lista escolar.
 */
@Data
public class DetalleListaEscolarDTO {

    private Long id;

    private String textoOriginal;
    private Integer cantidadSolicitada = 1;

    // Producto asignado
    private Long productoId;
    private String productoNombre;

    // Niveles de precio
    private Long productoEconomicoId;
    private BigDecimal precioEconomico;

    private Long productoMedioId;
    private BigDecimal precioMedio;

    private Long productoPremiumId;
    private BigDecimal precioPremium;

    // Selección
    private String nivelSeleccionado;
    private BigDecimal precioFinal;

    // Estado
    private String estado;

    // Reemplazo
    private Long productoReemplazoId;
    private String motivoReemplazo;

    // Observaciones
    private String observaciones;
}
