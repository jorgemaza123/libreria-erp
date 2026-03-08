package com.libreria.sistema.config;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.service.ConexionMovilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración global para TODAS las vistas del sistema.
 * Inyecta automáticamente la configuración en cada vista para eliminar valores hardcodeados.
 *
 * La IP y URL del servidor se leen desde BD (ConexionMovilService) — no se recalculan.
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private ConfiguracionService configuracionService;

    @Autowired(required = false)
    private ConexionMovilService conexionMovilService;

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    @ModelAttribute("config")
    public Configuracion agregarConfiguracionGlobal() {
        return configuracionService.obtenerConfiguracion();
    }

    @ModelAttribute("serverUrl")
    public String agregarServerUrl() {
        return resolveServerUrl();
    }

    @ModelAttribute("serverIp")
    public String agregarServerIp() {
        return resolveServerIp();
    }

    @ModelAttribute("networkInfo")
    public Map<String, Object> agregarNetworkInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("ip", resolveServerIp());
        info.put("port", serverPort);
        info.put("url", resolveServerUrl());
        info.put("sslEnabled", sslEnabled);
        info.put("protocol", sslEnabled ? "https" : "http");
        return info;
    }

    private String resolveServerIp() {
        return conexionMovilService != null ? conexionMovilService.obtenerIpConfigurada() : "localhost";
    }

    private String resolveServerUrl() {
        if (conexionMovilService != null) return conexionMovilService.generarServerUrl();
        return String.format("%s://localhost:%d", sslEnabled ? "https" : "http", serverPort);
    }
}
