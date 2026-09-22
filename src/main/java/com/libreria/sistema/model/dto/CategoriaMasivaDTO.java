package com.libreria.sistema.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class CategoriaMasivaDTO {
    private String categoria;
    private List<Long> productoIds;
}
