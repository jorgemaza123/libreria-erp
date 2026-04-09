package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LaminaFormDTO {

    private Long id;
    private String codigoInterno;
    private String codigoBarra;
    private String laminaNumero;
    private String laminaTitulo;
    private String laminaMarca;
    private String laminaCategoria;
    private String laminaProveedorRef;
    private String laminaZona;
    private String laminaContenedor;
    private String laminaPosicion;
    private BigDecimal precioCompra;
    private BigDecimal precioVenta;
    private Integer stockActual;
    private Integer stockMinimo;
    private Boolean activo;
}
