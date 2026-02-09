package com.libreria.sistema.aspect;

import com.libreria.sistema.model.SesionCaja;
import com.libreria.sistema.service.CajaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Aspecto que intercepta métodos anotados con @RequerirCajaAbierta.
 *
 * Verifica que el usuario actual tenga una sesión de caja activa (estado "ABIERTA").
 * Si NO tiene caja abierta:
 * - Peticiones Web: Redirige a /caja/apertura con mensaje flash
 * - Peticiones AJAX: Devuelve JSON con código 403
 *
 * ORDEN: Se ejecuta DESPUÉS de la autenticación pero ANTES del método del controlador.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(100) // Ejecutar después de la seguridad
public class CajaAbiertaAspect {

    private final CajaService cajaService;

    /**
     * Intercepta cualquier método anotado con @RequerirCajaAbierta.
     */
    @Around("@annotation(com.libreria.sistema.aspect.RequerirCajaAbierta)")
    public Object verificarCajaAbierta(ProceedingJoinPoint joinPoint) throws Throwable {

        // Obtener la anotación para leer sus parámetros
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequerirCajaAbierta anotacion = method.getAnnotation(RequerirCajaAbierta.class);

        // Verificar si hay caja abierta
        Optional<SesionCaja> sesionActiva = cajaService.obtenerSesionActiva();

        if (sesionActiva.isPresent()) {
            // Caja abierta → continuar con el método normalmente
            return joinPoint.proceed();
        }

        // NO hay caja abierta → interceptar y redirigir
        log.warn("Acceso bloqueado a {} - No hay caja abierta", method.getName());

        // Obtener request y response del contexto
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("No hay contexto de petición HTTP");
        }

        HttpServletRequest request = attrs.getRequest();
        HttpServletResponse response = attrs.getResponse();

        String mensaje = anotacion.mensaje();
        String redirectUrl = anotacion.redirectUrl();

        // Detectar si es petición AJAX
        if (esAjaxRequest(request)) {
            // Responder con JSON
            if (response != null) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(String.format(
                    "{\"success\": false, \"error\": \"%s\", \"redirect\": \"%s\", \"code\": 403, \"cajaRequerida\": true}",
                    mensaje, redirectUrl
                ));
                response.getWriter().flush();
            }
            return null;
        }

        // Petición web normal → buscar RedirectAttributes en los argumentos
        RedirectAttributes redirectAttributes = null;
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof RedirectAttributes) {
                redirectAttributes = (RedirectAttributes) arg;
                break;
            }
        }

        // Agregar mensaje flash si es posible
        if (redirectAttributes != null) {
            redirectAttributes.addFlashAttribute("errorCaja", mensaje);
            redirectAttributes.addFlashAttribute("warning", mensaje);
        } else {
            // Almacenar en sesión como fallback
            request.getSession().setAttribute("errorCaja", mensaje);
        }

        // Redirigir a la página de apertura de caja
        if (response != null) {
            response.sendRedirect(request.getContextPath() + redirectUrl);
        }

        return null;
    }

    /**
     * Detecta si la petición es AJAX/JSON.
     */
    private boolean esAjaxRequest(HttpServletRequest request) {
        String xRequested = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        String contentType = request.getContentType();

        return "XMLHttpRequest".equals(xRequested)
            || (accept != null && accept.contains("application/json"))
            || (contentType != null && contentType.contains("application/json"));
    }
}
