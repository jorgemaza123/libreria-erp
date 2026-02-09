package com.libreria.sistema.config;

import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Filtro que intercepta todas las peticiones autenticadas y verifica
 * si el usuario ha cambiado su contraseña inicial.
 *
 * Si el usuario NO ha cambiado su contraseña (passwordChanged = false),
 * será redirigido obligatoriamente a la página de cambio de contraseña.
 *
 * SEGURIDAD: Impide el uso del sistema hasta que se establezca una clave segura.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordChangeFilter extends OncePerRequestFilter {

    private final UsuarioRepository usuarioRepository;

    // Rutas permitidas sin haber cambiado la contraseña
    private static final List<String> RUTAS_PERMITIDAS = Arrays.asList(
        "/login",
        "/logout",
        "/usuarios/cambiar-password-inicial",
        "/usuarios/guardar-password-inicial",
        "/usuarios/recuperar-password",
        "/usuarios/validar-recuperacion",
        "/usuarios/restablecer-password",
        "/css/",
        "/js/",
        "/img/",
        "/plugins/",
        "/dist/",
        "/public/",
        "/error",
        "/favicon.ico"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Verificar si la ruta está permitida sin cambio de contraseña
        if (esRutaPermitida(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Obtener autenticación actual
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Si no está autenticado, dejar pasar (Spring Security se encargará)
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Obtener el usuario de la BD
        String username = auth.getName();
        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);

        // Si el usuario existe y NO ha cambiado su contraseña → redirigir
        if (usuario != null && usuario.requiereCambioPassword()) {
            log.info("Usuario {} requiere cambio de contraseña inicial. Redirigiendo...", username);

            // Para peticiones AJAX, devolver JSON
            if (esAjaxRequest(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\": \"Debe cambiar su contraseña inicial.\", \"redirect\": \"/usuarios/cambiar-password-inicial\", \"code\": 403}");
                return;
            }

            // Para peticiones normales, redirigir
            response.sendRedirect(request.getContextPath() + "/usuarios/cambiar-password-inicial");
            return;
        }

        // Usuario ha cambiado contraseña o no existe → continuar normalmente
        filterChain.doFilter(request, response);
    }

    /**
     * Verifica si la URI está en la lista de rutas permitidas.
     */
    private boolean esRutaPermitida(String uri) {
        return RUTAS_PERMITIDAS.stream().anyMatch(uri::startsWith);
    }

    /**
     * Detecta si la petición es AJAX.
     */
    private boolean esAjaxRequest(HttpServletRequest request) {
        String xRequested = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        String contentType = request.getContentType();

        return "XMLHttpRequest".equals(xRequested)
            || (accept != null && accept.contains("application/json"))
            || (contentType != null && contentType.contains("application/json"));
    }
}
