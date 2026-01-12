package com.libreria.sistema.service;

import com.libreria.sistema.model.Permission;
import com.libreria.sistema.model.Role;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.repository.PermissionRepository;
import com.libreria.sistema.repository.RoleRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RolePermissionService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * Crear un nuevo rol con permisos
     */
    @Transactional
    public Role crearRole(String nombre, String descripcion, List<Long> permissionIds) {
        if (roleRepository.existsByNombre(nombre)) {
            throw new RuntimeException("Ya existe un rol con ese nombre");
        }

        Role role = new Role(nombre, descripcion);

        if (permissionIds != null && !permissionIds.isEmpty()) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
            role.setPermissions(permissions);
        }

        return roleRepository.save(role);
    }

    /**
     * Actualizar un rol existente
     */
    @Transactional
    public Role actualizarRole(Long id, String nombre, String descripcion, List<Long> permissionIds) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        // Verificar si el nombre ya existe en otro rol
        if (!role.getNombre().equals(nombre) && roleRepository.existsByNombre(nombre)) {
            throw new RuntimeException("Ya existe un rol con ese nombre");
        }

        role.setNombre(nombre);
        role.setDescripcion(descripcion);

        // Actualizar permisos
        role.getPermissions().clear();
        if (permissionIds != null && !permissionIds.isEmpty()) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
            role.setPermissions(permissions);
        }

        return roleRepository.save(role);
    }

    /**
     * Eliminar un rol (solo si no tiene usuarios asignados)
     */
    @Transactional
    public void eliminarRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        // Verificar que no tenga usuarios asignados
        long usuariosConEsteRole = usuarioRepository.countByRole(role);
        if (usuariosConEsteRole > 0) {
            throw new RuntimeException("No se puede eliminar el rol porque tiene " +
                                     usuariosConEsteRole + " usuario(s) asignado(s)");
        }

        roleRepository.delete(role);
    }

    /**
     * Asignar permisos a un rol
     */
    @Transactional
    public void asignarPermisosARole(Long roleId, List<Long> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        role.getPermissions().clear();

        if (permissionIds != null && !permissionIds.isEmpty()) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
            role.setPermissions(permissions);
        }

        roleRepository.save(role);
    }

    /**
     * Obtener todos los códigos de permisos de un usuario
     */
    public List<String> obtenerPermisosDeUsuario(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElse(null);

        if (usuario == null) {
            return Collections.emptyList();
        }

        // Si tiene el nuevo sistema de roles
        if (usuario.getRole() != null) {
            return usuario.getRole().getPermissions().stream()
                    .map(Permission::getCodigo)
                    .collect(Collectors.toList());
        }

        // Fallback: Si usa el sistema antiguo, retornar permisos basados en roles antiguos
        // El ADMIN tiene todos los permisos
        if (usuario.getRoles().stream().anyMatch(r -> r.getNombre().equals("ROLE_ADMIN"))) {
            return permissionRepository.findAll().stream()
                    .map(Permission::getCodigo)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * Verificar si un usuario tiene un permiso específico.
     *
     * REGLA DE ORO: ADMIN siempre tiene todos los permisos (bypass).
     */
    public boolean tienePermiso(String username, String codigoPermiso) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElse(null);

        if (usuario == null) {
            return false;
        }

        // ============================================================
        // VERIFICACION 1: ADMIN tiene SIEMPRE todos los permisos
        // Verificar en el sistema de roles antiguo (roles)
        // ============================================================
        if (usuario.getRoles() != null &&
            usuario.getRoles().stream().anyMatch(r ->
                "ROLE_ADMIN".equals(r.getNombre()) || "ADMIN".equals(r.getNombre()))) {
            return true;
        }

        // ============================================================
        // VERIFICACION 2: Nuevo sistema de roles (role singular)
        // ============================================================
        if (usuario.getRole() != null) {
            // Si el nuevo role es ADMIN, permitir todo
            String nombreRole = usuario.getRole().getNombre();
            if ("ADMIN".equals(nombreRole) || "ROLE_ADMIN".equals(nombreRole)) {
                return true;
            }

            // Para otros roles, verificar permisos específicos
            return usuario.getRole().getPermissions().stream()
                    .anyMatch(p -> p.getCodigo().equals(codigoPermiso));
        }

        return false;
    }

    /**
     * Obtener todos los roles activos
     */
    public List<Role> obtenerRolesActivos() {
        return roleRepository.findByActivoOrderByNombreAsc(true);
    }

    /**
     * Obtener todos los roles
     */
    public List<Role> obtenerTodosLosRoles() {
        return roleRepository.findAll();
    }

    /**
     * Obtener un rol por ID con sus permisos
     */
    public Optional<Role> obtenerRolePorId(Long id) {
        return roleRepository.findByIdWithPermissions(id);
    }

    /**
     * Obtener un rol por nombre con sus permisos
     */
    public Optional<Role> obtenerRolePorNombre(String nombre) {
        return roleRepository.findByNombreWithPermissions(nombre);
    }

    /**
     * Obtener todos los permisos disponibles
     */
    public List<Permission> obtenerTodosLosPermisos() {
        return permissionRepository.findAll();
    }

    /**
     * Obtener permisos agrupados por módulo
     */
    public Map<String, List<Permission>> obtenerPermisosAgrupadosPorModulo() {
        List<Permission> todosPermisos = permissionRepository.findAll();
        return todosPermisos.stream()
                .collect(Collectors.groupingBy(Permission::getModulo));
    }

    /**
     * Crear permisos por defecto si no existen
     */
    @Transactional
    public void crearPermisosPorDefecto() {
        List<Permission> permisos = new ArrayList<>();

        // VENTAS
        permisos.add(crearPermisoSiNoExiste("VENTAS", "VER", "VENTAS_VER", "Ver ventas"));
        permisos.add(crearPermisoSiNoExiste("VENTAS", "CREAR", "VENTAS_CREAR", "Crear ventas"));
        permisos.add(crearPermisoSiNoExiste("VENTAS", "EDITAR", "VENTAS_EDITAR", "Editar ventas"));
        permisos.add(crearPermisoSiNoExiste("VENTAS", "ELIMINAR", "VENTAS_ELIMINAR", "Eliminar/Anular ventas"));

        // COMPRAS
        permisos.add(crearPermisoSiNoExiste("COMPRAS", "VER", "COMPRAS_VER", "Ver compras"));
        permisos.add(crearPermisoSiNoExiste("COMPRAS", "CREAR", "COMPRAS_CREAR", "Crear compras"));
        permisos.add(crearPermisoSiNoExiste("COMPRAS", "EDITAR", "COMPRAS_EDITAR", "Editar compras"));
        permisos.add(crearPermisoSiNoExiste("COMPRAS", "ELIMINAR", "COMPRAS_ELIMINAR", "Eliminar compras"));

        // INVENTARIO
        permisos.add(crearPermisoSiNoExiste("INVENTARIO", "VER", "INVENTARIO_VER", "Ver inventario"));
        permisos.add(crearPermisoSiNoExiste("INVENTARIO", "CREAR", "INVENTARIO_CREAR", "Crear productos"));
        permisos.add(crearPermisoSiNoExiste("INVENTARIO", "EDITAR", "INVENTARIO_EDITAR", "Editar productos"));
        permisos.add(crearPermisoSiNoExiste("INVENTARIO", "ELIMINAR", "INVENTARIO_ELIMINAR", "Eliminar productos"));

        // CAJA
        permisos.add(crearPermisoSiNoExiste("CAJA", "VER", "CAJA_VER", "Ver caja"));
        permisos.add(crearPermisoSiNoExiste("CAJA", "CREAR", "CAJA_CREAR", "Abrir/Cerrar caja"));
        permisos.add(crearPermisoSiNoExiste("CAJA", "EDITAR", "CAJA_EDITAR", "Editar movimientos de caja"));

        // REPORTES
        permisos.add(crearPermisoSiNoExiste("REPORTES", "VER", "REPORTES_VER", "Ver reportes"));
        permisos.add(crearPermisoSiNoExiste("REPORTES", "EXPORTAR", "REPORTES_EXPORTAR", "Exportar reportes"));

        // REPORTES FINANCIEROS
        permisos.add(crearPermisoSiNoExiste("REPORTES_FINANCIEROS", "VER", "REPORTES_FINANCIEROS_VER", "Ver reportes financieros"));
        permisos.add(crearPermisoSiNoExiste("REPORTES_FINANCIEROS", "EXPORTAR", "REPORTES_FINANCIEROS_EXPORTAR", "Exportar reportes financieros"));

        // CONFIGURACION
        permisos.add(crearPermisoSiNoExiste("CONFIGURACION", "VER", "CONFIGURACION_VER", "Ver configuración"));
        permisos.add(crearPermisoSiNoExiste("CONFIGURACION", "EDITAR", "CONFIGURACION_EDITAR", "Editar configuración"));

        // USUARIOS
        permisos.add(crearPermisoSiNoExiste("USUARIOS", "VER", "USUARIOS_VER", "Ver usuarios"));
        permisos.add(crearPermisoSiNoExiste("USUARIOS", "CREAR", "USUARIOS_CREAR", "Crear usuarios"));
        permisos.add(crearPermisoSiNoExiste("USUARIOS", "EDITAR", "USUARIOS_EDITAR", "Editar usuarios"));
        permisos.add(crearPermisoSiNoExiste("USUARIOS", "ELIMINAR", "USUARIOS_ELIMINAR", "Eliminar usuarios"));

        // AUDITORIA
        permisos.add(crearPermisoSiNoExiste("AUDITORIA", "VER", "AUDITORIA_VER", "Ver auditoría"));

        // DEVOLUCIONES
        permisos.add(crearPermisoSiNoExiste("DEVOLUCIONES", "VER", "DEVOLUCIONES_VER", "Ver devoluciones"));
        permisos.add(crearPermisoSiNoExiste("DEVOLUCIONES", "CREAR", "DEVOLUCIONES_CREAR", "Crear devoluciones"));
        permisos.add(crearPermisoSiNoExiste("DEVOLUCIONES", "ANULAR", "DEVOLUCIONES_ANULAR", "Anular devoluciones"));

        // COTIZACIONES
        permisos.add(crearPermisoSiNoExiste("COTIZACIONES", "VER", "COTIZACIONES_VER", "Ver cotizaciones"));
        permisos.add(crearPermisoSiNoExiste("COTIZACIONES", "CREAR", "COTIZACIONES_CREAR", "Crear cotizaciones"));
        permisos.add(crearPermisoSiNoExiste("COTIZACIONES", "EDITAR", "COTIZACIONES_EDITAR", "Editar cotizaciones"));
        permisos.add(crearPermisoSiNoExiste("COTIZACIONES", "ELIMINAR", "COTIZACIONES_ELIMINAR", "Eliminar cotizaciones"));
        permisos.add(crearPermisoSiNoExiste("COTIZACIONES", "CONVERTIR", "COTIZACIONES_CONVERTIR", "Convertir cotizaciones a ventas"));

        // COBRANZAS
        permisos.add(crearPermisoSiNoExiste("COBRANZAS", "VER", "COBRANZAS_VER", "Ver cobranzas"));
        permisos.add(crearPermisoSiNoExiste("COBRANZAS", "CREAR", "COBRANZAS_CREAR", "Registrar pagos de cobranza"));

        // CLIENTES
        permisos.add(crearPermisoSiNoExiste("CLIENTES", "VER", "CLIENTES_VER", "Ver clientes"));
        permisos.add(crearPermisoSiNoExiste("CLIENTES", "CREAR", "CLIENTES_CREAR", "Crear clientes"));
        permisos.add(crearPermisoSiNoExiste("CLIENTES", "EDITAR", "CLIENTES_EDITAR", "Editar clientes"));
        permisos.add(crearPermisoSiNoExiste("CLIENTES", "ELIMINAR", "CLIENTES_ELIMINAR", "Eliminar clientes"));

        // PROVEEDORES
        permisos.add(crearPermisoSiNoExiste("PROVEEDORES", "VER", "PROVEEDORES_VER", "Ver proveedores"));
        permisos.add(crearPermisoSiNoExiste("PROVEEDORES", "CREAR", "PROVEEDORES_CREAR", "Crear proveedores"));
        permisos.add(crearPermisoSiNoExiste("PROVEEDORES", "EDITAR", "PROVEEDORES_EDITAR", "Editar proveedores"));
        permisos.add(crearPermisoSiNoExiste("PROVEEDORES", "ELIMINAR", "PROVEEDORES_ELIMINAR", "Eliminar proveedores"));

        // KARDEX
        permisos.add(crearPermisoSiNoExiste("KARDEX", "VER", "KARDEX_VER", "Ver movimientos de kardex"));

        // ORDENES DE SERVICIO
        permisos.add(crearPermisoSiNoExiste("ORDENES_SERVICIO", "VER", "ORDENES_SERVICIO_VER", "Ver órdenes de servicio"));
        permisos.add(crearPermisoSiNoExiste("ORDENES_SERVICIO", "CREAR", "ORDENES_SERVICIO_CREAR", "Crear órdenes de servicio"));
        permisos.add(crearPermisoSiNoExiste("ORDENES_SERVICIO", "EDITAR", "ORDENES_SERVICIO_EDITAR", "Editar órdenes de servicio"));
        permisos.add(crearPermisoSiNoExiste("ORDENES_SERVICIO", "ELIMINAR", "ORDENES_SERVICIO_ELIMINAR", "Eliminar órdenes de servicio"));

        // ROLES Y PERMISOS (Gestión de accesos)
        permisos.add(crearPermisoSiNoExiste("ROLES", "VER", "ROLES_VER", "Ver roles y permisos"));
        permisos.add(crearPermisoSiNoExiste("ROLES", "CREAR", "ROLES_CREAR", "Crear roles"));
        permisos.add(crearPermisoSiNoExiste("ROLES", "EDITAR", "ROLES_EDITAR", "Editar roles y asignar permisos"));
        permisos.add(crearPermisoSiNoExiste("ROLES", "ELIMINAR", "ROLES_ELIMINAR", "Eliminar roles"));

        // INCIDENCIAS / REPORTES DE PROBLEMAS
        permisos.add(crearPermisoSiNoExiste("INCIDENCIAS", "VER", "INCIDENCIAS_VER", "Ver incidencias/reportes de problemas"));
        permisos.add(crearPermisoSiNoExiste("INCIDENCIAS", "CREAR", "INCIDENCIAS_CREAR", "Crear incidencias"));
        permisos.add(crearPermisoSiNoExiste("INCIDENCIAS", "EDITAR", "INCIDENCIAS_EDITAR", "Editar/Gestionar incidencias"));
        permisos.add(crearPermisoSiNoExiste("INCIDENCIAS", "ELIMINAR", "INCIDENCIAS_ELIMINAR", "Eliminar incidencias"));

        // NOTIFICACIONES
        permisos.add(crearPermisoSiNoExiste("NOTIFICACIONES", "VER", "NOTIFICACIONES_VER", "Ver notificaciones"));

        log.info("Permisos por defecto creados/verificados");
    }

    private Permission crearPermisoSiNoExiste(String modulo, String accion, String codigo, String descripcion) {
        return permissionRepository.findByCodigo(codigo)
                .orElseGet(() -> {
                    Permission p = new Permission(modulo, accion, codigo, descripcion);
                    return permissionRepository.save(p);
                });
    }

    /**
     * Crear roles predefinidos si no existen
     */
    @Transactional
    public void crearRolesPredefinidos() {
        // Asegurar que existen los permisos
        crearPermisosPorDefecto();

        // ADMIN - Todos los permisos
        Role admin = roleRepository.findByNombre("ADMIN").orElse(null);
        if (admin == null) {
            admin = new Role("ADMIN", "Administrador del sistema - Acceso total");
            log.info("Rol ADMIN creado con todos los permisos");
        } else {
            log.info("Rol ADMIN actualizado con todos los permisos");
        }
        // Actualizar permisos del ADMIN (incluye nuevos permisos si se agregaron)
        admin.setPermissions(new HashSet<>(permissionRepository.findAll()));
        roleRepository.save(admin);

        // VENDEDOR
        Role vendedor = roleRepository.findByNombre("VENDEDOR").orElse(null);
        if (vendedor == null) {
            vendedor = new Role("VENDEDOR", "Vendedor - Crear ventas, cotizaciones y consultar productos");
            log.info("Rol VENDEDOR creado");
        } else {
            log.info("Rol VENDEDOR actualizado con nuevos permisos");
        }
        Set<Permission> permisosVendedor = new HashSet<>();
        agregarPermiso(permisosVendedor, "VENTAS_VER");
        agregarPermiso(permisosVendedor, "VENTAS_CREAR");
        agregarPermiso(permisosVendedor, "INVENTARIO_VER");
        agregarPermiso(permisosVendedor, "CAJA_VER");
        agregarPermiso(permisosVendedor, "CAJA_CREAR");
        agregarPermiso(permisosVendedor, "DEVOLUCIONES_VER");
        agregarPermiso(permisosVendedor, "DEVOLUCIONES_CREAR");
        agregarPermiso(permisosVendedor, "COTIZACIONES_VER");
        agregarPermiso(permisosVendedor, "COTIZACIONES_CREAR");
        agregarPermiso(permisosVendedor, "COTIZACIONES_EDITAR");
        agregarPermiso(permisosVendedor, "COTIZACIONES_CONVERTIR");
        agregarPermiso(permisosVendedor, "COBRANZAS_VER");
        agregarPermiso(permisosVendedor, "COBRANZAS_CREAR");
        vendedor.setPermissions(permisosVendedor);
        roleRepository.save(vendedor);

        // CONTADOR
        Role contador = roleRepository.findByNombre("CONTADOR").orElse(null);
        if (contador == null) {
            contador = new Role("CONTADOR", "Contador - Ver reportes y operaciones financieras");
            log.info("Rol CONTADOR creado");
        } else {
            log.info("Rol CONTADOR actualizado con nuevos permisos");
        }
        Set<Permission> permisosContador = new HashSet<>();
        agregarPermiso(permisosContador, "REPORTES_VER");
        agregarPermiso(permisosContador, "REPORTES_EXPORTAR");
        agregarPermiso(permisosContador, "REPORTES_FINANCIEROS_VER");
        agregarPermiso(permisosContador, "REPORTES_FINANCIEROS_EXPORTAR");
        agregarPermiso(permisosContador, "VENTAS_VER");
        agregarPermiso(permisosContador, "COMPRAS_VER");
        agregarPermiso(permisosContador, "CAJA_VER");
        agregarPermiso(permisosContador, "AUDITORIA_VER");
        contador.setPermissions(permisosContador);
        roleRepository.save(contador);

        // ALMACENERO
        if (!roleRepository.existsByNombre("ALMACENERO")) {
            Role almacenero = new Role("ALMACENERO", "Almacenero - Gestión de inventario y compras");
            Set<Permission> permisosAlmacenero = new HashSet<>();
            agregarPermiso(permisosAlmacenero, "INVENTARIO_VER");
            agregarPermiso(permisosAlmacenero, "INVENTARIO_CREAR");
            agregarPermiso(permisosAlmacenero, "INVENTARIO_EDITAR");
            agregarPermiso(permisosAlmacenero, "COMPRAS_VER");
            agregarPermiso(permisosAlmacenero, "COMPRAS_CREAR");
            almacenero.setPermissions(permisosAlmacenero);
            roleRepository.save(almacenero);
            log.info("Rol ALMACENERO creado");
        }

        log.info("Roles predefinidos verificados/creados");
    }

    private void agregarPermiso(Set<Permission> permisos, String codigo) {
        permissionRepository.findByCodigo(codigo).ifPresent(permisos::add);
    }
}
