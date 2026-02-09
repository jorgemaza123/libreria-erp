package com.libreria.sistema.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador personalizado de errores HTTP.
 * Maneja errores como 404, 403, 500 que no lanzan excepciones Java
 * y muestra páginas de error amigables en lugar de Whitelabel Error Page.
 */
@Controller
@Slf4j
public class AppErrorController implements ErrorController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Punto de entrada principal para todos los errores HTTP.
     */
    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request, Model model) {
        // Obtener información del error
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object error = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        int statusCode = status != null ? Integer.parseInt(status.toString()) : 500;
        String errorMessage = error != null ? error.toString() : "Error desconocido";
        String path = requestUri != null ? requestUri.toString() : request.getRequestURI();

        // Log del error
        if (statusCode >= 500) {
            log.error("Error {} en {}: {}", statusCode, path, errorMessage);
            if (exception != null) {
                log.error("Excepción:", (Throwable) exception);
            }
        } else {
            log.warn("Error {} en {}: {}", statusCode, path, errorMessage);
        }

        // Detectar si es una petición AJAX
        if (isAjaxRequest(request)) {
            return handleAjaxError(statusCode, errorMessage, path);
        }

        // Configurar datos para la vista según el código de error
        configureErrorModel(model, statusCode, errorMessage, path);

        return "error";
    }

    /**
     * Detecta si la petición es AJAX.
     */
    private boolean isAjaxRequest(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equals(requestedWith) ||
               (accept != null && accept.contains("application/json"));
    }

    /**
     * Maneja errores para peticiones AJAX retornando JSON.
     */
    private ResponseEntity<Map<String, Object>> handleAjaxError(int statusCode, String errorMessage, String path) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("status", statusCode);
        response.put("error", getErrorTitle(statusCode));
        response.put("message", getErrorMessage(statusCode));
        response.put("path", path);
        response.put("timestamp", LocalDateTime.now().format(FORMATTER));

        return ResponseEntity.status(statusCode).body(response);
    }

    /**
     * Configura el modelo con datos amigables según el código de error.
     */
    private void configureErrorModel(Model model, int statusCode, String errorMessage, String path) {
        model.addAttribute("status", statusCode);
        model.addAttribute("error", HttpStatus.resolve(statusCode) != null ?
                HttpStatus.resolve(statusCode).getReasonPhrase() : "Error");
        model.addAttribute("titulo", getErrorTitle(statusCode));
        model.addAttribute("mensaje", getErrorMessage(statusCode));
        model.addAttribute("icono", getErrorIcon(statusCode));
        model.addAttribute("iconoColor", getErrorIconColor(statusCode));
        model.addAttribute("path", path);
        model.addAttribute("timestamp", LocalDateTime.now().format(FORMATTER));

        // Detalles técnicos solo en desarrollo (opcional)
        if (errorMessage != null && !errorMessage.isEmpty() && !errorMessage.equals("No message available")) {
            model.addAttribute("detalles", errorMessage);
        }
    }

    /**
     * Obtiene el título amigable según el código de error.
     */
    private String getErrorTitle(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Solicitud inválida";
            case 401 -> "No autenticado";
            case 403 -> "Acceso denegado";
            case 404 -> "Página no encontrada";
            case 405 -> "Método no permitido";
            case 408 -> "Tiempo de espera agotado";
            case 409 -> "Conflicto de datos";
            case 413 -> "Archivo muy grande";
            case 415 -> "Tipo de archivo no soportado";
            case 429 -> "Demasiadas solicitudes";
            case 500 -> "Error del servidor";
            case 502 -> "Servidor no disponible";
            case 503 -> "Servicio no disponible";
            case 504 -> "Tiempo de espera del servidor";
            default -> "Error " + statusCode;
        };
    }

    /**
     * Obtiene el mensaje descriptivo amigable según el código de error.
     */
    private String getErrorMessage(int statusCode) {
        return switch (statusCode) {
            case 400 -> "La solicitud no pudo ser procesada debido a datos incorrectos. Por favor verifique e intente nuevamente.";
            case 401 -> "Debe iniciar sesión para acceder a esta página. Por favor ingrese con su usuario y contraseña.";
            case 403 -> "No tiene permisos para acceder a esta sección. Contacte al administrador si cree que es un error.";
            case 404 -> "La página que busca no existe o ha sido movida. Verifique la URL e intente nuevamente.";
            case 405 -> "El método de solicitud no está permitido para este recurso.";
            case 408 -> "La solicitud tardó demasiado tiempo. Por favor intente nuevamente.";
            case 409 -> "Hay un conflicto con los datos actuales. Por favor recargue la página e intente nuevamente.";
            case 413 -> "El archivo que intenta subir es demasiado grande. El límite máximo es 10MB.";
            case 415 -> "El tipo de archivo no es soportado. Por favor use un formato válido.";
            case 429 -> "Ha realizado demasiadas solicitudes. Por favor espere un momento e intente nuevamente.";
            case 500 -> "Ha ocurrido un error interno en el servidor. Por favor intente nuevamente más tarde.";
            case 502 -> "El servidor no está respondiendo correctamente. Por favor intente nuevamente más tarde.";
            case 503 -> "El servicio no está disponible temporalmente. Por favor intente nuevamente más tarde.";
            case 504 -> "El servidor tardó demasiado en responder. Por favor intente nuevamente.";
            default -> "Ha ocurrido un error inesperado. Por favor intente nuevamente o contacte al soporte técnico.";
        };
    }

    /**
     * Obtiene el ícono FontAwesome según el código de error.
     */
    private String getErrorIcon(int statusCode) {
        return switch (statusCode) {
            case 400 -> "fas fa-exclamation-circle";
            case 401 -> "fas fa-lock";
            case 403 -> "fas fa-ban";
            case 404 -> "fas fa-search";
            case 405, 415 -> "fas fa-times-circle";
            case 408, 504 -> "fas fa-clock";
            case 409 -> "fas fa-sync-alt";
            case 413 -> "fas fa-file-upload";
            case 429 -> "fas fa-hand-paper";
            case 500, 502, 503 -> "fas fa-server";
            default -> "fas fa-exclamation-triangle";
        };
    }

    /**
     * Obtiene el color del ícono según el código de error.
     */
    private String getErrorIconColor(int statusCode) {
        if (statusCode >= 500) {
            return "#dc3545"; // Rojo - Error del servidor
        } else if (statusCode == 403 || statusCode == 401) {
            return "#fd7e14"; // Naranja - Acceso denegado
        } else if (statusCode == 404) {
            return "#6c757d"; // Gris - No encontrado
        } else if (statusCode >= 400) {
            return "#ffc107"; // Amarillo - Error del cliente
        }
        return "#17a2b8"; // Azul - Información
    }
}
