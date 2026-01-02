package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SUNAT: 1=DNI, 6=RUC, 4=CARNET EXT., 0=OTROS
    @Column(nullable = false, length = 1)
    private String tipoDocumento; 

    @Column(nullable = false, unique = true, length = 15)
    private String numeroDocumento;

    @Column(nullable = false)
    private String nombreRazonSocial; // Nombre o Razón Social

    private String direccion; // Obligatorio para Facturas
    private String telefono;
    private String email; // Para enviar el XML/PDF

    // DATOS DE CRÉDITO
    private boolean tieneCredito = false; // ¿Está autorizado para fiar?
    private Integer diasCredito = 0; // Ej: 7, 15, 30 días

    // AUDITORÍA
    private LocalDateTime fechaRegistro;

    @PrePersist
    protected void onCreate() {
        this.fechaRegistro = LocalDateTime.now();
    }
}