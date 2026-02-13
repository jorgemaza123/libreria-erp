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
        if (conexionMovilService != null) {
            return conexionMovilService.generarServerUrl();
        }
        String protocol = sslEnabled ? "https" : "http";
        return String.format("%s://localhost:%d", protocol, serverPort);
    }

    @ModelAttribute("serverIp")
    public String agregarServerIp() {
        if (conexionMovilService != null) {
            return conexionMovilService.obtenerIpConfigurada();
        }
        return "localhost";
    }

    @ModelAttribute("networkInfo")
    public Map<String, Object> agregarNetworkInfo() {
        Map<String, Object> info = new HashMap<>();
        String ip = conexionMovilService != null ? conexionMovilService.obtenerIpConfigurada() : "localhost";
        String url = conexionMovilService != null ? conexionMovilService.generarServerUrl()
                : String.format("%s://localhost:%d", sslEnabled ? "https" : "http", serverPort);

        info.put("ip", ip);
        info.put("port", serverPort);
        info.put("url", url);
        info.put("sslEnabled", sslEnabled);
        info.put("protocol", sslEnabled ? "https" : "http");

        return info;
    }
}
