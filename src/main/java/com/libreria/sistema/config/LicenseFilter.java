package com.libreria.sistema.config;

import com.libreria.sistema.service.LicenseValidationService;
import com.libreria.sistema.service.LicenseValidationService.EstadoLicencia;
import com.libreria.sistema.service.LicenseValidationService.LicenseInfo;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Filtro que intercepta todas las requests para validar la licencia del sistema.
 * Redirige a la página de activación si la licencia está bloqueada o no existe.
 */
@Component
@Order(1) // Se ejecuta antes que otros filtros
@Slf4j
public class LicenseFilter implements Filter {

    private final LicenseValidationService licenseService;

    // Rutas que NO requieren validación de licencia
    private static final Set<String> RUTAS_EXCLUIDAS = Set.of(
            "/licencia",
            "/licencia/activar",
            "/licencia/api",
            "/login",
            "/logout",
            "/css",
            "/js",
            "/img",
            "/plugins",
            "/public",
            "/error",
            "/favicon.ico",
            "/webjars"
    );

    public LicenseFilter(LicenseValidationService licenseService) {
        this.licenseService = licenseService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // 1. Verificar si la ruta está excluida
        if (esRutaExcluida(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Validar licencia
        try {
            LicenseInfo licenseInfo = licenseService.validarLicencia();

            // Guardar info de licencia en el request para uso en templates
            httpRequest.setAttribute("licenseInfo", licenseInfo);
            httpRequest.setAttribute("alertaPago", licenseInfo.isAlertaPago());

            // 3. Verificar estado
            if (licenseInfo.getEstado() == EstadoLicencia.BLOQUEADO ||
                licenseInfo.getEstado() == EstadoLicencia.SIN_LICENCIA ||
                licenseInfo.getEstado() == EstadoLicencia.INVALIDA) {

                log.warn("Acceso bloqueado por licencia. Estado: {}, Ruta: {}",
                        licenseInfo.getEstado(), path);

                // Redirigir a página de licencia
                httpResponse.sendRedirect("/licencia");
                return;
            }

            // Licencia válida (ACTIVO o EN_GRACIA) - continuar
            chain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Error en filtro de licencia: {}", e.getMessage(), e);
            // En caso de error, permitir acceso para no bloquear el sistema
            chain.doFilter(request, response);
        }
    }

    /**
     * Verifica si la ruta está en la lista de exclusiones.
     */
    private boolean esRutaExcluida(String path) {
        if (path == null) return true;

        for (String ruta : RUTAS_EXCLUIDAS) {
            if (path.equals(ruta) || path.startsWith(ruta + "/") || path.startsWith(ruta + "?")) {
                return true;
            }
        }

        // Excluir recursos estáticos
        if (path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".png") ||
            path.endsWith(".jpg") || path.endsWith(".gif") || path.endsWith(".ico") ||
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf")) {
            return true;
        }

        return false;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("LicenseFilter inicializado");
    }

    @Override
    public void destroy() {
        log.info("LicenseFilter destruido");
    }
}
