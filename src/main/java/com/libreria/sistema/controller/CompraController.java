package com.libreria.sistema.controller;

import com.libreria.sistema.model.dto.CompraDTO;
import com.libreria.sistema.service.CompraService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/compras")
public class CompraController {

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }

    @GetMapping("/nueva")
    public String nuevaCompra() {
        return "compras/ingreso";
    }

    @PostMapping("/api/procesar")
    @ResponseBody
    public ResponseEntity<?> procesarCompra(@RequestBody CompraDTO compraDTO) {
        try {
            compraService.registrarCompra(compraDTO);
            return ResponseEntity.ok(Map.of("mensaje", "Compra registrada y stock actualizado"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}