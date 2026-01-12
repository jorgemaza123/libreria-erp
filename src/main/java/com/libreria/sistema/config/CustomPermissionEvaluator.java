package com.libreria.sistema.config;

import com.libreria.sistema.service.RolePermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Evaluador personalizado de permisos para Spring Security.
 * Permite usar @PreAuthorize("hasPermission(null, 'CODIGO_PERMISO')")
 *
 * REGLA DE ORO: ROLE_ADMIN siempre tiene acceso total (bypass).
 */
@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    @Autowired
    private RolePermissionService rolePermissionService;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // ============================================================
        // BYPASS TOTAL PARA ADMINISTRADOR
        // Si el usuario tiene ROLE_ADMIN, permitir SIEMPRE
        // ============================================================
        if (isAdmin(authentication)) {
            return true;
        }

        // Para otros usuarios, verificar permisos específicos
        String username = authentication.getName();
        String codigoPermiso = permission.toString();

        return rolePermissionService.tienePermiso(username, codigoPermiso);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        return hasPermission(authentication, null, permission);
    }

    /**
     * Verifica si el usuario autenticado tiene rol de administrador.
     * Busca tanto 'ROLE_ADMIN' como 'ADMIN' en las authorities.
     */
    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority) ||
                        "ADMIN".equals(authority));
    }
}
