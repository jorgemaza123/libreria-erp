package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "correlativos")
public class Correlativo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version = 0L; // Para control de concurrencia optimista (inicializado para evitar NPE)

    private String codigo; // EJ: BOLETA, FACTURA, NOTA_VENTA
    private String serie;  // EJ: B001, F001
    private Integer ultimoNumero = 0; // EJ: 0, 1, 100... (inicializado para evitar NPE)

    public Correlativo() {
        this.version = 0L;
        this.ultimoNumero = 0;
    }

    public Correlativo(String codigo, String serie, Integer ultimoNumero) {
        this.codigo = codigo;
        this.serie = serie;
        this.ultimoNumero = ultimoNumero != null ? ultimoNumero : 0;
        this.version = 0L; // Inicializar versión para evitar NPE con @Version
    }

    /**
     * Getter seguro que nunca devuelve null.
     * SAFE UNBOXING: Si ultimoNumero es null, retorna 0.
     * Esto previene NullPointerException al generar el primer comprobante
     * de una nueva serie o cuando la base de datos tiene valores nulos.
     */
    public Integer getUltimoNumero() {
        return ultimoNumero != null ? ultimoNumero : 0;
    }
}