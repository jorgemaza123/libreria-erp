package com.libreria.sistema.config;

import com.libreria.sistema.service.UsuarioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Listener de eventos de autenticación de Spring Security.
 *
 * Captura eventos de login exitoso y fallido para:
 * - Registrar intentos fallidos
 * - Bloquear cuentas tras múltiples fallos
 * - Resetear contador tras login exitoso
 */
@Component
@Slf4j
public class AuthenticationEventListener {

    private final UsuarioService usuarioService;

    public AuthenticationEventListener(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Captura evento de login exitoso.
     * Resetea el contador de intentos fallidos.
     */
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        log.info("Login exitoso para usuario: {}", username);
        usuarioService.registrarLoginExitoso(username);
    }

    /**
     * Captura evento de login fallido por credenciales incorrectas.
     * Incrementa el contador de intentos y bloquea si corresponde.
     */
    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = (String) event.getAuthentication().getPrincipal();
        log.warn("Login fallido para usuario: {}", username);
        usuarioService.registrarIntentoFallido(username);
    }
}
