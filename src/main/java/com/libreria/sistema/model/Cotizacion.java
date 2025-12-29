package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "cotizaciones")
public class Cotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String serie;  // C001
    private Integer numero;

    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;

    // Cliente
    private String clienteDocumento;
    private String clienteNombre;
    private String clienteTelefono; // <--- NUEVO CAMPO

    // Totales
    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    private String estado; 

    private LocalDateTime fechaCreacion;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<DetalleCotizacion> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        this.fechaEmision = LocalDate.now();
        this.fechaVencimiento = LocalDate.now().plusDays(15);
        if(this.estado == null) this.estado = "EMITIDO";
    }
}