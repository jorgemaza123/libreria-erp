package com.libreria.sistema.aspect;

import com.libreria.sistema.service.AuditoriaService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Aspecto que intercepta métodos anotados con @Auditable para registrar auditoría
 */
@Aspect
@Component
@Slf4j
public class AuditoriaAspect {

    @Autowired
    private AuditoriaService auditoriaService;

    /**
     * Intercepta métodos anotados con @Auditable
     */
    @Around("@annotation(auditable)")
    public Object auditar(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        // Información de la anotación
        String modulo = auditable.modulo();
        String accion = auditable.accion();
        String descripcion = auditable.descripcion();

        // Información del método
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        // Variables para auditoría
        Object valorAnterior = null;
        Object resultado = null;
        Long entidadId = null;
        String entidad = null;
        String detalles = descripcion.isEmpty() ? method.getName() : descripcion;

        try {
            // Intentar capturar el estado anterior si es una modificación
            if ("MODIFICAR".equals(accion) || "ELIMINAR".equals(accion) || "ANULAR".equals(accion)) {
                valorAnterior = capturarEstadoAnterior(args);
                entidadId = extraerEntidadId(args);
                entidad = inferirNombreEntidad(method);
            }

            // Ejecutar el método original
            resultado = joinPoint.proceed();

            // Capturar el estado posterior
            Object valorNuevo = null;
            if ("CREAR".equals(accion) || "MODIFICAR".equals(accion)) {
                valorNuevo = resultado;
                if (entidadId == null) {
                    entidadId = extraerEntidadIdDeResultado(resultado);
                }
                if (entidad == null) {
                    entidad = inferirNombreEntidadDeResultado(resultado);
                }
            }

            // Registrar auditoría de forma asíncrona
            auditoriaService.registrarAuditoria(
                modulo,
                accion,
                entidad != null ? entidad : "Unknown",
                entidadId,
                valorAnterior,
                valorNuevo,
                detalles
            );

            return resultado;

        } catch (Exception e) {
            // Registrar también los errores
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("error", e.getMessage());
            errorInfo.put("metodo", method.getName());

            auditoriaService.registrarAuditoria(
                modulo,
                "ERROR_" + accion,
                entidad != null ? entidad : "Unknown",
                entidadId,
                valorAnterior,
                errorInfo,
                "Error al ejecutar: " + detalles + " - " + e.getMessage()
            );

            // Re-lanzar la excepción
            throw e;
        }
    }

    /**
     * Captura el estado anterior del objeto
     */
    private Object capturarEstadoAnterior(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        // Si el primer argumento es un ID, buscamos el segundo argumento que suela ser el objeto
        for (Object arg : args) {
            if (arg != null && !esTipoPrimitivo(arg)) {
                return crearCopiaSimple(arg);
            }
        }

        return null;
    }

    /**
     * Extrae el ID de la entidad de los argumentos
     */
    private Long extraerEntidadId(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        // Buscar el primer argumento que sea Long o Integer
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            } else if (arg instanceof Integer) {
                return ((Integer) arg).longValue();
            }
        }

        // Intentar obtener el ID del primer objeto que tenga método getId()
        for (Object arg : args) {
            if (arg != null) {
                try {
                    Method getIdMethod = arg.getClass().getMethod("getId");
                    Object id = getIdMethod.invoke(arg);
                    if (id instanceof Long) {
                        return (Long) id;
                    } else if (id instanceof Integer) {
                        return ((Integer) id).longValue();
                    }
                } catch (Exception ignored) {
                    // No tiene método getId()
                }
            }
        }

        return null;
    }

    /**
     * Extrae el ID de la entidad del resultado
     */
    private Long extraerEntidadIdDeResultado(Object resultado) {
        if (resultado == null) {
            return null;
        }

        try {
            Method getIdMethod = resultado.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(resultado);
            if (id instanceof Long) {
                return (Long) id;
            } else if (id instanceof Integer) {
                return ((Integer) id).longValue();
            }
        } catch (Exception ignored) {
            // No tiene método getId()
        }

        return null;
    }

    /**
     * Infiere el nombre de la entidad del nombre del método
     */
    private String inferirNombreEntidad(Method method) {
        String methodName = method.getName();

        // Patrones comunes: guardar*, actualizar*, eliminar*, crear*
        if (methodName.startsWith("guardar") || methodName.startsWith("actualizar") ||
            methodName.startsWith("eliminar") || methodName.startsWith("crear") ||
            methodName.startsWith("anular")) {
            // Extraer el nombre después del verbo
            String entidad = methodName.replaceFirst("^(guardar|actualizar|eliminar|crear|anular)", "");
            if (!entidad.isEmpty()) {
                return entidad;
            }
        }

        // Usar el nombre del tipo de retorno
        Class<?> returnType = method.getReturnType();
        if (returnType != null && !returnType.equals(Void.TYPE)) {
            return returnType.getSimpleName();
        }

        return "Unknown";
    }

    /**
     * Infiere el nombre de la entidad del resultado
     */
    private String inferirNombreEntidadDeResultado(Object resultado) {
        if (resultado == null) {
            return null;
        }
        return resultado.getClass().getSimpleName();
    }

    /**
     * Verifica si un objeto es de tipo primitivo
     */
    private boolean esTipoPrimitivo(Object obj) {
        return obj instanceof String ||
               obj instanceof Number ||
               obj instanceof Boolean ||
               obj instanceof Character;
    }

    /**
     * Crea una copia simple del objeto para auditoría
     */
    private Object crearCopiaSimple(Object obj) {
        if (obj == null) {
            return null;
        }

        // Para objetos complejos, intentamos crear un Map con los campos principales
        Map<String, Object> copia = new HashMap<>();

        try {
            Method[] methods = obj.getClass().getMethods();
            for (Method method : methods) {
                String methodName = method.getName();
                // Solo getters simples
                if (methodName.startsWith("get") &&
                    method.getParameterCount() == 0 &&
                    !methodName.equals("getClass")) {

                    String fieldName = methodName.substring(3);
                    fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);

                    Object value = method.invoke(obj);

                    // Solo valores simples
                    if (value == null || esTipoPrimitivo(value) ||
                        value instanceof java.time.LocalDateTime ||
                        value instanceof java.time.LocalDate ||
                        value instanceof java.util.Date) {
                        copia.put(fieldName, value);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo crear copia del objeto: {}", e.getMessage());
            return obj.toString();
        }

        return copia.isEmpty() ? obj.toString() : copia;
    }
}
