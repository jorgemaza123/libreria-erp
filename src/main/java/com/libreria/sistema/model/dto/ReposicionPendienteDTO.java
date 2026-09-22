package com.libreria.sistema.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ReposicionPendienteDTO {
    private Long id;
    private Long productoId;
    private String codigoInterno;
    private String nombre;
    private String categoria;
    private Integer stockActual;
    private Integer stockMinimo;
    private BigDecimal cantidadPendiente;
    private BigDecimal cantidadVendidaAcumulada;
    private BigDecimal cantidadCompradaAplicada;
    private BigDecimal montoVentaAcumulado;
    private BigDecimal montoReposicionPendiente;
    private BigDecimal venta7Dias;
    private BigDecimal venta30Dias;
    private BigDecimal cantidadSugeridaCompra;
    private BigDecimal utilidadBrutaAcumulada;
    private BigDecimal costoIndirectoAcumulado;
    private BigDecimal utilidadNetaEstimadaAcumulada;
    private Integer vecesVendida;
    private Integer vecesComprada;
    private String estado;
    private String prioridad;
    private String bloqueCompra;
    private String presentacionRecomendada;
    private String proveedorSugerido;
    private String notas;
    private LocalDateTime primeraVenta;
    private LocalDateTime ultimaVenta;
    private LocalDateTime ultimaCompra;
}
