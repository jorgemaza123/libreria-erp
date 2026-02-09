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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Carga usuario para Spring Security.
     * MEJORADO: Verifica bloqueo de cuenta y auto-desbloqueo por timeout.
     */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Búsqueda case-insensitive para soportar teclados móviles que auto-capitalizan
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(username)
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
        Optional<Usuario> optUsuario = usuarioRepository.findByUsernameIgnoreCase(username);

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
        usuarioRepository.findByUsernameIgnoreCase(username).ifPresent(usuario -> {
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
        return usuarioRepository.findByUsernameIgnoreCase(username)
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
        return usuarioRepository.findByUsernameIgnoreCase(username)
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
        return usuarioRepository.findByUsernameIgnoreCase(username)
                .map(u -> u.getIntentosFallidos() != null ? u.getIntentosFallidos() : 0)
                .orElse(0);
    }

    /**
     * Busca un usuario por username.
     */
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsernameIgnoreCase(username);
    }

    // =====================================================
    //  MÉTODOS PARA CAMBIO DE CONTRASEÑA OBLIGATORIO
    // =====================================================

    /**
     * Verifica si el usuario necesita cambiar su contraseña inicial.
     *
     * @param username Nombre de usuario
     * @return true si debe cambiar la contraseña
     */
    @Transactional(readOnly = true)
    public boolean requiereCambioPassword(String username) {
        return usuarioRepository.findByUsernameIgnoreCase(username)
                .map(Usuario::requiereCambioPassword)
                .orElse(false);
    }

    /**
     * Cambia la contraseña del usuario y marca que ya fue cambiada.
     *
     * @param username Nombre de usuario
     * @param nuevaPassword Nueva contraseña (sin encriptar)
     * @param preguntaSeguridad Pregunta de seguridad (opcional)
     * @param respuestaSeguridad Respuesta a la pregunta (opcional)
     * @return true si se cambió exitosamente
     */
    @Transactional
    public boolean cambiarPasswordInicial(String username, String nuevaPassword,
                                          String preguntaSeguridad, String respuestaSeguridad) {
        Optional<Usuario> optUsuario = usuarioRepository.findByUsernameIgnoreCase(username);

        if (optUsuario.isEmpty()) {
            log.warn("Intento de cambio de contraseña para usuario inexistente: {}", username);
            return false;
        }

        Usuario usuario = optUsuario.get();

        // Validar longitud mínima
        if (nuevaPassword == null || nuevaPassword.length() < Constants.MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + Constants.MIN_PASSWORD_LENGTH + " caracteres.");
        }

        // Encriptar y guardar nueva contraseña
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuario.marcarPasswordCambiado();

        // Guardar pregunta y respuesta de seguridad si se proporcionan
        if (preguntaSeguridad != null && !preguntaSeguridad.isBlank()) {
            usuario.setPreguntaSeguridad(preguntaSeguridad);
        }
        if (respuestaSeguridad != null && !respuestaSeguridad.isBlank()) {
            // La respuesta se guarda en hash para mayor seguridad
            usuario.setRespuestaSeguridad(passwordEncoder.encode(respuestaSeguridad.toLowerCase().trim()));
        }

        usuarioRepository.save(usuario);
        log.info("Contraseña inicial cambiada exitosamente para: {}", username);
        return true;
    }

    /**
     * Cambia la contraseña del usuario (para usuarios que ya cambiaron su contraseña inicial).
     *
     * @param username Nombre de usuario
     * @param passwordActual Contraseña actual (para verificación)
     * @param nuevaPassword Nueva contraseña
     * @return true si se cambió exitosamente
     */
    @Transactional
    public boolean cambiarPassword(String username, String passwordActual, String nuevaPassword) {
        Optional<Usuario> optUsuario = usuarioRepository.findByUsernameIgnoreCase(username);

        if (optUsuario.isEmpty()) {
            return false;
        }

        Usuario usuario = optUsuario.get();

        // Verificar contraseña actual
        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            log.warn("Intento de cambio de contraseña con clave incorrecta: {}", username);
            throw new IllegalArgumentException("La contraseña actual es incorrecta.");
        }

        // Validar nueva contraseña
        if (nuevaPassword == null || nuevaPassword.length() < Constants.MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "La nueva contraseña debe tener al menos " + Constants.MIN_PASSWORD_LENGTH + " caracteres.");
        }

        // Encriptar y guardar
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);

        log.info("Contraseña cambiada exitosamente para: {}", username);
        return true;
    }

    // =====================================================
    //  MÉTODOS PARA RECUPERACIÓN DE CONTRASEÑA
    // =====================================================

    /**
     * Genera un token de recuperación de contraseña.
     * El token expira en 1 hora.
     *
     * @param username Nombre de usuario
     * @return Token generado o null si el usuario no existe
     */
    @Transactional
    public String generarTokenRecuperacion(String username) {
        Optional<Usuario> optUsuario = usuarioRepository.findByUsernameIgnoreCase(username);

        if (optUsuario.isEmpty()) {
            log.warn("Solicitud de recuperación para usuario inexistente: {}", username);
            return null;
        }

        Usuario usuario = optUsuario.get();

        // Generar token único
        String token = UUID.randomUUID().toString();
        usuario.setTokenRecuperacion(token);
        usuario.setTokenExpiracion(LocalDateTime.now().plusHours(1)); // Expira en 1 hora

        usuarioRepository.save(usuario);
        log.info("Token de recuperación generado para: {}", username);

        return token;
    }

    /**
     * Valida si un token de recuperación es válido.
     *
     * @param username Nombre de usuario
     * @param token Token a validar
     * @return true si el token es válido
     */
    @Transactional(readOnly = true)
    public boolean validarTokenRecuperacion(String username, String token) {
        return usuarioRepository.findByUsernameIgnoreCase(username)
                .filter(u -> token.equals(u.getTokenRecuperacion()))
                .filter(Usuario::tieneTokenValido)
                .isPresent();
    }

    /**
     * Valida la respuesta de seguridad para recuperación de contraseña.
     *
     * @param username Nombre de usuario
     * @param respuesta Respuesta proporcionada
     * @return true si la respuesta es correcta
     */
    @Transactional(readOnly = true)
    public boolean validarRespuestaSeguridad(String username, String respuesta) {
        Optional<Usuario> optUsuario = usuarioRepository.findByUsernameIgnoreCase(username);

        if (optUsuario.isEmpty()) {
            return false;
        }

        Usuario usuario = optUsuario.get();

        // Verificar que tenga pregunta de seguridad configurada
        if (usuario.getRespuestaSeguridad() == null || usuario.getRespuestaSeguridad().isBlank()) {
            return false;
        }

        // Comparar respuesta (normalizada a minúsculas)
        return passwordEncoder.matches(respuesta.toLowerCase().trim(), usuario.getRespuestaSeguridad());
    }

    /**
     * Obtiene la pregunta de seguridad del usuario.
     *
     * @param username Nombre de usuario
     * @return Pregunta de seguridad o null si no tiene
     */
    @Transactional(readOnly = true)
    public String obtenerPreguntaSeguridad(String username) {
        return usuarioRepository.findByUsernameIgnoreCase(username)
                .map(Usuario::getPreguntaSeguridad)
                .orElse(null);
    }

    /**
     * Restablece la contraseña usando el token de recuperación.
     *
     * @param username Nombre de usuario
     * @param token Token de recuperación
     * @param nuevaPassword Nueva contraseña
     * @return true si se restableció exitosamente
     */
    @Transactional
    public boolean restablecerPasswordConToken(String username, String token, String nuevaPassword) {
        if (!validarTokenRecuperacion(username, token)) {
            log.warn("Token de recuperación inválido o expirado para: {}", username);
            return false;
        }

        Optional<Usuario> optUsuario = usuarioRepository.findByUsernameIgnoreCase(username);
        if (optUsuario.isEmpty()) {
            return false;
        }

        Usuario usuario = optUsuario.get();

        // Validar nueva contraseña
        if (nuevaPassword == null || nuevaPassword.length() < Constants.MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + Constants.MIN_PASSWORD_LENGTH + " caracteres.");
        }

        // Cambiar contraseña y limpiar token
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuario.limpiarTokenRecuperacion();
        usuario.marcarPasswordCambiado();

        usuarioRepository.save(usuario);
        log.info("Contraseña restablecida exitosamente para: {}", username);

        return true;
    }

    /**
     * Restablece la contraseña usando la pregunta de seguridad.
     *
     * @param username Nombre de usuario
     * @param respuesta Respuesta a la pregunta de seguridad
     * @param nuevaPassword Nueva contraseña
     * @return true si se restableció exitosamente
     */
    @Transactional
    public boolean restablecerPasswordConPregunta(String username, String respuesta, String nuevaPassword) {
        if (!validarRespuestaSeguridad(username, respuesta)) {
            log.warn("Respuesta de seguridad incorrecta para: {}", username);
            return false;
        }

        Optional<Usuario> optUsuario = usuarioRepository.findByUsernameIgnoreCase(username);
        if (optUsuario.isEmpty()) {
            return false;
        }

        Usuario usuario = optUsuario.get();

        // Validar nueva contraseña
        if (nuevaPassword == null || nuevaPassword.length() < Constants.MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + Constants.MIN_PASSWORD_LENGTH + " caracteres.");
        }

        // Cambiar contraseña
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuario.marcarPasswordCambiado();

        usuarioRepository.save(usuario);
        log.info("Contraseña restablecida mediante pregunta de seguridad para: {}", username);

        return true;
    }

    /**
     * MÉTODO DE EMERGENCIA: Restablece contraseña a valor por defecto.
     * Solo debe usarse cuando el administrador pierde acceso.
     *
     * @param codigoMaestro Código maestro del sistema
     * @return true si se restableció
     */
    @Transactional
    public boolean restablecerPasswordEmergencia(String codigoMaestro) {
        // Código maestro basado en fecha de instalación o configuración
        // Este código debe ser conocido SOLO por el dueño del negocio
        String codigoEsperado = "SISTEMA-LIBRERIA-2024-RESET";

        if (!codigoEsperado.equals(codigoMaestro)) {
            log.warn("Intento de reset de emergencia con código incorrecto");
            return false;
        }

        // Buscar usuario admin (case-insensitive por consistencia)
        Optional<Usuario> optAdmin = usuarioRepository.findByUsernameIgnoreCase("admin");
        if (optAdmin.isEmpty()) {
            log.error("No se encontró el usuario admin para reset de emergencia");
            return false;
        }

        Usuario admin = optAdmin.get();
        admin.setPassword(passwordEncoder.encode("admin123")); // Contraseña temporal
        admin.setPasswordChanged(false); // Forzar cambio en próximo login
        admin.desbloquearCuenta();

        usuarioRepository.save(admin);
        log.warn("PASSWORD DE EMERGENCIA ACTIVADO - El admin debe cambiar su contraseña al ingresar");

        return true;
    }
}