package com.libreria.sistema.controller;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.service.ProductoService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'INVENTARIO_VER')")
    public String listar(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
        return "productos/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        Producto p = new Producto();
        p.setActivo(true);
        p.setStockMinimo(5);
        p.setUnidadMedida("UNIDAD"); // Valor por defecto
        
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
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String guardar(@ModelAttribute Producto producto, 
                          @RequestParam("file") MultipartFile imagen, 
                          RedirectAttributes attributes) {
        try {
            // 1. MANEJO DE IMAGEN
            if (!imagen.isEmpty()) {
                // Crear carpeta uploads si no existe
                Path rootPath = Paths.get("uploads").toAbsolutePath();
                if (!Files.exists(rootPath)) {
                    Files.createDirectories(rootPath);
                }

                // Generar nombre único y guardar
                String nombreUnico = UUID.randomUUID().toString() + "_" + imagen.getOriginalFilename();
                Files.copy(imagen.getInputStream(), rootPath.resolve(nombreUnico));
                producto.setImagen(nombreUnico);
            } else {
                // Si es EDICIÓN y no subió foto nueva, mantener la anterior
                if (producto.getId() != null) {
                    Producto pDb = productoService.obtenerPorId(producto.getId()).orElse(null);
                    if (pDb != null) {
                        producto.setImagen(pDb.getImagen());
                        // Mantener fechas de auditoría si el formulario no las envía
                        if(producto.getFechaCreacion() == null) {
                            producto.setFechaCreacion(pDb.getFechaCreacion());
                        }
                    }
                }
            }

            // 2. VALIDACIONES DE NEGOCIO
            if (producto.getStockMinimo() == null) producto.setStockMinimo(0);
            if (producto.getId() == null) producto.setActivo(true);
            
            // Convertir a Mayúsculas para estandarizar
            if(producto.getNombre() != null) producto.setNombre(producto.getNombre().toUpperCase());
            if(producto.getMarca() != null) producto.setMarca(producto.getMarca().toUpperCase());

            // 3. GUARDAR
            productoService.guardar(producto);
            
            attributes.addFlashAttribute("success", "Producto guardado correctamente");
            return "redirect:/productos";

        } catch (DataIntegrityViolationException e) {
            // ERROR DE DUPLICADOS (Código Barras o SKU repetido)
            System.err.println("ERROR SQL: " + e.getRootCause().getMessage());
            attributes.addFlashAttribute("error", "Error: El Código de Barras o Código Interno ya existe en otro producto.");
            return "redirect:/productos/nuevo"; // O volver al formulario
            
        } catch (IOException e) {
            e.printStackTrace();
            attributes.addFlashAttribute("error", "Error crítico al subir la imagen: " + e.getMessage());
            return "redirect:/productos/nuevo";
            
        } catch (Exception e) {
            e.printStackTrace();
            attributes.addFlashAttribute("error", "Error inesperado al guardar: " + e.getMessage());
            return "redirect:/productos/nuevo"; 
        }
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes attributes) {
        try {
            productoService.eliminar(id);
            attributes.addFlashAttribute("success", "Producto eliminado/desactivado correctamente");
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "No se puede eliminar: " + e.getMessage());
        }
        return "redirect:/productos";
    }
}