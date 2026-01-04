package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "configuracion_sunat")
public class ConfiguracionSunat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- DATOS DEL EMISOR ---
    @Column(nullable = false, length = 11)
    private String rucEmisor;

    @Column(nullable = false)
    private String razonSocialEmisor;

    private String nombreComercial;

    @Column(nullable = false)
    private String direccionFiscal;

    @Column(length = 6)
    private String ubigeoEmisor; // Código de 6 dígitos: departamento-provincia-distrito

    // --- CONFIGURACIÓN API SUNAT ---
    @Column(nullable = false, columnDefinition = "TEXT")
    private String tokenApiSunat; // Token encriptado del PSE

    @Column(nullable = false)
    private String urlApiSunat; // https://sandbox.apisunat.pe o https://app.apisunat.pe

    @Column(nullable = false)
    private Boolean facturaElectronicaActiva = false; // Toggle para activar/desactivar

    // --- AUDITORÍA ---
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        this.fechaActualizacion = LocalDateTime.now();
        if (this.facturaElectronicaActiva == null) {
            this.facturaElectronicaActiva = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
