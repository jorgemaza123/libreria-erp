package com.libreria.sistema.controller;

import com.libreria.sistema.model.Proveedor;
import com.libreria.sistema.repository.ProveedorRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@RequestMapping("/proveedores")
@PreAuthorize("hasPermission(null, 'PROVEEDORES_VER')")
public class ProveedorController {

    private final ProveedorRepository proveedorRepository;

    public ProveedorController(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    @GetMapping
    public String lista(Model model) {
        model.addAttribute("proveedores", proveedorRepository.findByActivoTrue());
        return "proveedores/lista";
    }

    @GetMapping("/nuevo")
    @PreAuthorize("hasPermission(null, 'PROVEEDORES_CREAR')")
    public String nuevo(Model model) {
        model.addAttribute("proveedor", new Proveedor());
        model.addAttribute("titulo", "Registrar Proveedor");
        return "proveedores/formulario";
    }

    @PostMapping("/guardar")
    @PreAuthorize("hasPermission(null, 'PROVEEDORES_CREAR') or hasPermission(null, 'PROVEEDORES_EDITAR')")
    public String guardar(@ModelAttribute Proveedor proveedor, RedirectAttributes attributes) {
        try {
            if (proveedor.getId() == null) proveedor.setActivo(true);
            proveedorRepository.save(proveedor);
            attributes.addFlashAttribute("success", "Proveedor guardado con éxito.");
            return "redirect:/proveedores";
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "Error al guardar (¿RUC duplicado?)");
            return "redirect:/proveedores/nuevo";
        }
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("hasPermission(null, 'PROVEEDORES_EDITAR')")
    public String editar(@PathVariable Long id, Model model) {
        return proveedorRepository.findById(id).map(p -> {
            model.addAttribute("proveedor", p);
            model.addAttribute("titulo", "Editar Proveedor");
            return "proveedores/formulario";
        }).orElse("redirect:/proveedores");
    }

    @GetMapping("/eliminar/{id}")
    @PreAuthorize("hasPermission(null, 'PROVEEDORES_ELIMINAR')")
    public String eliminar(@PathVariable Long id, RedirectAttributes attributes) {
        // Borrado lógico
        proveedorRepository.findById(id).ifPresent(p -> {
            p.setActivo(false);
            proveedorRepository.save(p);
        });
        attributes.addFlashAttribute("success", "Proveedor eliminado.");
        return "redirect:/proveedores";
    }
}