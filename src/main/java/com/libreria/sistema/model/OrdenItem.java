package com.libreria.sistema.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "orden_items")
@Data
public class OrdenItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    private BigDecimal costo;

    @ManyToOne
    @JoinColumn(name = "orden_id")
    @JsonIgnore // Importante para evitar bucles infinitos al serializar a JSON
    private OrdenServicio orden;
}