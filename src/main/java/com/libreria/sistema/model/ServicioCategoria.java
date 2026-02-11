package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "servicio_categorias")
public class ServicioCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo; // SUBLIMACION, COPIAS, COSTURA, etc.

    @Column(nullable = false)
    private String nombre; // Sublimación, Copias, Costura

    private String descripcion;

    private String icono; // fas fa-tshirt, fas fa-copy, etc.

    private Boolean activa = true;

    private Integer orden = 0; // Para ordenar en dropdown

    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.activa == null) this.activa = true;
        if (this.orden == null) this.orden = 0;
    }
}
