package com.libreria.sistema.controller;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.service.ProductoService;
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
    public String listar(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
        return "productos/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        Producto p = new Producto();
        p.setActivo(true);
        p.setStockMinimo(5); 
        
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
    public String guardar(@ModelAttribute Producto producto, 
                          @RequestParam("file") MultipartFile imagen, // Recibimos el archivo
                          RedirectAttributes attributes) {
        try {
            // LÓGICA DE IMAGEN
            if (!imagen.isEmpty()) {
                // Crear nombre único
                String nombreUnico = UUID.randomUUID().toString() + "_" + imagen.getOriginalFilename();
                Path rootPath = Paths.get("uploads").toAbsolutePath();
                
                // Crear carpeta si no existe
                if (!Files.exists(rootPath)) {
                    Files.createDirectories(rootPath);
                }

                // Guardar en disco
                Files.copy(imagen.getInputStream(), rootPath.resolve(nombreUnico));
                producto.setImagen(nombreUnico);
            } else {
                // Si no subió imagen nueva pero ya existía una (Edición), tratamos de mantenerla.
                // Como 'producto' viene del formulario, el campo imagen podría ser null si no lo pusiste en un hidden.
                // Buscamos el original en BD para no perder la foto vieja.
                if (producto.getId() != null) {
                    Producto pDb = productoService.obtenerPorId(producto.getId()).orElse(null);
                    if (pDb != null) {
                        producto.setImagen(pDb.getImagen());
                        // También recuperamos fechas si es necesario, aunque JPA suele manejarlo
                        producto.setFechaCreacion(pDb.getFechaCreacion()); 
                    }
                }
            }

            // Lógica original de validación
            if (producto.getStockMinimo() == null) producto.setStockMinimo(0);
            if (producto.getId() == null) producto.setActivo(true);

            // Guardar usando tu SERVICE
            productoService.guardar(producto);
            
            attributes.addFlashAttribute("success", "Producto guardado correctamente");
            return "redirect:/productos";
        } catch (IOException e) {
            e.printStackTrace();
            attributes.addFlashAttribute("error", "Error al subir imagen: " + e.getMessage());
            return "redirect:/productos/nuevo";
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "Error al guardar: " + e.getMessage());
            return "redirect:/productos/nuevo"; 
        }
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes attributes) {
        try {
            // Asumiendo que tu servicio hace borrado lógico (setActivo false) o físico
            // Si tu servicio hace borrado físico y quieres lógico, cámbialo aquí o en el servicio.
            // Por ahora uso tu método original:
            productoService.eliminar(id);
            attributes.addFlashAttribute("success", "Producto procesado correctamente");
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "No se puede eliminar: " + e.getMessage());
        }
        return "redirect:/productos";
    }
}