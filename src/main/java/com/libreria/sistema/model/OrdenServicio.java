package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;


@Entity
@Table(name = "orden_servicios")
@Data
public class OrdenServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipoServicio; 
    private String tituloTrabajo; 

    private String clienteNombre;
    private String clienteTelefono;
    private String clienteDocumento;
    private String clienteEmail;
    private String clienteDireccion;
    private Long cotizacionId;

    private LocalDateTime fechaRecepcion;
    private LocalDate fechaEntregaEstimada;
    private LocalDate fechaRecordatorio;
    private Boolean recordatorioActivo = false;

    private BigDecimal total;
    private BigDecimal aCuenta; 
    private BigDecimal saldo;   
    private Long ventaId;

    private String estado;
    private String prioridad;

    @Column(columnDefinition = "TEXT")
    private String observaciones; 

    @Column(columnDefinition = "TEXT")
    private String recordatorioNota;

    // AQUÍ ES DONDE SUELE FALLAR SI NO ENCUENTRA LA CLASE
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<OrdenItem> items = new LinkedHashSet<>();

    @OneToMany(mappedBy = "ordenServicio", cascade = CascadeType.ALL, orphanRemoval = true)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<OrdenServicioPago> pagos = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        fechaRecepcion = LocalDateTime.now();
        if(estado == null) estado = "PENDIENTE";
        if(prioridad == null || prioridad.isBlank()) prioridad = "NORMAL";
        if(aCuenta == null) aCuenta = BigDecimal.ZERO;
        if(saldo == null) saldo = BigDecimal.ZERO;
        if(recordatorioActivo == null) recordatorioActivo = false;
    }

    @PreUpdate
    protected void onUpdate() {
        if(prioridad == null || prioridad.isBlank()) prioridad = "NORMAL";
        if(aCuenta == null) aCuenta = BigDecimal.ZERO;
        if(saldo == null) saldo = BigDecimal.ZERO;
        if(recordatorioActivo == null) recordatorioActivo = false;
    }
}
