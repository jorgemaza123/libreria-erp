package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "configuracion")
public class Configuracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombreEmpresa; // Ej: "Librería El Saber S.A.C."
    private String ruc;           // Ej: "20601234567"
    private String direccion;     // Ej: "Av. Larco 123, Miraflores"
    private String telefono;      // Ej: "(01) 245-8888"
    private String email;         // Ej: "contacto@elsaber.com"
    private String moneda;        // Ej: "S/" o "$"
    
    @Column(columnDefinition = "LONGTEXT") // Para guardar Base64 largo
private String logoBase64;
    @Column(columnDefinition = "TEXT")
    private String logoUrl;
}