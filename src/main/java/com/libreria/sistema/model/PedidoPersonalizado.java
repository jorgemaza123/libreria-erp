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
@Table(name = "pedidos_personalizados", indexes = {
        @Index(name = "idx_pedido_personalizado_codigo", columnList = "codigoPedido"),
        @Index(name = "idx_pedido_personalizado_estado", columnList = "estado"),
        @Index(name = "idx_pedido_personalizado_canal", columnList = "canalOrigen")
})
public class PedidoPersonalizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigoPedido;

    @Column(nullable = false, length = 20)
    private String canalOrigen = "TIENDA";

    @Column(nullable = false, length = 20)
    private String estado = "BORRADOR";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(length = 200)
    private String clienteNombre;

    @Column(length = 1)
    private String clienteTipoDocumento;

    @Column(length = 15)
    private String clienteNumeroDocumento;

    @Column(length = 20)
    private String clienteWhatsapp;

    @Column(length = 20)
    private String clienteTelefono;

    @Column(length = 120)
    private String clienteEmail;

    @Column(length = 200)
    private String nombreDestinatario;

    @Column(length = 20)
    private String telefonoDestinatario;

    @Column(length = 800)
    private String dedicatoria;

    @Column(length = 200)
    private String nombreEtiqueta;

    @Column(length = 1000)
    private String notasDiseno;

    @Column(length = 1000)
    private String observacionesCliente;

    @Column(length = 30)
    private String modoEntrega = "RECOJO_TIENDA";

    @Column(nullable = false)
    private Boolean envioGratis = false;

    @Column(precision = 12, scale = 2)
    private BigDecimal costoEnvio = BigDecimal.ZERO;

    @Column(length = 100)
    private String departamento;

    @Column(length = 100)
    private String provincia;

    @Column(length = 100)
    private String distrito;

    @Column(length = 300)
    private String direccionEntrega;

    @Column(length = 300)
    private String referenciaEntrega;

    private LocalDate fechaEntregaSolicitada;

    @Column(length = 60)
    private String franjaEntrega;

    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal adelanto = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO;

    @Column(length = 1000)
    private String observacionesInternas;

    @Column(name = "venta_id")
    private Long ventaId;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PedidoPersonalizadoItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.canalOrigen == null || this.canalOrigen.isBlank()) this.canalOrigen = "TIENDA";
        if (this.estado == null || this.estado.isBlank()) this.estado = "BORRADOR";
        if (this.modoEntrega == null || this.modoEntrega.isBlank()) this.modoEntrega = "RECOJO_TIENDA";
        if (this.envioGratis == null) this.envioGratis = false;
        if (this.costoEnvio == null) this.costoEnvio = BigDecimal.ZERO;
        if (this.subtotal == null) this.subtotal = BigDecimal.ZERO;
        if (this.descuento == null) this.descuento = BigDecimal.ZERO;
        if (this.total == null) this.total = BigDecimal.ZERO;
        if (this.adelanto == null) this.adelanto = BigDecimal.ZERO;
        if (this.saldo == null) this.saldo = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
        if (this.costoEnvio == null) this.costoEnvio = BigDecimal.ZERO;
        if (this.subtotal == null) this.subtotal = BigDecimal.ZERO;
        if (this.descuento == null) this.descuento = BigDecimal.ZERO;
        if (this.total == null) this.total = BigDecimal.ZERO;
        if (this.adelanto == null) this.adelanto = BigDecimal.ZERO;
        if (this.saldo == null) this.saldo = BigDecimal.ZERO;
    }
}
