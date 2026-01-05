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

@Controller
@RequestMapping("/public")
public class PublicResourcesController {

    @Autowired
    private ConfiguracionService configuracionService;

    @GetMapping(value = "/css-personalizado", produces = "text/css")
    @ResponseBody
    public ResponseEntity<String> cssPersonalizado() {
        String css = configuracionService.generarCssPersonalizado();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/css"));
        
        // CAMBIO CRÍTICO: Desactivar caché para ver cambios al instante
        headers.setCacheControl(CacheControl.noCache().mustRevalidate());
        headers.setPragma("no-cache");
        headers.setExpires(0);

        return ResponseEntity.ok().headers(headers).body(css);
    }
}