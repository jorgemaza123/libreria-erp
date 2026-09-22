package com.libreria.sistema.model.dto;

import lombok.Data;

@Data
public class ServicioCategoriaDTO {
    private Long id;
    private String nombre;
    private String descripcion;
    private String icono;
    private Boolean activa;
    private Integer orden;
}
