package com.libreria.sistema.controller;

import com.libreria.sistema.model.Rol;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.repository.RolRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import com.libreria.sistema.util.PasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.security.access.prepost.PreAuthorize;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/usuarios")
@Slf4j
@PreAuthorize("hasPermission(null, 'USUARIOS_VER')")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioRepository usuarioRepository, RolRepository rolRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioRepository.findAll());
        return "usuarios/lista";
    }

    @GetMapping("/nuevo")
    @PreAuthorize("hasPermission(null, 'USUARIOS_CREAR')")
    public String nuevo(Model model) {
        Usuario usuario = new Usuario();
        usuario.setActivo(true);
        model.addAttribute("usuario", usuario);
        model.addAttribute("rolesDisponibles", rolRepository.findAll());
        model.addAttribute("titulo", "Nuevo Usuario");
        return "usuarios/formulario";
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("hasPermission(null, 'USUARIOS_EDITAR')")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes attr) {
        return usuarioRepository.findById(id).map(u -> {
            model.addAttribute("usuario", u);
            model.addAttribute("rolesDisponibles", rolRepository.findAll());
            model.addAttribute("titulo", "Editar Usuario");
            return "usuarios/formulario";
        }).orElseGet(() -> {
            attr.addFlashAttribute("error", "Usuario no encontrado");
            return "redirect:/usuarios";
        });
    }

    @PostMapping("/guardar")
    @PreAuthorize("hasPermission(null, 'USUARIOS_CREAR') or hasPermission(null, 'USUARIOS_EDITAR')")
    public String guardar(@ModelAttribute Usuario usuario,
                          @RequestParam(required = false) List<Long> rolesIds,
                          RedirectAttributes attr) {
        try {
            // 1. Manejo de Roles
            if (rolesIds != null) {
                List<Rol> rolesSeleccionados = rolRepository.findAllById(rolesIds);
                usuario.setRoles(new HashSet<>(rolesSeleccionados));
            }

            // 2. Manejo de Contraseña
            if (usuario.getId() != null) {
                // Edición: Si la contraseña viene vacía, mantenemos la anterior
                Usuario actual = usuarioRepository.findById(usuario.getId()).orElse(null);
                if (actual != null) {
                    if (usuario.getPassword() == null || usuario.getPassword().isEmpty()) {
                        usuario.setPassword(actual.getPassword());
                    } else {
                        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
                    }
                }
            } else {
                // Creación: Contraseña obligatoria y fuerte
                if (usuario.getPassword() == null || usuario.getPassword().isEmpty()) {
                    attr.addFlashAttribute("error", "La contraseña es obligatoria para nuevos usuarios");
                    return "redirect:/usuarios/nuevo";
                }

                // Validar fortaleza de contraseña
                String validationError = PasswordValidator.validateAndGetMessage(usuario.getPassword());
                if (validationError != null) {
                    attr.addFlashAttribute("error", validationError);
                    return "redirect:/usuarios/nuevo";
                }

                usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
            }

            // Si se está cambiando la contraseña en una edición, también validar
            if (usuario.getId() != null && usuario.getPassword() != null && !usuario.getPassword().isEmpty()) {
                String validationError = PasswordValidator.validateAndGetMessage(usuario.getPassword());
                if (validationError != null) {
                    attr.addFlashAttribute("error", validationError);
                    return "redirect:/usuarios/editar/" + usuario.getId();
                }
            }

            usuarioRepository.save(usuario);
            attr.addFlashAttribute("success", "Usuario guardado correctamente");
            return "redirect:/usuarios";

        } catch (Exception e) {
            attr.addFlashAttribute("error", "Error al guardar: " + e.getMessage());
            return "redirect:/usuarios";
        }
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes attr) {
        try {
            // No borramos físicamente para mantener integridad de ventas, solo desactivamos
            usuarioRepository.findById(id).ifPresent(u -> {
                u.setActivo(false); // Baja lógica
                usuarioRepository.save(u);
            });
            attr.addFlashAttribute("success", "Usuario desactivado correctamente");
        } catch (Exception e) {
            attr.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/usuarios";
    }
}