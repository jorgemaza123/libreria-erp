package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kardex")
public class Kardex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fecha;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    private String tipo; // "ENTRADA", "SALIDA", "AJUSTE"
    private String motivo; // Ej: "Compra Fact #123", "Venta Ticket #99", "Merma"

    private Integer cantidad;      // Cuánto entró/salió
    private Integer stockAnterior; // Cuánto había antes
    private Integer stockActual;   // Cuánto quedó después

    @PrePersist
    protected void onCreate() {
        this.fecha = LocalDateTime.now();
    }
}