package com.libreria.sistema.config;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para toda la aplicación
 * Evita la exposición de stack traces y proporciona respuestas consistentes
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Maneja errores de validación de DTOs
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Errores de validación: {}", errors);
        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * Maneja conflictos de concurrencia (bloqueo optimista)
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<String> handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        log.warn("Conflicto de concurrencia detectado", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("El registro fue modificado por otro usuario. Por favor recargue e intente nuevamente.");
    }

    /**
     * Maneja violaciones de integridad de datos (claves duplicadas, etc.)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Violación de integridad de datos", ex);
        String message = "Error: Los datos ingresados violan las restricciones de la base de datos.";

        // Intentar proporcionar un mensaje más específico
        String rootCause = ex.getMostSpecificCause().getMessage();
        if (rootCause != null) {
            if (rootCause.contains("duplicate key") || rootCause.contains("Duplicate entry")) {
                message = "Error: Ya existe un registro con estos datos.";
            } else if (rootCause.contains("foreign key constraint")) {
                message = "Error: No se puede eliminar este registro porque está siendo utilizado.";
            }
        }

        return ResponseEntity.badRequest().body(message);
    }

    /**
     * Maneja errores de acceso denegado
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("No tiene permisos para realizar esta acción.");
    }

    /**
     * Maneja archivos demasiado grandes
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        log.warn("Archivo demasiado grande: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body("El archivo es demasiado grande. Tamaño máximo permitido: 10MB");
    }

    /**
     * Maneja argumentos ilegales (validaciones manuales)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Argumento ilegal: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    /**
     * Maneja excepciones de runtime genéricas
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        log.error("Error de ejecución", ex);
        // NO exponemos el mensaje de error completo al usuario
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al procesar la solicitud. Por favor contacte al administrador.");
    }

    /**
     * Maneja cualquier otra excepción no contemplada
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        log.error("Error inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Ha ocurrido un error inesperado. Por favor contacte al administrador.");
    }
}
