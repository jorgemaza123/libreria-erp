package com.libreria.sistema.config;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.service.ConfiguracionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Configuración global para TODAS las vistas del sistema.
 * Inyecta automáticamente la configuración en cada vista para eliminar valores hardcodeados.
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private ConfiguracionService configuracionService;

    /**
     * Inyecta la configuración global en TODAS las vistas automáticamente.
     * Accesible en Thymeleaf como ${config}
     */
    @ModelAttribute("config")
    public Configuracion agregarConfiguracionGlobal() {
        return configuracionService.obtenerConfiguracion();
    }
}
