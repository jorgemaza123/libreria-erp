package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Catálogo de colegios para autocompletado en listas escolares.
 */
@Data
@Entity
@Table(name = "colegios")
public class Colegio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String nombre;

    @Column(length = 300)
    private String direccion;

    @Column(length = 100)
    private String distrito;

    @Column(length = 50)
    private String tipo; // PUBLICO, PRIVADO, PARROQUIAL

    private boolean activo = true;

    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
    }
}
