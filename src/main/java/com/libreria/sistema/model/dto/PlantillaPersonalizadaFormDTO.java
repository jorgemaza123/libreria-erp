package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlantillaPersonalizadaFormDTO {
    private Long id;
    private String codigoModelo;
    private String nombreComercial;
    private String slug;
    private String categoria;
    private String coleccionOcasion;
    private String descripcionComercial;
    private Boolean activo;
    private Boolean visibleWeb;
    private Boolean vendibleDirecto;
    private Boolean permitePersonalizacion;
    private BigDecimal margenMinimoPct;
    private BigDecimal margenObjetivoPct;
    private String observacionesInternas;
    private String componentesJson;
    private String rangosJson;
    private String fotoActual;
}
