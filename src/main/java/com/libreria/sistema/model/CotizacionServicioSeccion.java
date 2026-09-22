package com.libreria.sistema.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "cotizacion_servicio_secciones")
@Data
public class CotizacionServicioSeccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;

    @Column(name = "orden_visual")
    private Integer orden;

    private BigDecimal total;

    @ManyToOne
    @JoinColumn(name = "cotizacion_id")
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private CotizacionServicio cotizacion;

    @OneToMany(mappedBy = "seccion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<CotizacionServicioItem> items = new LinkedHashSet<>();

    @PrePersist
    protected void onCreate() {
        normalizar();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizar();
    }

    private void normalizar() {
        if (orden == null) orden = 0;
        if (total == null) total = BigDecimal.ZERO;
    }
}
