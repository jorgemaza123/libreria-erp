package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "plantillas_personalizadas", indexes = {
        @Index(name = "idx_plantilla_personalizada_codigo", columnList = "codigoModelo"),
        @Index(name = "idx_plantilla_personalizada_activa", columnList = "activo")
})
public class PlantillaPersonalizada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String codigoModelo;

    @Column(nullable = false, length = 180)
    private String nombreComercial;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(nullable = false, length = 60)
    private String categoria;

    @Column(length = 80)
    private String coleccionOcasion;

    @Column(length = 1200)
    private String descripcionComercial;

    private String fotoPrincipal;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(nullable = false)
    private Boolean visibleWeb = true;

    @Column(nullable = false)
    private Boolean vendibleDirecto = true;

    @Column(nullable = false)
    private Boolean permitePersonalizacion = true;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal margenMinimoPct = BigDecimal.ZERO;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal margenObjetivoPct = BigDecimal.ZERO;

    @Column(length = 1200)
    private String observacionesInternas;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @OneToMany(mappedBy = "plantilla", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlantillaComponentePersonalizado> componentes = new ArrayList<>();

    @OneToMany(mappedBy = "plantilla", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlantillaRangoPrecio> rangos = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.activo == null) this.activo = true;
        if (this.visibleWeb == null) this.visibleWeb = true;
        if (this.vendibleDirecto == null) this.vendibleDirecto = true;
        if (this.permitePersonalizacion == null) this.permitePersonalizacion = true;
        if (this.margenMinimoPct == null) this.margenMinimoPct = BigDecimal.ZERO;
        if (this.margenObjetivoPct == null) this.margenObjetivoPct = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
        if (this.margenMinimoPct == null) this.margenMinimoPct = BigDecimal.ZERO;
        if (this.margenObjetivoPct == null) this.margenObjetivoPct = BigDecimal.ZERO;
    }
}
