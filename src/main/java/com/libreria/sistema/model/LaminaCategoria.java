package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "lamina_categorias", indexes = {
        @Index(name = "idx_lamina_categoria_nombre", columnList = "nombre"),
        @Index(name = "idx_lamina_categoria_activo", columnList = "activo")
})
public class LaminaCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private Integer orden = 0;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        fechaCreacion = LocalDateTime.now();
        normalizar();
    }

    @PreUpdate
    protected void onUpdate() {
        fechaActualizacion = LocalDateTime.now();
        normalizar();
    }

    private void normalizar() {
        if (nombre != null) {
            nombre = nombre.trim().toUpperCase();
        }
        if (orden == null) {
            orden = 0;
        }
    }
}
