package com.libreria.sistema.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación para marcar métodos que requieren una caja abierta.
 *
 * Cuando se aplica a un método de controlador, el sistema verificará
 * automáticamente que el usuario actual tenga una sesión de caja activa
 * antes de ejecutar el método.
 *
 * Si NO hay caja abierta:
 * - Para peticiones web: Redirige a /caja/apertura con mensaje flash
 * - Para peticiones AJAX: Devuelve JSON con error 403
 *
 * Uso:
 * @RequerirCajaAbierta
 * @GetMapping("/ventas/nueva")
 * public String nuevaVenta(Model model) { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequerirCajaAbierta {

    /**
     * Mensaje personalizado a mostrar cuando no hay caja abierta.
     * Por defecto: "Debe abrir caja antes de realizar esta operación."
     */
    String mensaje() default "Debe abrir caja antes de realizar esta operación.";

    /**
     * URL de redirección cuando no hay caja abierta.
     * Por defecto: "/caja/apertura"
     */
    String redirectUrl() default "/caja/apertura";
}
