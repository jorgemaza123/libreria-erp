package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "solicitudes_producto")
public class SolicitudProducto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombreProducto; // Lo que pidió el cliente
    private int contador; // Cuántas veces lo han pedido
    private LocalDateTime ultimaSolicitud;
    private String estado; // PENDIENTE, COMPRADO

    @PrePersist
    void onCreate() { 
        ultimaSolicitud = LocalDateTime.now(); 
        if(contador == 0) contador = 1;
        if(estado == null) estado = "PENDIENTE";
    }
}