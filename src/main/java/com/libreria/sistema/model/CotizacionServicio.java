package com.libreria.sistema.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "cotizacion_servicios")
@Data
public class CotizacionServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 24)
    private String codigoPublico;

    private String tipoServicio;
    private String tituloTrabajo;

    private String clienteNombre;
    private String clienteTelefono;
    private String clienteDocumento;
    private String clienteEmail;
    private String clienteDireccion;

    private LocalDate fechaEntregaEstimada;
    private LocalDate fechaValidez;
    private LocalDateTime fechaCreacion;

    private BigDecimal total;

    private String estado;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC, id ASC")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<CotizacionServicioSeccion> secciones = new LinkedHashSet<>();

    @PrePersist
    protected void onCreate() {
        fechaCreacion = LocalDateTime.now();
        normalizar();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizar();
    }

    private void normalizar() {
        if (total == null) total = BigDecimal.ZERO;
        if (estado == null || estado.isBlank()) estado = "BORRADOR";
    }
}
