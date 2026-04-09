package com.libreria.sistema.model.dto;

import lombok.Data;

@Data
public class InsumoPersonalizadoFormDTO {
    private Long id;
    private String codigo;
    private String nombre;
    private String slugBusqueda;
    private String categoria;
    private String subcategoria;
    private String unidadBase;
    private Boolean controlaStock;
    private Boolean activo;
    private String descripcion;
    private String tags;
    private String presentacionesJson;
    private String fotoActual;
}
