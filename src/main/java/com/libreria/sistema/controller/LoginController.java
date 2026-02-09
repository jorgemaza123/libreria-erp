package com.libreria.sistema.controller;

import com.libreria.sistema.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador para autenticación y gestión de contraseñas.
 *
 * Funcionalidades:
 * - Login/Logout
 * - Cambio de contraseña inicial obligatorio
 * - Recuperación de contraseña (sin costo - pregunta de seguridad)
 * - Reset de emergencia para administradores
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final UsuarioService usuarioService;

    /**
     * Página de login.
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // =====================================================
    //  CAMBIO DE CONTRASEÑA INICIAL (OBLIGATORIO)
    // =====================================================

    /**
     * Vista para cambiar la contraseña inicial.
     * El usuario DEBE cambiar su contraseña antes de usar el sistema.
     */
    @GetMapping("/usuarios/cambiar-password-inicial")
    public String vistaCambiarPasswordInicial(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "redirect:/login";
        }

        String username = auth.getName();
        model.addAttribute("username", username);

        // Opciones de preguntas de seguridad predefinidas
        model.addAttribute("preguntasSeguridad", new String[]{
            "¿Cuál es el nombre de tu primera mascota?",
            "¿En qué ciudad naciste?",
            "¿Cuál es tu comida favorita?",
            "¿Cuál es el nombre de tu mejor amigo de la infancia?",
            "¿Cuál es tu número de teléfono favorito?"
        });

        return "usuarios/cambiar-password-inicial";
    }

    /**
     * Procesa el cambio de contraseña inicial.
     */
    @PostMapping("/usuarios/guardar-password-inicial")
    public String guardarPasswordInicial(
            @RequestParam String nuevaPassword,
            @RequestParam String confirmarPassword,
            @RequestParam(required = false) String preguntaSeguridad,
            @RequestParam(required = false) String respuestaSeguridad,
            RedirectAttributes redirectAttributes) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        String username = auth.getName();

        try {
            // Validar que las contraseñas coincidan
            if (!nuevaPassword.equals(confirmarPassword)) {
                redirectAttributes.addFlashAttribute("error", "Las contraseñas no coinciden.");
                return "redirect:/usuarios/cambiar-password-inicial";
            }

            // Validar longitud mínima
            if (nuevaPassword.length() < 8) {
                redirectAttributes.addFlashAttribute("error", "La contraseña debe tener al menos 8 caracteres.");
                return "redirect:/usuarios/cambiar-password-inicial";
            }

            // Cambiar contraseña
            boolean exito = usuarioService.cambiarPasswordInicial(
                    username, nuevaPassword, preguntaSeguridad, respuestaSeguridad);

            if (exito) {
                redirectAttributes.addFlashAttribute("success",
                        "Contraseña actualizada correctamente. Ya puede usar el sistema.");
                return "redirect:/";
            } else {
                redirectAttributes.addFlashAttribute("error", "Error al cambiar la contraseña.");
                return "redirect:/usuarios/cambiar-password-inicial";
            }

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/usuarios/cambiar-password-inicial";
        } catch (Exception e) {
            log.error("Error al cambiar contraseña inicial: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error interno. Intente nuevamente.");
            return "redirect:/usuarios/cambiar-password-inicial";
        }
    }

    // =====================================================
    //  RECUPERACIÓN DE CONTRASEÑA (SIN COSTO)
    // =====================================================

    /**
     * Vista para solicitar recuperación de contraseña.
     */
    @GetMapping("/usuarios/recuperar-password")
    public String vistaRecuperarPassword() {
        return "usuarios/recuperar-password";
    }

    /**
     * Paso 1: Verifica el usuario y muestra la pregunta de seguridad.
     */
    @PostMapping("/usuarios/validar-recuperacion")
    public String validarRecuperacion(
            @RequestParam String username,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Obtener pregunta de seguridad
        String pregunta = usuarioService.obtenerPreguntaSeguridad(username);

        if (pregunta == null || pregunta.isBlank()) {
            // No tiene pregunta de seguridad configurada
            redirectAttributes.addFlashAttribute("error",
                    "Este usuario no tiene pregunta de seguridad configurada. " +
                    "Use el código maestro de recuperación o contacte al administrador.");
            redirectAttributes.addFlashAttribute("mostrarEmergencia", true);
            redirectAttributes.addFlashAttribute("usernameRecuperacion", username);
            return "redirect:/usuarios/recuperar-password";
        }

        // Mostrar formulario con la pregunta
        model.addAttribute("username", username);
        model.addAttribute("preguntaSeguridad", pregunta);
        return "usuarios/responder-pregunta";
    }

    /**
     * Paso 2: Valida la respuesta y permite establecer nueva contraseña.
     */
    @PostMapping("/usuarios/restablecer-password")
    public String restablecerPassword(
            @RequestParam String username,
            @RequestParam String respuestaSeguridad,
            @RequestParam String nuevaPassword,
            @RequestParam String confirmarPassword,
            RedirectAttributes redirectAttributes) {

        try {
            // Validar contraseñas
            if (!nuevaPassword.equals(confirmarPassword)) {
                redirectAttributes.addFlashAttribute("error", "Las contraseñas no coinciden.");
                return "redirect:/usuarios/recuperar-password";
            }

            if (nuevaPassword.length() < 8) {
                redirectAttributes.addFlashAttribute("error", "La contraseña debe tener al menos 8 caracteres.");
                return "redirect:/usuarios/recuperar-password";
            }

            // Intentar restablecer con pregunta de seguridad
            boolean exito = usuarioService.restablecerPasswordConPregunta(
                    username, respuestaSeguridad, nuevaPassword);

            if (exito) {
                redirectAttributes.addFlashAttribute("success",
                        "Contraseña restablecida correctamente. Ahora puede iniciar sesión.");
                return "redirect:/login";
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "Respuesta de seguridad incorrecta. Intente nuevamente.");
                return "redirect:/usuarios/recuperar-password";
            }

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/usuarios/recuperar-password";
        } catch (Exception e) {
            log.error("Error al restablecer contraseña: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error interno. Intente nuevamente.");
            return "redirect:/usuarios/recuperar-password";
        }
    }

    /**
     * RECUPERACIÓN DE EMERGENCIA: Restablece contraseña del admin.
     * Solo funciona con el código maestro del sistema.
     */
    @PostMapping("/usuarios/recuperar-emergencia")
    public String recuperarEmergencia(
            @RequestParam String codigoMaestro,
            RedirectAttributes redirectAttributes) {

        try {
            boolean exito = usuarioService.restablecerPasswordEmergencia(codigoMaestro);

            if (exito) {
                redirectAttributes.addFlashAttribute("success",
                        "Contraseña del administrador restablecida a: admin123. " +
                        "Deberá cambiarla al iniciar sesión.");
                return "redirect:/login";
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "Código maestro incorrecto. Verifique el código proporcionado en la instalación.");
                return "redirect:/usuarios/recuperar-password";
            }

        } catch (Exception e) {
            log.error("Error en recuperación de emergencia: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error interno.");
            return "redirect:/usuarios/recuperar-password";
        }
    }
}
