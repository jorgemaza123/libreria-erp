package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "roles")
public class Rol {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String nombre; // EJ: ROLE_ADMIN, ROLE_VENDEDOR

    // Constructor para inicialización rápida
    public Rol() {}
    public Rol(String nombre) {
        this.nombre = nombre;
    }
}