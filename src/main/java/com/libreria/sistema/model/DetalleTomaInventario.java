package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Entidad que representa el detalle de cada producto en una toma de inventario.
 * Contiene el snapshot del stock del sistema y el conteo físico realizado.
 */
@Data
@Entity
@Table(name = "detalle_toma_inventario",
       uniqueConstraints = @UniqueConstraint(columnNames = {"toma_inventario_id", "producto_id"}))
public class DetalleTomaInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Toma de inventario a la que pertenece este detalle.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "toma_inventario_id", nullable = false)
    private TomaInventario tomaInventario;

    /**
     * Producto que se está contando.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /**
     * Stock que tenía el sistema al momento de iniciar la toma (SNAPSHOT).
     * Este valor NO cambia una vez creado el registro.
     */
    @Column(nullable = false)
    private Integer stockSistema;

    /**
     * Stock físico contado por el usuario.
     * NULL indica que aún no se ha contado este producto.
     */
    private Integer stockFisico;

    /**
     * Diferencia calculada: stockFisico - stockSistema.
     * Negativo = Faltante, Positivo = Sobrante.
     */
    private Integer diferencia;

    /**
     * Indica si este producto ya fue contado/verificado.
     */
    @Column(nullable = false)
    private Boolean contado = false;

    /**
     * Fecha/hora en que se registró el conteo físico.
     */
    private LocalDateTime fechaConteo;

    /**
     * Observación específica para este producto (opcional).
     * Ej: "Producto dañado", "Encontrado en otra ubicación".
     */
    @Column(length = 255)
    private String observacion;

    /**
     * Indica si el ajuste ya fue aplicado al stock (durante el procesamiento).
     */
    @Column(nullable = false)
    private Boolean ajusteAplicado = false;

    // ========== MÉTODOS HELPER ==========

    /**
     * Registra el conteo físico y calcula la diferencia.
     */
    public void registrarConteo(Integer cantidadFisica) {
        this.stockFisico = cantidadFisica;
        this.diferencia = cantidadFisica - this.stockSistema;
        this.contado = true;
        this.fechaConteo = LocalDateTime.now();
    }

    /**
     * Verifica si hay diferencia entre el stock físico y el del sistema.
     */
    public boolean tieneDiferencia() {
        return this.diferencia != null && this.diferencia != 0;
    }

    /**
     * Verifica si es un faltante (diferencia negativa).
     */
    public boolean esFaltante() {
        return this.diferencia != null && this.diferencia < 0;
    }

    /**
     * Verifica si es un sobrante (diferencia positiva).
     */
    public boolean esSobrante() {
        return this.diferencia != null && this.diferencia > 0;
    }

    /**
     * Obtiene el valor absoluto de la diferencia.
     */
    public int getDiferenciaAbsoluta() {
        return this.diferencia != null ? Math.abs(this.diferencia) : 0;
    }

    @PrePersist
    protected void onCreate() {
        if (this.contado == null) this.contado = false;
        if (this.ajusteAplicado == null) this.ajusteAplicado = false;
    }
}
