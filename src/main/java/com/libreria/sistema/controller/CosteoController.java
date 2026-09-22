package com.libreria.sistema.controller;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.CosteoPreviewRequestDTO;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.service.CosteoCalculatorService;
import com.libreria.sistema.service.CosteoSugerenciaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/costeo/api")
@PreAuthorize("hasPermission(null, 'INVENTARIO_VER') or hasPermission(null, 'COMPRAS_VER')")
public class CosteoController {

    private final CosteoCalculatorService costeoCalculatorService;
    private final CosteoSugerenciaService costeoSugerenciaService;
    private final ProductoRepository productoRepository;

    public CosteoController(CosteoCalculatorService costeoCalculatorService,
                            CosteoSugerenciaService costeoSugerenciaService,
                            ProductoRepository productoRepository) {
        this.costeoCalculatorService = costeoCalculatorService;
        this.costeoSugerenciaService = costeoSugerenciaService;
        this.productoRepository = productoRepository;
    }

    @PostMapping("/simular")
    public ResponseEntity<?> simular(@RequestBody CosteoPreviewRequestDTO request) {
        Producto producto = null;
        if (request.getProductoId() != null) {
            producto = productoRepository.findById(request.getProductoId()).orElse(null);
        }
        return ResponseEntity.ok(costeoCalculatorService.simular(request, producto));
    }

    @GetMapping("/sugerencia-indirecto")
    public ResponseEntity<?> sugerenciaIndirecto(@RequestParam(required = false) Long proveedorId) {
        return ResponseEntity.ok(Map.of(
                "factorIndirectoSugeridoPct",
                proveedorId != null
                        ? costeoSugerenciaService.obtenerFactorSugeridoProveedor(proveedorId)
                        : costeoSugerenciaService.obtenerFactorSugeridoGlobal()
        ));
    }
}
