package com.libreria.sistema.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/servicios")
public class ServicioController {

    @GetMapping("/nuevo")
    public String nuevoServicio() {
        return "redirect:/ordenes/nueva";
    }
}
