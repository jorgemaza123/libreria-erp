package com.libreria.sistema.controller;

import com.libreria.sistema.model.Permission;
import com.libreria.sistema.model.Role;
import com.libreria.sistema.service.RolePermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/roles")
@PreAuthorize("hasPermission(null, 'ROLES_VER')")
public class RoleController {

    @Autowired
    private RolePermissionService rolePermissionService;

    /**
     * Vista principal de roles
     */
    @GetMapping
    public String index(Model model) {
        model.addAttribute("titulo", "Gestión de Roles y Permisos");
        return "roles/index";
    }

    /**
     * Listar todos los roles
     */
    @GetMapping("/api/listar")
    @ResponseBody
    public List<Map<String, Object>> listarRoles() {
        return rolePermissionService.obtenerTodosLosRoles().stream()
                .map(role -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", role.getId());
                    map.put("nombre", role.getNombre());
                    map.put("descripcion", role.getDescripcion());
                    map.put("activo", role.getActivo());
                    map.put("cantidadPermisos", role.getPermissions().size());
                    map.put("cantidadUsuarios", role.getUsuarios().size());
                    map.put("fechaCreacion", role.getFechaCreacion());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Obtener todos los permisos disponibles agrupados por módulo
     */
    @GetMapping("/api/permisos")
    @ResponseBody
    public Map<String, List<Map<String, Object>>> obtenerPermisos() {
        Map<String, List<Permission>> permisosAgrupados =
                rolePermissionService.obtenerPermisosAgrupadosPorModulo();

        Map<String, List<Map<String, Object>>> resultado = new HashMap<>();

        permisosAgrupados.forEach((modulo, permisos) -> {
            List<Map<String, Object>> permisosList = permisos.stream()
                    .map(p -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", p.getId());
                        map.put("codigo", p.getCodigo());
                        map.put("accion", p.getAccion());
                        map.put("descripcion", p.getDescripcion());
                        return map;
                    })
                    .collect(Collectors.toList());
            resultado.put(modulo, permisosList);
        });

        return resultado;
    }

    /**
     * Obtener permisos de un rol específico
     */
    @GetMapping("/api/{id}/permisos")
    @ResponseBody
    public ResponseEntity<List<Long>> obtenerPermisosDeRole(@PathVariable Long id) {
        return rolePermissionService.obtenerRolePorId(id)
                .map(role -> {
                    List<Long> permissionIds = role.getPermissions().stream()
                            .map(Permission::getId)
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(permissionIds);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Crear un nuevo rol
     */
    @PostMapping("/api/crear")
    @PreAuthorize("hasPermission(null, 'ROLES_CREAR')")
    @ResponseBody
    public ResponseEntity<?> crearRole(@RequestBody Map<String, Object> request) {
        try {
            String nombre = (String) request.get("nombre");
            String descripcion = (String) request.get("descripcion");
            @SuppressWarnings("unchecked")
            List<Integer> permissionIdsInt = (List<Integer>) request.get("permissionIds");

            List<Long> permissionIds = permissionIdsInt != null ?
                    permissionIdsInt.stream().map(Integer::longValue).collect(Collectors.toList()) :
                    List.of();

            Role role = rolePermissionService.crearRole(nombre, descripcion, permissionIds);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rol creado exitosamente",
                    "roleId", role.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Actualizar un rol existente
     */
    @PutMapping("/api/actualizar/{id}")
    @PreAuthorize("hasPermission(null, 'ROLES_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> actualizarRole(@PathVariable Long id,
                                           @RequestBody Map<String, Object> request) {
        try {
            String nombre = (String) request.get("nombre");
            String descripcion = (String) request.get("descripcion");
            @SuppressWarnings("unchecked")
            List<Integer> permissionIdsInt = (List<Integer>) request.get("permissionIds");

            List<Long> permissionIds = permissionIdsInt != null ?
                    permissionIdsInt.stream().map(Integer::longValue).collect(Collectors.toList()) :
                    List.of();

            rolePermissionService.actualizarRole(id, nombre, descripcion, permissionIds);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rol actualizado exitosamente"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Eliminar un rol
     */
    @DeleteMapping("/api/eliminar/{id}")
    @PreAuthorize("hasPermission(null, 'ROLES_ELIMINAR')")
    @ResponseBody
    public ResponseEntity<?> eliminarRole(@PathVariable Long id) {
        try {
            rolePermissionService.eliminarRole(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rol eliminado exitosamente"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
