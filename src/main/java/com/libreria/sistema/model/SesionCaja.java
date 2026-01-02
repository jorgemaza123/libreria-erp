package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sesiones_caja")
public class SesionCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;

    @Column(precision = 10, scale = 2)
    private BigDecimal montoInicial; // Con cuanto empezó

    @Column(precision = 10, scale = 2)
    private BigDecimal montoFinalCalculado; // Lo que el sistema dice que debe haber

    @Column(precision = 10, scale = 2)
    private BigDecimal montoFinalReal; // Lo que el cajero contó físicamente

    @Column(precision = 10, scale = 2)
    private BigDecimal diferencia; // Sobrante o Faltante

    private String estado; // ABIERTA, CERRADA

    @PrePersist
    protected void onCreate() {
        this.fechaInicio = LocalDateTime.now();
        this.estado = "ABIERTA";
    }
}