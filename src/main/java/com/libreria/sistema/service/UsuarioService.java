package com.libreria.sistema.service;

import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.repository.UsuarioRepository;
import com.libreria.sistema.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio de usuarios con soporte para:
 * - Autenticación Spring Security
 * - Bloqueo de cuenta por intentos fallidos
 * - Desbloqueo automático tras tiempo de expiración
 */
@Service
@Slf4j
public class UsuarioService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Carga usuario para Spring Security.
     * MEJORADO: Verifica bloqueo de cuenta y auto-desbloqueo por timeout.
     */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        // Verificar si el usuario está activo (bloqueo administrativo)
        if (!usuario.isActivo()) {
            log.warn("Intento de login con usuario desactivado: {}", username);
            throw new UsernameNotFoundException("El usuario no está activo");
        }

        // Verificar bloqueo por intentos fallidos (Manejo seguro de Boolean nulo)
        if (Boolean.TRUE.equals(usuario.getCuentaBloqueada())) {
            // Verificar si el bloqueo ha expirado
            if (usuario.haExpiradoBloqueo(Constants.ACCOUNT_LOCK_DURATION_MINUTES)) {
                log.info("Desbloqueando cuenta por expiración de tiempo: {}", username);
                usuario.desbloquearCuenta();
                usuarioRepository.save(usuario);
            } else {
                log.warn("Intento de login con cuenta bloqueada: {}", username);
                throw new UsernameNotFoundException(
                        "Cuenta bloqueada por múltiples intentos fallidos. Intente en " +
                                Constants.ACCOUNT_LOCK_DURATION_MINUTES + " minutos.");
            }
        }

        List<GrantedAuthority> authorities = usuario.getRoles().stream()
                .map(rol -> new SimpleGrantedAuthority(rol.getNombre()))
                .collect(Collectors.toList());

        return new User(usuario.getUsername(), usuario.getPassword(), authorities);
    }

    /**
     * Registra un intento de login fallido.
     * Bloquea la cuenta si se exceden los intentos permitidos.
     *
     * @param username Nombre de usuario
     */
    @Transactional
    public void registrarIntentoFallido(String username) {
        Optional<Usuario> optUsuario = usuarioRepository.findByUsername(username);

        if (optUsuario.isEmpty()) {
            log.warn("Intento de login fallido para usuario inexistente: {}", username);
            return;
        }

        Usuario usuario = optUsuario.get();

        // Si la cuenta ya está bloqueada, no incrementar más
        if (Boolean.TRUE.equals(usuario.getCuentaBloqueada())) {
            log.debug("Cuenta ya bloqueada, ignorando intento adicional: {}", username);
            return;
        }

        usuario.incrementarIntentosFallidos();

        // Manejo seguro de Integer nulo
        int intentos = usuario.getIntentosFallidos() != null ? usuario.getIntentosFallidos() : 0;

        if (intentos >= Constants.MAX_LOGIN_ATTEMPTS) {
            usuario.bloquearCuenta();
            log.warn("CUENTA BLOQUEADA por {} intentos fallidos: {}",
                    Constants.MAX_LOGIN_ATTEMPTS, username);
        } else {
            log.info("Intento fallido #{} para usuario: {}", intentos, username);
        }

        usuarioRepository.save(usuario);
    }

    /**
     * Registra un login exitoso.
     * Resetea el contador de intentos fallidos.
     *
     * @param username Nombre de usuario
     */
    @Transactional
    public void registrarLoginExitoso(String username) {
        usuarioRepository.findByUsername(username).ifPresent(usuario -> {
            int intentos = usuario.getIntentosFallidos() != null ? usuario.getIntentosFallidos() : 0;
            boolean bloqueada = Boolean.TRUE.equals(usuario.getCuentaBloqueada());

            if (intentos > 0 || bloqueada) {
                log.info("Login exitoso, reseteando intentos fallidos para: {}", username);
            }
            usuario.resetearIntentosFallidos();
            usuarioRepository.save(usuario);
        });
    }

    /**
     * Desbloquea manualmente una cuenta (para administradores).
     *
     * @param username Nombre de usuario a desbloquear
     * @return true si se desbloqueó, false si no se encontró
     */
    @Transactional
    public boolean desbloquearCuenta(String username) {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> {
                    usuario.desbloquearCuenta();
                    usuarioRepository.save(usuario);
                    log.info("Cuenta desbloqueada manualmente: {}", username);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Verifica si una cuenta está bloqueada.
     *
     * @param username Nombre de usuario
     * @return true si está bloqueada
     */
    @Transactional(readOnly = true)
    public boolean estaCuentaBloqueada(String username) {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> {
                    if (!Boolean.TRUE.equals(usuario.getCuentaBloqueada())) {
                        return false;
                    }
                    // Verificar si el bloqueo ha expirado
                    return !usuario.haExpiradoBloqueo(Constants.ACCOUNT_LOCK_DURATION_MINUTES);
                })
                .orElse(false);
    }

    /**
     * Obtiene el número de intentos fallidos de un usuario.
     *
     * @param username Nombre de usuario
     * @return Número de intentos fallidos, 0 si no existe
     */
    @Transactional(readOnly = true)
    public int obtenerIntentosFallidos(String username) {
        return usuarioRepository.findByUsername(username)
                .map(u -> u.getIntentosFallidos() != null ? u.getIntentosFallidos() : 0)
                .orElse(0);
    }

    /**
     * Busca un usuario por username.
     */
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }
}