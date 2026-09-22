package com.libreria.sistema.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ConexionMovilAccessTracker {

    private static final DateTimeFormatter FECHA_FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AtomicReference<AccesoMovil> ultimoAcceso = new AtomicReference<>();

    public void registrar(HttpServletRequest request, String ruta) {
        registrar(obtenerIpCliente(request), request.getHeader("User-Agent"), ruta);
    }

    public void registrar(String ip, String userAgent, String ruta) {
        ultimoAcceso.set(new AccesoMovil(
                LocalDateTime.now(),
                ip != null ? ip : "",
                userAgent,
                ruta
        ));
    }

    public Map<String, Object> obtenerResumen() {
        AccesoMovil acceso = ultimoAcceso.get();
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("existe", acceso != null);
        if (acceso == null) {
            resumen.put("mensaje", "Todavía no llegó ninguna solicitud desde el celular.");
            return resumen;
        }

        resumen.put("fecha", acceso.fecha().format(FECHA_FORMATO));
        resumen.put("ip", acceso.ip());
        resumen.put("ruta", acceso.ruta());
        resumen.put("userAgent", acceso.userAgent() != null ? acceso.userAgent() : "");
        resumen.put("mensaje", "Último acceso móvil detectado a las " + acceso.fecha().format(FECHA_FORMATO));
        return resumen;
    }

    private String obtenerIpCliente(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private record AccesoMovil(LocalDateTime fecha, String ip, String userAgent, String ruta) {
    }
}
