package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PedidoPersonalizadoDTO {
    private Long id;
    private String canalOrigen;
    private String estado;
    private Long clienteId;
    private String clienteNombre;
    private String clienteTipoDocumento;
    private String clienteNumeroDocumento;
    private String clienteWhatsapp;
    private String clienteTelefono;
    private String clienteEmail;
    private String nombreDestinatario;
    private String telefonoDestinatario;
    private String dedicatoria;
    private String nombreEtiqueta;
    private String notasDiseno;
    private String observacionesCliente;
    private String modoEntrega;
    private Boolean envioGratis;
    private BigDecimal costoEnvio;
    private String departamento;
    private String provincia;
    private String distrito;
    private String direccionEntrega;
    private String referenciaEntrega;
    private String fechaEntregaSolicitada;
    private String franjaEntrega;
    private BigDecimal descuento;
    private BigDecimal adelanto;
    private String observacionesInternas;
    private String tipoComprobante;
    private String formaPago;
    private String metodoPago;
    private List<ItemDTO> items = new ArrayList<>();

    @Data
    public static class ItemDTO {
        private Long plantillaId;
        private String codigoModeloSnapshot;
        private String nombreComercialSnapshot;
        private String fotoSnapshot;
        private String categoriaSnapshot;
        private BigDecimal cantidad;
        private BigDecimal costoSnapshot;
        private BigDecimal precioMinimoSnapshot;
        private BigDecimal precioSugeridoSnapshot;
        private BigDecimal precioFinal;
        private String configuracionJson;
        private List<ComponenteDTO> componentes = new ArrayList<>();
    }

    @Data
    public static class ComponenteDTO {
        private String categoria;
        private String nombre;
        private String tipoOrigen;
        private Long insumoPersonalizadoId;
        private Long adicionalPersonalizadoId;
        private BigDecimal cantidad;
        private BigDecimal costoUnitario;
        private BigDecimal costoTotal;
        private Boolean incluido;
        private Boolean eliminado;
        private Integer orden;
    }
}
