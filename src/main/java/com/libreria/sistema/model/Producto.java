package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- TUS CAMPOS ORIGINALES ---
    @Column(unique = true) 
    private String codigoBarra;

    @Column(unique = true)
    private String codigoInterno;

    @Column(nullable = false)
    private String nombre;

    private String categoria;
    
    @Column(length = 500)
    private String descripcion;

    @Column(precision = 10, scale = 2)
    private BigDecimal precioCompra;

    @Column(precision = 10, scale = 2)
    private BigDecimal precioVenta;

    @Column(precision = 10, scale = 2)
    private BigDecimal precioMayorista; // Mantenido

    private Integer stockActual;
    private Integer stockMinimo;
    private String unidadMedida;
    
    private String ubicacionFila;
    private String ubicacionColumna;
    private String ubicacionEstante;

    private String tipoAfectacionIgv; // Mantenido
    private boolean activo = true;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    // --- NUEVOS CAMPOS (FASE 2) ---
    private String marca;
    private String modelo;
    private String color;
    private String generacion; 
    private String tipo;       
    private String imagen; // Para la foto

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.stockActual == null) this.stockActual = 0;
        if (this.stockMinimo == null) this.stockMinimo = 5; // Tu valor por defecto
        if (this.tipoAfectacionIgv == null) this.tipoAfectacionIgv = "GRAVADO";
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}