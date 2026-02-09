package com.libreria.sistema.config;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.util.NetworkUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración global para TODAS las vistas del sistema.
 * Inyecta automáticamente la configuración en cada vista para eliminar valores hardcodeados.
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private ConfiguracionService configuracionService;

    @Autowired(required = false)
    private SslConfigService sslConfigService;

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    /**
     * Inyecta la configuración global en TODAS las vistas automáticamente.
     * Accesible en Thymeleaf como ${config}
     */
    @ModelAttribute("config")
    public Configuracion agregarConfiguracionGlobal() {
        return configuracionService.obtenerConfiguracion();
    }

    /**
     * Inyecta la URL del servidor para conexiones móviles.
     * Accesible en Thymeleaf como ${serverUrl}
     * Ejemplo: https://192.168.1.100:8443
     */
    @ModelAttribute("serverUrl")
    public String agregarServerUrl() {
        if (sslConfigService != null) {
            return sslConfigService.getServerUrl();
        }
        return NetworkUtils.getServerUrl(sslEnabled, serverPort);
    }

    /**
     * Inyecta la IP local del servidor.
     * Accesible en Thymeleaf como ${serverIp}
     * Ejemplo: 192.168.1.100
     */
    @ModelAttribute("serverIp")
    public String agregarServerIp() {
        if (sslConfigService != null) {
            return sslConfigService.getLocalIp();
        }
        return NetworkUtils.getLocalIpAddress();
    }

    /**
     * Inyecta información completa de red.
     * Accesible en Thymeleaf como ${networkInfo}
     */
    @ModelAttribute("networkInfo")
    public Map<String, Object> agregarNetworkInfo() {
        Map<String, Object> info = new HashMap<>();
        String ip = sslConfigService != null ? sslConfigService.getLocalIp() : NetworkUtils.getLocalIpAddress();
        String url = sslConfigService != null ? sslConfigService.getServerUrl() : NetworkUtils.getServerUrl(sslEnabled, serverPort);

        info.put("ip", ip);
        info.put("port", serverPort);
        info.put("url", url);
        info.put("sslEnabled", sslEnabled);
        info.put("protocol", sslEnabled ? "https" : "http");

        return info;
    }
}
