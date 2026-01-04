package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "compras")
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fecha;

    @ManyToOne
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    private String tipoComprobante; // FACTURA, BOLETA, GUIA
    private String numeroComprobante; // F001-2342

    private BigDecimal total;
    private String observaciones;

    @Column(nullable = false)
    private String estado = "REGISTRADA"; // "REGISTRADA", "ANULADA"

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleCompra> detalles = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.fecha = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = "REGISTRADA";
        }
    }
}