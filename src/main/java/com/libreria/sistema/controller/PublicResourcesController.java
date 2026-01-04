package com.libreria.sistema.controller;

import com.libreria.sistema.service.ConfiguracionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.concurrent.TimeUnit;

/**
 * Controlador para recursos públicos accesibles sin autenticación.
 * Usado para CSS personalizado que debe cargarse incluso en la página de login.
 */
@Controller
@RequestMapping("/public")
public class PublicResourcesController {

    @Autowired
    private ConfiguracionService configuracionService;

    /**
     * Endpoint público para CSS personalizado dinámico.
     * Accesible sin autenticación para que funcione en login y todas las páginas.
     */
    @GetMapping(value = "/css-personalizado", produces = "text/css")
    @ResponseBody
    public ResponseEntity<String> cssPersonalizado() {
        String css = configuracionService.generarCssPersonalizado();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/css"));
        headers.setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS));

        return ResponseEntity.ok().headers(headers).body(css);
    }
}
