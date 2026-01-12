package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SUNAT: 1=DNI, 6=RUC, 4=CARNET EXT., 0=OTROS
    @Column(nullable = false, length = 1)
    private String tipoDocumento;

    @Column(nullable = false, unique = true, length = 15)
    private String numeroDocumento;

    @Column(nullable = false)
    private String nombreRazonSocial; // Nombre o Razón Social

    private String direccion; // Obligatorio para Facturas
    private String telefono;
    private String telefonoSecundario; // Contacto alternativo
    private String email; // Para enviar el XML/PDF

    // DATOS DE CRÉDITO
    @Column(name = "tiene_credito")
    private boolean tieneCredito = false; // ¿Está autorizado para fiar?
    
    private Integer diasCredito = 0; // Ej: 7, 15, 30 días

    @Column(precision = 10, scale = 2)
    private BigDecimal limiteCredito; // Monto máximo de crédito autorizado

    @Column(precision = 10, scale = 2)
    private BigDecimal saldoDeudor = BigDecimal.ZERO; // Deuda actual acumulada

    // ESTADÍSTICAS (actualizadas automáticamente)
    private LocalDate fechaUltimaCompra; // Última vez que compró

    @Column(precision = 12, scale = 2)
    private BigDecimal totalComprasHistorico = BigDecimal.ZERO; // Total comprado en toda la historia

    private Integer cantidadCompras = 0; // Número de compras realizadas

    // CLASIFICACIÓN Y NOTAS
    @Column(length = 20)
    private String categoria; // VIP, FRECUENTE, NUEVO, MOROSO, etc.

    @Column(length = 500)
    private String observaciones; // Notas internas del cliente

    // ESTADO
    private boolean activo = true; // Para dar de baja sin eliminar

    // AUDITORÍA
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaActualizacion;

    // --- GETTERS MANUALES PARA BOOLEANOS (Solución al error undefined) ---
    public boolean isTieneCredito() {
        return tieneCredito;
    }
    
    public boolean getTieneCredito() { // Alias para compatibilidad
        return tieneCredito;
    }

    public void setTieneCredito(boolean tieneCredito) {
        this.tieneCredito = tieneCredito;
    }
    // ---------------------------------------------------------------------

    @PrePersist
    protected void onCreate() {
        this.fechaRegistro = LocalDateTime.now();
        this.fechaActualizacion = LocalDateTime.now();
        if (this.limiteCredito == null) this.limiteCredito = BigDecimal.ZERO;
        if (this.saldoDeudor == null) this.saldoDeudor = BigDecimal.ZERO;
        if (this.totalComprasHistorico == null) this.totalComprasHistorico = BigDecimal.ZERO;
        if (this.cantidadCompras == null) this.cantidadCompras = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    /**
     * Verifica si el cliente puede recibir más crédito
     */
    public boolean puedeRecibirCredito(BigDecimal montoSolicitado) {
        if (!tieneCredito) return false;
        if (limiteCredito == null || limiteCredito.compareTo(BigDecimal.ZERO) <= 0) return false;

        BigDecimal deudaActual = saldoDeudor != null ? saldoDeudor : BigDecimal.ZERO;
        BigDecimal deudaFutura = deudaActual.add(montoSolicitado);

        return deudaFutura.compareTo(limiteCredito) <= 0;
    }

    /**
     * Obtiene el crédito disponible
     */
    public BigDecimal getCreditoDisponible() {
        if (!tieneCredito || limiteCredito == null) return BigDecimal.ZERO;
        BigDecimal deudaActual = saldoDeudor != null ? saldoDeudor : BigDecimal.ZERO;
        BigDecimal disponible = limiteCredito.subtract(deudaActual);
        return disponible.compareTo(BigDecimal.ZERO) > 0 ? disponible : BigDecimal.ZERO;
    }
}