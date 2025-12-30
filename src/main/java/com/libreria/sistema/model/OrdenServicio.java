package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    private LocalDateTime fechaRecepcion;
    private LocalDate fechaEntregaEstimada;

    private BigDecimal total;
    private BigDecimal aCuenta; 
    private BigDecimal saldo;   

    private String estado;

    @Column(columnDefinition = "TEXT")
    private String observaciones; 

    // AQUÍ ES DONDE SUELE FALLAR SI NO ENCUENTRA LA CLASE
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrdenItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        fechaRecepcion = LocalDateTime.now();
        if(estado == null) estado = "PENDIENTE";
    }
}