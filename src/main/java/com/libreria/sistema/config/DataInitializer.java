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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Configuration
public class DataInitializer {

    @PersistenceContext
    private EntityManager entityManager;

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

                // =================================================================================
                // 0.1 SANITIZACIÓN DE DATOS (CRÍTICO PARA @Version)
                // =================================================================================
                
                // Arreglar productos con version NULL
                int productosActualizados = entityManager
                    .createNativeQuery("UPDATE productos SET version = 0 WHERE version IS NULL")
                    .executeUpdate();
                if (productosActualizados > 0) {
                    System.out.println(">>> SANITIZACIÓN: " + productosActualizados + " productos con version NULL corregidos.");
                }

                // NUEVO: Arreglar correlativos con version NULL (SOLUCIÓN A TU ERROR)
                int correlativosActualizados = entityManager
                    .createNativeQuery("UPDATE correlativos SET version = 0 WHERE version IS NULL")
                    .executeUpdate();
                if (correlativosActualizados > 0) {
                    System.out.println(">>> SANITIZACIÓN: " + correlativosActualizados + " correlativos con version NULL corregidos.");
                }
                // =================================================================================

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
                    vend.setRoles(new HashSet<>(Set.of(rolVendedor)));
                    vend.setActivo(true);
                    usuarioRepo.save(vend);
                    System.out.println(">>> USUARIO VENDEDOR CREADO");
                }

                // 3. Correlativos - Inicialización segura respectando tu lógica Dual
                // Si ya existen en BD, no los toca (respeta la secuencia actual)
                
                // MODO SUNAT
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

                // MODO INTERNO
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

                // COTIZACIONES
                if (correlativoRepo.findByCodigoAndSerie("COTIZACION", "C001").isEmpty()) {
                    correlativoRepo.save(new Correlativo("COTIZACION", "C001", 0));
                }

                // 4. Producto Servicio Genérico (Existente)
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
                    servicio.setTipo("SERVICIO");
                    productoRepo.save(servicio);
                }

                // 4.1 SERVICIOS POS Y PRODUCTOS RÁPIDOS
                System.out.println(">>> Inicializando Servicios Rápidos del POS...");
                
                // Servicios Intangibles (Sin control de stock)
                crearProductoSiNoExiste(productoRepo, "FOTOCOPIA_BN", "Fotocopia B/N", new BigDecimal("0.10"), "SERVICIO");
                crearProductoSiNoExiste(productoRepo, "IMPRESION_A4", "Impresión A4", new BigDecimal("0.50"), "SERVICIO");
                crearProductoSiNoExiste(productoRepo, "ANILLADO", "Anillado", new BigDecimal("3.50"), "SERVICIO");
                crearProductoSiNoExiste(productoRepo, "SCANNER", "Escaneo Documento", new BigDecimal("1.00"), "SERVICIO");
                crearProductoSiNoExiste(productoRepo, "INTERNET", "Alquiler Internet (Hora)", new BigDecimal("2.00"), "SERVICIO");

                // Productos Rápidos Físicos (Con control de stock)
                crearProductoSiNoExiste(productoRepo, "BOLSA_PLASTICA", "Bolsa Plástica", new BigDecimal("0.10"), "PRODUCTO");
                crearProductoSiNoExiste(productoRepo, "LAPICERO_AZUL", "Lapicero Azul Std", new BigDecimal("1.00"), "PRODUCTO");

                // 5. Migración de Roles
                System.out.println(">>> Verificando migración de roles...");
                List<Usuario> todosUsuarios = usuarioRepo.findAll();
                
                for (Usuario usuario : todosUsuarios) {
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
                        usuarioRepo.save(usuario);
                    }
                }
                System.out.println(">>> Inicialización completada con éxito.");
                
                return null;
            });
        };
    }

    private void crearProductoSiNoExiste(ProductoRepository repo, String codigoInterno, String nombre, BigDecimal precio, String tipo) {
        Optional<Producto> existente = repo.findByCodigoInterno(codigoInterno);

        if (existente.isEmpty()) {
            Producto p = new Producto();
            p.setCodigoInterno(codigoInterno);
            p.setCodigoBarra(tipo.equals("SERVICIO") ? "SRV-" + codigoInterno : codigoInterno);
            p.setNombre(nombre);
            p.setPrecioVenta(precio);
            p.setPrecioCompra(BigDecimal.ZERO);
            p.setPrecioMayorista(precio);
            p.setTipo(tipo); 
            p.setActivo(true);
            p.setCategoria(tipo.equals("SERVICIO") ? "SERVICIOS" : "GENERAL");
            p.setDescripcion("Item rápido del sistema");
            p.setTipoAfectacionIgv("GRAVADO");
            p.setStockActual(tipo.equals("SERVICIO") ? 99999 : 100); 
            p.setStockMinimo(5);

            repo.save(p);
            System.out.println(" > Creado item POS: " + nombre + " [" + tipo + "]");
        } else {
            Producto p = existente.get();
            if (p.getTipo() == null || !p.getTipo().equals(tipo)) {
                p.setTipo(tipo);
                repo.save(p);
                System.out.println(" > Actualizado tipo de item: " + nombre + " a " + tipo);
            }
        }
    }
}