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

    private String codigo; // EJ: BOLETA, FACTURA, NOTA_VENTA
    private String serie;  // EJ: B001, F001
    private Integer ultimoNumero; // EJ: 0, 1, 100...

    public Correlativo() {}

    public Correlativo(String codigo, String serie, Integer ultimoNumero) {
        this.codigo = codigo;
        this.serie = serie;
        this.ultimoNumero = ultimoNumero;
    }
}