package com.libreria.sistema.controller;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.service.ProductoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
        return "productos/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        Producto p = new Producto();
        p.setActivo(true);
        p.setStockMinimo(5); // Valor sugerido por defecto
        
        model.addAttribute("producto", p);
        model.addAttribute("titulo", "Nuevo Producto");
        return "productos/formulario";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes attributes) {
        return productoService.obtenerPorId(id).map(producto -> {
            model.addAttribute("producto", producto);
            model.addAttribute("titulo", "Editar Producto");
            return "productos/formulario";
        }).orElseGet(() -> {
            attributes.addFlashAttribute("error", "Producto no encontrado");
            return "redirect:/productos";
        });
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Producto producto, RedirectAttributes attributes) {
        try {
            // Asegurar que el stock mínimo tenga valor (si viene vacío del form)
            if (producto.getStockMinimo() == null) producto.setStockMinimo(0);
            if (producto.getId() == null) producto.setActivo(true);

            productoService.guardar(producto);
            attributes.addFlashAttribute("success", "Producto guardado correctamente");
            return "redirect:/productos";
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "Error al guardar: " + e.getMessage());
            return "redirect:/productos/nuevo"; 
        }
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes attributes) {
        try {
            productoService.eliminar(id);
            attributes.addFlashAttribute("success", "Producto desactivado correctamente");
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "No se puede eliminar: " + e.getMessage());
        }
        return "redirect:/productos";
    }
}