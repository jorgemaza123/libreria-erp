package com.libreria.sistema.config;

import com.libreria.sistema.model.Correlativo;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Rol;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.repository.CorrelativoRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.RolRepository;
import com.libreria.sistema.repository.RoleRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import com.libreria.sistema.service.RolePermissionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(UsuarioRepository usuarioRepo,
                               RolRepository rolRepo,
                               RoleRepository roleRepo,
                               CorrelativoRepository correlativoRepo,
                               ProductoRepository productoRepo,
                               RolePermissionService rolePermissionService,
                               PasswordEncoder passwordEncoder,
                               PlatformTransactionManager transactionManager) {
        return args -> {
            // Usamos TransactionTemplate para mantener la sesión de BD abierta
            new TransactionTemplate(transactionManager).execute(status -> {
                
                System.out.println(">>> Inicializando sistema...");
                
                // 0. Permisos y Roles
                rolePermissionService.crearPermisosPorDefecto();
                rolePermissionService.crearRolesPredefinidos();

                // 1. Roles Legacy
                Rol rolAdmin = rolRepo.findByNombre("ROLE_ADMIN").orElseGet(() -> rolRepo.save(new Rol("ROLE_ADMIN")));
                Rol rolVendedor = rolRepo.findByNombre("ROLE_VENDEDOR").orElseGet(() -> rolRepo.save(new Rol("ROLE_VENDEDOR")));

                // 2. Usuarios
                if (usuarioRepo.findByUsername("admin").isEmpty()) {
                    Usuario admin = new Usuario();
                    admin.setUsername("admin");
                    admin.setPassword(passwordEncoder.encode("admin"));
                    admin.setNombreCompleto("Administrador del Sistema");
                    // CORRECCIÓN AQUÍ: Usar new HashSet para que sea mutable
                    admin.setRoles(new HashSet<>(Set.of(rolAdmin)));
                    admin.setActivo(true);
                    usuarioRepo.save(admin);
                    System.out.println(">>> USUARIO ADMIN CREADO");
                }

                if (usuarioRepo.findByUsername("vendedor").isEmpty()) {
                    Usuario vend = new Usuario();
                    vend.setUsername("vendedor");
                    vend.setPassword(passwordEncoder.encode("1234"));
                    vend.setNombreCompleto("Vendedor de Tienda");
                    // CORRECCIÓN AQUÍ: Usar new HashSet para que sea mutable
                    vend.setRoles(new HashSet<>(Set.of(rolVendedor)));
                    vend.setActivo(true);
                    usuarioRepo.save(vend);
                    System.out.println(">>> USUARIO VENDEDOR CREADO");
                }

                // 3. Correlativos - Inicialización segura para TODOS los tipos de documento y series
                // MODO SUNAT (Facturación Electrónica Activa)
                if (correlativoRepo.findByCodigoAndSerie("BOLETA", "B001").isEmpty()) {
                    correlativoRepo.save(new Correlativo("BOLETA", "B001", 0));
                }
                if (correlativoRepo.findByCodigoAndSerie("FACTURA", "F001").isEmpty()) {
                    correlativoRepo.save(new Correlativo("FACTURA", "F001", 0));
                }
                if (correlativoRepo.findByCodigoAndSerie("NOTA_CREDITO", "C001").isEmpty()) {
                    correlativoRepo.save(new Correlativo("NOTA_CREDITO", "C001", 0));
                }
                if (correlativoRepo.findByCodigoAndSerie("NOTA_VENTA", "N001").isEmpty()) {
                    correlativoRepo.save(new Correlativo("NOTA_VENTA", "N001", 0));
                }

                // MODO INTERNO (Facturación Electrónica Desactivada)
                if (correlativoRepo.findByCodigoAndSerie("BOLETA", "I001").isEmpty()) {
                    correlativoRepo.save(new Correlativo("BOLETA", "I001", 0));
                }
                if (correlativoRepo.findByCodigoAndSerie("FACTURA", "IF001").isEmpty()) {
                    correlativoRepo.save(new Correlativo("FACTURA", "IF001", 0));
                }
                if (correlativoRepo.findByCodigoAndSerie("NOTA_VENTA", "NI001").isEmpty()) {
                    correlativoRepo.save(new Correlativo("NOTA_VENTA", "NI001", 0));
                }
                if (correlativoRepo.findByCodigoAndSerie("NOTA_CREDITO", "NC01").isEmpty()) {
                    correlativoRepo.save(new Correlativo("NOTA_CREDITO", "NC01", 0));
                }

                // COTIZACIONES (Independiente del modo)
                if (correlativoRepo.findByCodigoAndSerie("COTIZACION", "C001").isEmpty()) {
                    correlativoRepo.save(new Correlativo("COTIZACION", "C001", 0));
                }

                // 4. Producto Servicio
                if (productoRepo.findByCodigoInterno("SERV-001").isEmpty()) {
                    Producto servicio = new Producto();
                    servicio.setCodigoInterno("SERV-001");
                    servicio.setCodigoBarra("SERVICIO");
                    servicio.setNombre("SERVICIO GENERAL");
                    servicio.setCategoria("SERVICIOS");
                    servicio.setPrecioCompra(BigDecimal.ZERO);
                    servicio.setPrecioVenta(BigDecimal.ZERO);
                    servicio.setStockActual(999999);
                    servicio.setUnidadMedida("UNIDAD");
                    servicio.setTipoAfectacionIgv("GRAVADO");
                    servicio.setActivo(true);
                    productoRepo.save(servicio);
                }

                // 5. Migración de Roles
                System.out.println(">>> Verificando migración de roles...");
                List<Usuario> todosUsuarios = usuarioRepo.findAll();
                
                for (Usuario usuario : todosUsuarios) {
                    // Solo migrar si no tiene el nuevo rol asignado
                    if (usuario.getRole() == null) {
                        boolean esAdmin = usuario.getRoles().stream()
                                .anyMatch(r -> r.getNombre().equals("ROLE_ADMIN"));
                        boolean esVendedor = usuario.getRoles().stream()
                                .anyMatch(r -> r.getNombre().equals("ROLE_VENDEDOR"));

                        if (esAdmin) {
                            roleRepo.findByNombre("ADMIN").ifPresent(usuario::setRole);
                            System.out.println("  - Usuario '" + usuario.getUsername() + "' migrado a rol ADMIN");
                        } else if (esVendedor) {
                            roleRepo.findByNombre("VENDEDOR").ifPresent(usuario::setRole);
                            System.out.println("  - Usuario '" + usuario.getUsername() + "' migrado a rol VENDEDOR");
                        }
                        // Aquí es donde fallaba antes, ahora con HashSet no fallará
                        usuarioRepo.save(usuario);
                    }
                }
                System.out.println(">>> Inicialización completada con éxito.");
                
                return null;
            });
        };
    }
}