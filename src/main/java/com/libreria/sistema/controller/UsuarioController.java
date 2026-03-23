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
        if (!model.containsAttribute("usuario")) {
            Usuario usuario = new Usuario();
            usuario.setActivo(true);
            model.addAttribute("usuario", usuario);
        }
        if (!model.containsAttribute("rolesIdsSeleccionados")) {
            model.addAttribute("rolesIdsSeleccionados", List.of());
        }
        model.addAttribute("rolesDisponibles", rolRepository.findAll());
        model.addAttribute("titulo", "Nuevo Usuario");
        return "usuarios/formulario";
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("hasPermission(null, 'USUARIOS_EDITAR')")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes attr) {
        return usuarioRepository.findById(id).map(u -> {
            if (!model.containsAttribute("usuario")) {
                model.addAttribute("usuario", u);
            }
            if (!model.containsAttribute("rolesIdsSeleccionados")) {
                model.addAttribute("rolesIdsSeleccionados",
                        u.getRoles().stream().map(Rol::getId).toList());
            }
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
                          Model model,
                          RedirectAttributes attr) {
        try {
            // 1. Manejo de Roles
            List<Long> rolesSeleccionadosIds = rolesIds != null ? rolesIds : List.of();
            List<Rol> rolesSeleccionados = rolRepository.findAllById(rolesSeleccionadosIds);
            usuario.setRoles(new HashSet<>(rolesSeleccionados));

            boolean esNuevo = usuario.getId() == null;
            String passwordPlano = usuario.getPassword();

            if (usuario.getUsername() == null || usuario.getUsername().isBlank()) {
                return volverAlFormulario(model, usuario, rolesSeleccionadosIds,
                        "El username es obligatorio.");
            }

            if (existeOtroUsuarioConUsername(usuario.getUsername(), usuario.getId())) {
                return volverAlFormulario(model, usuario, rolesSeleccionadosIds,
                        "Ya existe un usuario con ese username.");
            }

            // 2. Manejo de Contraseña
            if (!esNuevo) {
                // Edición: Si la contraseña viene vacía, mantenemos la anterior
                Usuario actual = usuarioRepository.findById(usuario.getId()).orElse(null);
                if (actual == null) {
                    attr.addFlashAttribute("error", "Usuario no encontrado");
                    return "redirect:/usuarios";
                }

                usuario.setRole(actual.getRole());
                usuario.setIntentosFallidos(actual.getIntentosFallidos());
                usuario.setFechaBloqueo(actual.getFechaBloqueo());
                usuario.setCuentaBloqueada(actual.getCuentaBloqueada());
                usuario.setUltimoIntentoLogin(actual.getUltimoIntentoLogin());
                usuario.setUltimoLoginExitoso(actual.getUltimoLoginExitoso());
                usuario.setPasswordChanged(actual.getPasswordChanged());
                usuario.setEmail(actual.getEmail());
                usuario.setTokenRecuperacion(actual.getTokenRecuperacion());
                usuario.setTokenExpiracion(actual.getTokenExpiracion());
                usuario.setPreguntaSeguridad(actual.getPreguntaSeguridad());
                usuario.setRespuestaSeguridad(actual.getRespuestaSeguridad());

                if (passwordPlano == null || passwordPlano.isEmpty()) {
                    usuario.setPassword(actual.getPassword());
                } else {
                    String validationError = PasswordValidator.validateAndGetMessage(passwordPlano);
                    if (validationError != null) {
                        usuario.setPassword("");
                        return volverAlFormulario(model, usuario, rolesSeleccionadosIds, validationError);
                    }
                    usuario.setPassword(passwordEncoder.encode(passwordPlano));
                }
            } else {
                // Creación: Contraseña obligatoria y fuerte
                if (passwordPlano == null || passwordPlano.isEmpty()) {
                    return volverAlFormulario(model, usuario, rolesSeleccionadosIds,
                            "La contraseña es obligatoria para nuevos usuarios.");
                }

                // Validar fortaleza de contraseña
                String validationError = PasswordValidator.validateAndGetMessage(passwordPlano);
                if (validationError != null) {
                    usuario.setPassword("");
                    return volverAlFormulario(model, usuario, rolesSeleccionadosIds, validationError);
                }

                usuario.setPassword(passwordEncoder.encode(passwordPlano));
            }

            usuarioRepository.save(usuario);
            attr.addFlashAttribute("success", "Usuario guardado correctamente");
            return "redirect:/usuarios";

        } catch (Exception e) {
            log.error("Error al guardar usuario {}", usuario.getUsername(), e);
            usuario.setPassword("");
            return volverAlFormulario(model, usuario, rolesIds != null ? rolesIds : List.of(),
                    "Error al guardar: " + e.getMessage());
        }
    }

    private boolean existeOtroUsuarioConUsername(String username, Long usuarioId) {
        return usuarioRepository.findByUsernameIgnoreCase(username.trim())
                .filter(u -> usuarioId == null || !u.getId().equals(usuarioId))
                .isPresent();
    }

    private String volverAlFormulario(Model model, Usuario usuario, List<Long> rolesIds, String error) {
        usuario.setPassword("");
        model.addAttribute("usuario", usuario);
        model.addAttribute("rolesIdsSeleccionados", rolesIds != null ? rolesIds : List.of());
        model.addAttribute("rolesDisponibles", rolRepository.findAll());
        model.addAttribute("titulo", usuario.getId() == null ? "Nuevo Usuario" : "Editar Usuario");
        model.addAttribute("error", error);
        return "usuarios/formulario";
    }

    @GetMapping("/eliminar/{id}")
    @PreAuthorize("hasPermission(null, 'USUARIOS_ELIMINAR')")
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
