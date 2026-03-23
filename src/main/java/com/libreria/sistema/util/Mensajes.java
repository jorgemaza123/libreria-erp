package com.libreria.sistema.util;

/**
 * BAJO-1 FIX: Centralización de mensajes de error del sistema.
 * Evita mensajes inconsistentes (mezcla de español/inglés, formatos distintos)
 * dispersos en services y controllers.
 *
 * USO: throw new RuntimeException(Mensajes.CAJA_CERRADA);
 */
public final class Mensajes {

    private Mensajes() {
    } // No instanciable

    // === CAJA ===
    public static final String CAJA_CERRADA = "CAJA CERRADA: Debe abrir la caja antes de realizar esta operación.";
    public static final String CAJA_YA_ABIERTA = "Ya existe una sesión de caja abierta. Cierre la sesión actual para abrir una nueva.";

    // === PRODUCTOS ===
    public static final String PRODUCTO_NO_ENCONTRADO = "No se encontró el producto solicitado.";
    public static final String STOCK_INSUFICIENTE = "Stock insuficiente para '%s'. Disponible: %d, Requerido: %d";
    public static final String CANTIDAD_INVALIDA = "La cantidad debe ser mayor a cero para el producto: %s";
    public static final String COSTO_INVALIDO = "El costo debe ser mayor a cero para el producto: %s";
    public static final String PRECIO_INVALIDO = "El precio de venta debe ser mayor a cero.";

    // === VENTAS ===
    public static final String VENTA_NO_ENCONTRADA = "No se encontró la venta solicitada.";
    public static final String VENTA_YA_ANULADA = "La venta ya fue anulada anteriormente.";
    public static final String MONTO_EXCEDE_DEUDA = "El monto ingresado excede la deuda pendiente.";

    // === CLIENTES ===
    public static final String CLIENTE_NO_ENCONTRADO = "No se encontró el cliente solicitado.";

    // === COMPRAS ===
    public static final String PROVEEDOR_NO_ENCONTRADO = "No se encontró el proveedor solicitado.";
    public static final String COMPRA_NO_ENCONTRADA = "No se encontró la compra solicitada.";
    public static final String COMPRA_YA_ANULADA = "La compra ya fue anulada anteriormente.";

    // === GENÉRICOS ===
    public static final String ERROR_INTERNO = "Ha ocurrido un error interno. Contacte al administrador.";
    public static final String ACCESO_DENEGADO = "No tiene permisos para realizar esta acción.";
}
