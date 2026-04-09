package com.libreria.sistema.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CotizacionDTO {

    // Cliente
    private String clienteDocumento;
    private String clienteNombre;
    private String clienteTelefono;
    private String clienteEmail;
    private String clienteDireccion;

    // Servicio / proyecto
    private String tipoServicioContrato;
    private String tituloProyectoServicio;
    private LocalDate fechaEntregaComprometida;

    // Textos
    private String observaciones;
    private String condiciones;

    // Forma de pago
    private String formaPago = "CONTADO";       // CONTADO o CREDITO
    private String metodoPago = "EFECTIVO";     // EFECTIVO, YAPE, PLIN, TARJETA, TRANSFERENCIA
    private BigDecimal montoInicial;            // Adelanto si es crédito
    private Integer diasCredito;                // Días de crédito

    // Descuento global
    private BigDecimal descuento;

    // Estado deseado (BORRADOR o EMITIDA)
    private String estado;

    // Items
    private List<ItemDTO> items;

    @Data
    public static class ItemDTO {
        private Long productoId;                // null para servicios puros
        private String tipoItem = "PRODUCTO";   // PRODUCTO o SERVICIO
        private String categoriaServicio;       // SUBLIMACION, COPIAS, etc. (solo para SERVICIO)
        private String descripcion;
        private BigDecimal cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal costoEstimado;       // Costo estimado para cálculo de margen
        private String parametrosJson;          // JSON libre para parámetros personalizados
    }
}
