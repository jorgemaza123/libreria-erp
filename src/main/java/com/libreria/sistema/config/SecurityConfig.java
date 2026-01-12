package com.libreria.sistema.config;

import com.libreria.sistema.service.UsuarioService;
import com.libreria.sistema.util.Constants;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Configuración de seguridad de Spring Security con:
 * - CSRF habilitado
 * - Bloqueo de cuenta por intentos fallidos
 * - Mensajes de error personalizados
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private CustomPermissionEvaluator customPermissionEvaluator;

    @Autowired
    @Lazy
    private UsuarioService usuarioService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ============================================================
            // CSRF - Deshabilitado para endpoints operativos del ERP
            // Justificacion: Sistema local sin exposicion a internet,
            // los formularios usan AJAX y el token causa conflictos.
            // ============================================================
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    "/public/**",
                    // Modulos operativos (formularios POST/PUT)
                    "/caja/**",
                    "/ventas/**",
                    "/productos/**",
                    "/compras/**",
                    "/clientes/**",
                    "/inventario/**",
                    "/cotizaciones/**",
                    "/cobranzas/**",
                    "/devoluciones/**",
                    "/ordenes-servicio/**",
                    "/proveedores/**",
                    "/kardex/**",
                    "/reportes/**",
                    "/reportes-financieros/**",
                    // Configuracion y administracion
                    "/configuracion/**",
                    "/usuarios/**",
                    "/roles/**",
                    "/licencia/**",
                    "/auditoria/**",
                    "/notificaciones/**",
                    "/incidencias/**",
                    // Endpoints API genericos
                    "/api/**"
                )
            )

            .authorizeHttpRequests(auth -> auth
                // ============================================================
                // RUTAS PUBLICAS - CRITICO: Deben ir ANTES de anyRequest()
                // ============================================================

                // Login y sus variantes (EVITA BUCLE DE REDIRECCION)
                .requestMatchers("/login", "/login/**").permitAll()

                // Recursos estaticos publicos
                .requestMatchers("/css/**", "/js/**", "/img/**", "/plugins/**", "/dist/**", "/uploads/**").permitAll()
                .requestMatchers("/public/**").permitAll()

                // Error pages
                .requestMatchers("/error", "/error/**").permitAll()

                // ============================================================
                // RUTAS DE ADMINISTRACION EXCLUSIVA - solo ADMIN
                // ============================================================
                .requestMatchers("/configuracion/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                .requestMatchers("/licencia/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                .requestMatchers("/auditoria/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                .requestMatchers("/roles/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                .requestMatchers("/usuarios/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")

                // ============================================================
                // MODULOS OPERATIVOS - Requieren autenticacion
                // Los permisos especificos se validan con @PreAuthorize
                // ============================================================
                .requestMatchers("/caja/**").authenticated()
                .requestMatchers("/ventas/**").authenticated()
                .requestMatchers("/productos/**").authenticated()
                .requestMatchers("/clientes/**").authenticated()
                .requestMatchers("/compras/**").authenticated()
                .requestMatchers("/reportes/**").authenticated()
                .requestMatchers("/reportes-financieros/**").authenticated()
                .requestMatchers("/inventario/**").authenticated()
                .requestMatchers("/kardex/**").authenticated()
                .requestMatchers("/cotizaciones/**").authenticated()
                .requestMatchers("/cobranzas/**").authenticated()
                .requestMatchers("/devoluciones/**").authenticated()
                .requestMatchers("/ordenes-servicio/**").authenticated()
                .requestMatchers("/proveedores/**").authenticated()
                .requestMatchers("/notificaciones/**").authenticated()
                .requestMatchers("/incidencias/**").authenticated()

                // ============================================================
                // ENDPOINTS API - Autenticados (CSRF se maneja aparte)
                // ============================================================
                .requestMatchers("/api/**").authenticated()

                // ============================================================
                // DASHBOARD Y HOME - Cualquier usuario autenticado
                // ============================================================
                .requestMatchers("/", "/dashboard/**").authenticated()

                // ============================================================
                // REGLA FINAL: Cualquier otra ruta requiere autenticacion
                // ============================================================
                .anyRequest().authenticated()
            )

            // ============================================================
            // FORM LOGIN
            // ============================================================
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureHandler(customAuthenticationFailureHandler())
                .permitAll()
            )

            // ============================================================
            // LOGOUT - Con limpieza de cookies para evitar bucles
            // ============================================================
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID")  // CRITICO: Limpia la cookie de sesion
                .invalidateHttpSession(true)   // Invalida la sesion HTTP
                .clearAuthentication(true)     // Limpia el contexto de autenticacion
                .permitAll()
            )

            // ============================================================
            // SESSION MANAGEMENT - Configuracion correcta para evitar bucles
            // ============================================================
            .sessionManagement(session -> session
                // Cuando la sesion es invalida (no existe), redirigir a login
                .invalidSessionUrl("/login?invalid")
                // Configuracion de sesiones concurrentes
                .maximumSessions(Constants.MAX_SESSIONS_PER_USER)
                .maxSessionsPreventsLogin(false)  // Permite login, expira la sesion anterior
                .expiredUrl("/login?expired")
            )

            // ============================================================
            // MANEJO DE EXCEPCIONES
            // ============================================================
            .exceptionHandling(ex -> ex
                .accessDeniedHandler(apiAccessDeniedHandler())
                .authenticationEntryPoint((request, response, authException) -> {
                    // Si es una peticion AJAX/API, devolver JSON
                    if (isApiRequest(request)) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write("{\"error\": \"Sesion expirada. Por favor inicie sesion nuevamente.\", \"code\": 401}");
                    } else {
                        // Redirigir a login sin parametros adicionales que puedan causar bucle
                        response.sendRedirect(request.getContextPath() + "/login");
                    }
                })
            );

        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /**
     * Handler personalizado para errores de autenticación.
     * Muestra mensajes específicos según el tipo de error.
     */
    @Bean
    public AuthenticationFailureHandler customAuthenticationFailureHandler() {
        return new AuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(HttpServletRequest request,
                                                HttpServletResponse response,
                                                AuthenticationException exception) throws IOException, ServletException {
                String username = request.getParameter("username");
                String errorMessage = "Credenciales incorrectas";

                // Verificar si la cuenta está bloqueada
                if (username != null && usuarioService.estaCuentaBloqueada(username)) {
                    errorMessage = "Cuenta bloqueada por " + Constants.MAX_LOGIN_ATTEMPTS +
                            " intentos fallidos. Intente en " +
                            Constants.ACCOUNT_LOCK_DURATION_MINUTES + " minutos.";
                } else if (exception.getMessage().contains("bloqueada")) {
                    errorMessage = exception.getMessage();
                } else if (exception.getMessage().contains("no está activo")) {
                    errorMessage = "El usuario no está activo. Contacte al administrador.";
                } else {
                    // Mostrar intentos restantes
                    int intentosFallidos = usuarioService.obtenerIntentosFallidos(username);
                    int intentosRestantes = Constants.MAX_LOGIN_ATTEMPTS - intentosFallidos;
                    if (intentosRestantes > 0 && intentosRestantes < Constants.MAX_LOGIN_ATTEMPTS) {
                        errorMessage = "Credenciales incorrectas. Intentos restantes: " + intentosRestantes;
                    }
                }

                // Redirigir con mensaje de error
                String encodedError = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
                response.sendRedirect("/login?error=" + encodedError);
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(customPermissionEvaluator);
        return handler;
    }

    /**
     * Handler para errores de acceso denegado (403).
     * Devuelve JSON para peticiones API, redirección para vistas.
     */
    @Bean
    public AccessDeniedHandler apiAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            if (isApiRequest(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\": \"No tiene permisos para realizar esta acción.\", \"code\": 403}");
            } else {
                response.sendRedirect("/error/403");
            }
        };
    }

    /**
     * Determina si la petición es una llamada API (AJAX/JSON).
     */
    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        String xRequested = request.getHeader("X-Requested-With");

        return uri.contains("/api/")
            || "XMLHttpRequest".equals(xRequested)
            || (accept != null && accept.contains("application/json"));
    }
}
