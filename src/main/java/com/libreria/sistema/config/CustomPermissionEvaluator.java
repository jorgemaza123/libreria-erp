package com.libreria.sistema.config;

import com.libreria.sistema.service.RolePermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Evaluador personalizado de permisos para Spring Security
 * Permite usar @PreAuthorize("hasPermission('CODIGO_PERMISO')")
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

        String username = authentication.getName();
        String codigoPermiso = permission.toString();

        return rolePermissionService.tienePermiso(username, codigoPermiso);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        // No implementado para este caso de uso
        return hasPermission(authentication, null, permission);
    }
}
