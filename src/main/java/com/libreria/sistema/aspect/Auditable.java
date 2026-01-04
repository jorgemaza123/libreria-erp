package com.libreria.sistema.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación para marcar métodos que deben ser auditados.
 *
 * Ejemplo de uso:
 * @Auditable(modulo = "PRODUCTOS", accion = "MODIFICAR")
 * public Producto actualizarProducto(Long id, Producto producto) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * Módulo donde se realiza la acción
     * Ejemplos: PRODUCTOS, VENTAS, COMPRAS, CAJA, INVENTARIO, CONFIGURACION
     */
    String modulo();

    /**
     * Tipo de acción realizada
     * Ejemplos: CREAR, MODIFICAR, ELIMINAR, ANULAR
     */
    String accion();

    /**
     * Descripción adicional de la operación (opcional)
     */
    String descripcion() default "";
}
