package com.libreria.sistema.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Manejador global de excepciones para toda la aplicación.
 * Detecta automáticamente si es una petición AJAX o web y responde apropiadamente.
 * Evita la exposición de stack traces y proporciona respuestas consistentes.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Detecta si la petición es AJAX (espera JSON) o una petición web normal.
     */
    private boolean isAjaxRequest(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        String contentType = request.getContentType();

        return "XMLHttpRequest".equals(requestedWith) ||
               (accept != null && accept.contains("application/json")) ||
               (contentType != null && contentType.contains("application/json"));
    }

    /**
     * Crea un ModelAndView para la página de error.
     */
    private ModelAndView createErrorView(HttpStatus status, String titulo, String mensaje,
                                          String detalles, HttpServletRequest request) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", status.value());
        mav.addObject("error", status.getReasonPhrase());
        mav.addObject("titulo", titulo);
        mav.addObject("mensaje", mensaje);
        mav.addObject("detalles", detalles);
        mav.addObject("timestamp", LocalDateTime.now().format(FORMATTER));
        mav.addObject("path", request.getRequestURI());
        mav.setStatus(status);
        return mav;
    }

    /**
     * Crea una respuesta JSON para errores AJAX.
     */
    private ResponseEntity<Map<String, Object>> createJsonError(HttpStatus status, String mensaje) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("status", status.value());
        error.put("error", status.getReasonPhrase());
        error.put("message", mensaje);
        error.put("timestamp", LocalDateTime.now().format(FORMATTER));
        return ResponseEntity.status(status).body(error);
    }

    // =====================================================
    //  ERRORES DE RECURSOS NO ENCONTRADOS (404)
    // =====================================================

    /**
     * Maneja recursos no encontrados (NoSuchElementException).
     */
    @ExceptionHandler(NoSuchElementException.class)
    public Object handleNoSuchElement(NoSuchElementException ex, HttpServletRequest request) {
        log.warn("Recurso no encontrado: {} - {}", request.getRequestURI(), ex.getMessage());

        if (isAjaxRequest(request)) {
            return createJsonError(HttpStatus.NOT_FOUND, "El recurso solicitado no fue encontrado.");
        }

        return createErrorView(
                HttpStatus.NOT_FOUND,
                "Recurso no encontrado",
                "El elemento que busca no existe o ha sido eliminado.",
                ex.getMessage(),
                request
        );
    }

    /**
     * Maneja rutas no encontradas (404).
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("Ruta no encontrada: {}", ex.getRequestURL());

        if (isAjaxRequest(request)) {
            return createJsonError(HttpStatus.NOT_FOUND, "La página solicitada no existe.");
        }

        return createErrorView(
                HttpStatus.NOT_FOUND,
                "Página no encontrada",
                "La página que busca no existe. Verifique la URL e intente nuevamente.",
                "URL: " + ex.getRequestURL(),
                request
        );
    }

    // =====================================================
    //  ERRORES DE ACCESO Y PERMISOS (403)
    // =====================================================

    /**
     * Maneja errores de acceso denegado lanzados por @PreAuthorize en métodos de servicio.
     * Nota: Los 403 del filtro HTTP son interceptados por apiAccessDeniedHandler en SecurityConfig
     * antes de llegar aquí. Este handler actúa como fallback para excepciones en la capa de servicio.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Acceso denegado: {} - Usuario: {}", request.getRequestURI(),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "Anónimo");

        if (isAjaxRequest(request)) {
            return createJsonError(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta acción.");
        }

        return createErrorView(
                HttpStatus.FORBIDDEN,
                "Acceso denegado",
                "No tiene permisos para acceder a esta sección. Contacte al administrador si cree que es un error.",
                null,
                request
        );
    }

    // =====================================================
    //  ERRORES DE VALIDACIÓN Y DATOS (400)
    // =====================================================

    /**
     * Maneja errores de validación de DTOs.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Errores de validación en {}: {}", request.getRequestURI(), errors);

        if (isAjaxRequest(request)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("status", 400);
            response.put("error", "Errores de validación");
            response.put("errors", errors);
            return ResponseEntity.badRequest().body(response);
        }

        return createErrorView(
                HttpStatus.BAD_REQUEST,
                "Datos inválidos",
                "Los datos ingresados contienen errores. Por favor verifique e intente nuevamente.",
                errors.toString(),
                request
        );
    }

    /**
     * Maneja argumentos ilegales (validaciones manuales).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Argumento ilegal en {}: {}", request.getRequestURI(), ex.getMessage());

        if (isAjaxRequest(request)) {
            return createJsonError(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        return createErrorView(
                HttpStatus.BAD_REQUEST,
                "Datos inválidos",
                ex.getMessage(),
                null,
                request
        );
    }

    /**
     * Maneja estados ilegales (operaciones no permitidas).
     */
    @ExceptionHandler(IllegalStateException.class)
    public Object handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("Estado ilegal en {}: {}", request.getRequestURI(), ex.getMessage());

        if (isAjaxRequest(request)) {
            return createJsonError(HttpStatus.CONFLICT, ex.getMessage());
        }

        return createErrorView(
                HttpStatus.CONFLICT,
                "Operación no permitida",
                ex.getMessage(),
                null,
                request
        );
    }

    // =====================================================
    //  ERRORES DE BASE DE DATOS
    // =====================================================

    /**
     * Maneja conflictos de concurrencia (bloqueo optimista).
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public Object handleOptimisticLockingFailure(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Conflicto de concurrencia en {}", request.getRequestURI());

        String mensaje = "El registro fue modificado por otro usuario. Por favor recargue la página e intente nuevamente.";

        if (isAjaxRequest(request)) {
            return createJsonError(HttpStatus.CONFLICT, mensaje);
        }

        return createErrorView(
                HttpStatus.CONFLICT,
                "Conflicto de datos",
                mensaje,
                null,
                request
        );
    }

    /**
     * Maneja violaciones de integridad de datos.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Violación de integridad en {}", request.getRequestURI(), ex);

        String mensaje = "Error: Los datos ingresados violan las restricciones de la base de datos.";
        String rootCause = ex.getMostSpecificCause().getMessage();

        if (rootCause != null) {
            if (rootCause.contains("duplicate key") || rootCause.contains("Duplicate entry") ||
                rootCause.contains("unique constraint")) {
                mensaje = "Ya existe un registro con estos datos. Por favor verifique e intente con datos diferentes.";
            } else if (rootCause.contains("foreign key constraint") || rootCause.contains("violates foreign key")) {
                mensaje = "No se puede eliminar este registro porque está siendo utilizado en otras partes del sistema.";
            } else if (rootCause.contains("null value in column") || rootCause.contains("cannot be null")) {
                mensaje = "Faltan datos requeridos. Por favor complete todos los campos obligatorios.";
            }
        }

        if (isAjaxRequest(request)) {
            return createJsonError(HttpStatus.BAD_REQUEST, mensaje);
        }

        return createErrorView(
                HttpStatus.BAD_REQUEST,
                "Error de datos",
                mensaje,
                null,
                request
        );
    }

    // =====================================================
    //  ERRORES DE ARCHIVOS
    // =====================================================

    /**
     * Maneja archivos demasiado grandes.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxSizeException(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Archivo demasiado grande en {}", request.getRequestURI());

        String mensaje = "El archivo es demasiado grande. Tamaño máximo permitido: 10MB";

        if (isAjaxRequest(request)) {
            return createJsonError(HttpStatus.PAYLOAD_TOO_LARGE, mensaje);
        }

        return createErrorView(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Archivo muy grande",
                mensaje,
                null,
                request
        );
    }

    // =====================================================
    //  ERRORES GENÉRICOS (500)
    // =====================================================

    /**
     * Maneja excepciones de runtime genéricas.
     */
    @ExceptionHandler(RuntimeException.class)
    public Object handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        log.error("Error de ejecución en {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        String mensaje = "Ha ocurrido un error al procesar su solicitud. Por favor intente nuevamente.";

        if (isAjaxRequest(request)) {
            return createJsonError(HttpStatus.INTERNAL_SERVER_ERROR, mensaje);
        }

        return createErrorView(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error del sistema",
                mensaje,
                "Si el problema persiste, contacte al administrador del sistema.",
                request
        );
    }

    /**
     * Maneja cualquier otra excepción no contemplada.
     */
    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        String mensaje = "Ha ocurrido un error inesperado. Por favor intente nuevamente más tarde.";

        if (isAjaxRequest(request)) {
            return createJsonError(HttpStatus.INTERNAL_SERVER_ERROR, mensaje);
        }

        return createErrorView(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error inesperado",
                mensaje,
                "Si el problema persiste, contacte al administrador del sistema.",
                request
        );
    }
}
