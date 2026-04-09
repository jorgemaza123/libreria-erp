package com.libreria.sistema.config;

import com.libreria.sistema.model.Correlativo;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Rol;
import com.libreria.sistema.model.ServicioCategoria;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.repository.CorrelativoRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.RolRepository;
import com.libreria.sistema.repository.RoleRepository;
import com.libreria.sistema.repository.ServicioCategoriaRepository;
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
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Configuration
@Slf4j
public class DataInitializer {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    CommandLineRunner initData(UsuarioRepository usuarioRepo,
                               RolRepository rolRepo,
                               RoleRepository roleRepo,
                               CorrelativoRepository correlativoRepo,
                               ProductoRepository productoRepo,
                               ServicioCategoriaRepository servicioCategoriaRepo,
                               RolePermissionService rolePermissionService,
                               PasswordEncoder passwordEncoder,
                               PlatformTransactionManager transactionManager) {
        return args -> {
            // Usamos TransactionTemplate para mantener la sesión de BD abierta
            new TransactionTemplate(transactionManager).execute(status -> {
                
                log.info(">>> Inicializando sistema...");

                // =================================================================================
                // 0.1 SANITIZACIÓN DE DATOS (CRÍTICO PARA @Version)
                // =================================================================================

                // Arreglar productos con version NULL
                int productosActualizados = entityManager
                    .createNativeQuery("UPDATE productos SET version = 0 WHERE version IS NULL")
                    .executeUpdate();
                if (productosActualizados > 0) {
                    log.info(">>> SANITIZACIÓN: {} productos con version NULL corregidos.", productosActualizados);
                }

                int clasificacionesActualizadas = entityManager
                    .createNativeQuery("UPDATE productos SET clasificacion = 'MERCADERIA' WHERE clasificacion IS NULL OR TRIM(clasificacion) = ''")
                    .executeUpdate();
                if (clasificacionesActualizadas > 0) {
                    log.info(">>> SANITIZACIÓN: {} productos migrados a clasificacion MERCADERIA.", clasificacionesActualizadas);
                }

                int origenesActualizados = entityManager
                    .createNativeQuery("UPDATE productos SET origen_catalogo = 'GENERAL' WHERE origen_catalogo IS NULL OR TRIM(origen_catalogo) = ''")
                    .executeUpdate();
                if (origenesActualizados > 0) {
                    log.info(">>> SANITIZACIÓN: {} productos migrados a origen GENERAL.", origenesActualizados);
                }

                // NUEVO: Arreglar correlativos con version NULL (SOLUCIÓN A TU ERROR)
                int correlativosActualizados = entityManager
                    .createNativeQuery("UPDATE correlativos SET version = 0 WHERE version IS NULL")
                    .executeUpdate();
                if (correlativosActualizados > 0) {
                    log.info(">>> SANITIZACIÓN: {} correlativos con version NULL corregidos.", correlativosActualizados);
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
                    admin.setPasswordChanged(true); // Forzar cambio en primer login
                    usuarioRepo.save(admin);
                    log.info(">>> USUARIO ADMIN CREADO POR DEFECTO (sin bloqueo)");
                }

                if (usuarioRepo.findByUsername("vendedor").isEmpty()) {
                    Usuario vend = new Usuario();
                    vend.setUsername("vendedor");
                    vend.setPassword(passwordEncoder.encode("1234"));
                    vend.setNombreCompleto("Vendedor de Tienda");
                    vend.setRoles(new HashSet<>(Set.of(rolVendedor)));
                    vend.setActivo(true);
                    vend.setPasswordChanged(false); // Forzar cambio en primer login
                    usuarioRepo.save(vend);
                    log.info(">>> USUARIO VENDEDOR CREADO (debe cambiar contraseña)");
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
                    servicio.setOrigenCatalogo(Producto.ORIGEN_CATALOGO_GENERAL);
                    productoRepo.save(servicio);
                }

                if (productoRepo.findByCodigoInterno("SERV-PERS-001").isEmpty()) {
                    Producto servicioPersonalizado = new Producto();
                    servicioPersonalizado.setCodigoInterno("SERV-PERS-001");
                    servicioPersonalizado.setCodigoBarra("SERV-PERS");
                    servicioPersonalizado.setNombre("SERVICIO PERSONALIZADO");
                    servicioPersonalizado.setCategoria("PERSONALIZADO");
                    servicioPersonalizado.setPrecioCompra(BigDecimal.ZERO);
                    servicioPersonalizado.setPrecioVenta(BigDecimal.ZERO);
                    servicioPersonalizado.setStockActual(999999);
                    servicioPersonalizado.setUnidadMedida("UNIDAD");
                    servicioPersonalizado.setTipoAfectacionIgv("GRAVADO");
                    servicioPersonalizado.setActivo(true);
                    servicioPersonalizado.setTipo("SERVICIO");
                    servicioPersonalizado.setOrigenCatalogo(Producto.ORIGEN_CATALOGO_GENERAL);
                    productoRepo.save(servicioPersonalizado);
                }

                // 4.1 SERVICIOS POS Y PRODUCTOS RÁPIDOS
                log.info(">>> Inicializando Servicios Rápidos del POS...");
                
                // Servicios Intangibles (Sin control de stock)
                crearProductoSiNoExiste(productoRepo, "FOTOCOPIA_BN", "Fotocopia B/N", new BigDecimal("0.10"), "SERVICIO");
                crearProductoSiNoExiste(productoRepo, "IMPRESION_A4", "Impresión A4", new BigDecimal("0.50"), "SERVICIO");
                crearProductoSiNoExiste(productoRepo, "ANILLADO", "Anillado", new BigDecimal("3.50"), "SERVICIO");
                crearProductoSiNoExiste(productoRepo, "SCANNER", "Escaneo Documento", new BigDecimal("1.00"), "SERVICIO");
                crearProductoSiNoExiste(productoRepo, "INTERNET", "Alquiler Internet (Hora)", new BigDecimal("2.00"), "SERVICIO");

                // Productos Rápidos Físicos (Con control de stock)
                crearProductoSiNoExiste(productoRepo, "BOLSA_PLASTICA", "Bolsa Plástica", new BigDecimal("0.10"), "PRODUCTO");
                crearProductoSiNoExiste(productoRepo, "LAPICERO_AZUL", "Lapicero Azul Std", new BigDecimal("1.00"), "PRODUCTO");

                // 4.2 Migración CRM: clientes existentes sin tipo -> CLIENTE
                int clientesMigrados = entityManager
                    .createNativeQuery("UPDATE clientes SET tipo = 'CLIENTE' WHERE tipo IS NULL")
                    .executeUpdate();
                if (clientesMigrados > 0) {
                    log.info(">>> CRM: {} clientes migrados con tipo=CLIENTE", clientesMigrados);
                }

                // 4.3 Categorías de Servicio (para cotizaciones)
                log.info(">>> Inicializando Categorías de Servicio...");
                crearCategoriaSiNoExiste(servicioCategoriaRepo, "SUBLIMACION", "Sublimación", "Sublimación en tazas, polos, etc.", "fas fa-tshirt", 1);
                crearCategoriaSiNoExiste(servicioCategoriaRepo, "COPIAS", "Copias", "Fotocopias B/N y color", "fas fa-copy", 2);
                crearCategoriaSiNoExiste(servicioCategoriaRepo, "IMPRESION", "Impresión", "Impresiones A4, A3, fotos", "fas fa-print", 3);
                crearCategoriaSiNoExiste(servicioCategoriaRepo, "ESCANEO", "Escaneo", "Digitalización de documentos", "fas fa-scanner", 4);
                crearCategoriaSiNoExiste(servicioCategoriaRepo, "SOPORTE_TECNICO", "Soporte Técnico", "Reparación y soporte de equipos", "fas fa-tools", 5);
                crearCategoriaSiNoExiste(servicioCategoriaRepo, "TRAMITES", "Trámites", "Gestión de trámites diversos", "fas fa-file-signature", 6);
                crearCategoriaSiNoExiste(servicioCategoriaRepo, "SOFTWARE", "Software", "Instalación y configuración de software", "fas fa-laptop-code", 7);
                crearCategoriaSiNoExiste(servicioCategoriaRepo, "COSTURA", "Costura", "Servicios de costura y confección", "fas fa-cut", 8);
                crearCategoriaSiNoExiste(servicioCategoriaRepo, "TRABAJOS_ESCOLARES", "Trabajos Escolares", "Maquetas, informes, trabajos", "fas fa-graduation-cap", 9);
                crearCategoriaSiNoExiste(servicioCategoriaRepo, "PAPELERIA", "Papelería", "Encuadernado, anillado, empastado", "fas fa-book", 10);
                crearCategoriaSiNoExiste(servicioCategoriaRepo, "OTRO", "Otro", "Servicios varios", "fas fa-ellipsis-h", 99);

                // 5. Migración de Roles
                log.info(">>> Verificando migración de roles...");
                List<Usuario> todosUsuarios = usuarioRepo.findAll();

                for (Usuario usuario : todosUsuarios) {
                    if (usuario.getRole() == null) {
                        boolean esAdmin = usuario.getRoles().stream()
                                .anyMatch(r -> r.getNombre().equals("ROLE_ADMIN"));
                        boolean esVendedor = usuario.getRoles().stream()
                                .anyMatch(r -> r.getNombre().equals("ROLE_VENDEDOR"));

                        if (esAdmin) {
                            roleRepo.findByNombre("ADMIN").ifPresent(usuario::setRole);
                            log.info("  - Usuario '{}' migrado a rol ADMIN", usuario.getUsername());
                        } else if (esVendedor) {
                            roleRepo.findByNombre("VENDEDOR").ifPresent(usuario::setRole);
                            log.info("  - Usuario '{}' migrado a rol VENDEDOR", usuario.getUsername());
                        }
                        usuarioRepo.save(usuario);
                    }
                }
                log.info(">>> Inicialización completada con éxito.");
                
                return null;
            });
        };
    }

    private void crearCategoriaSiNoExiste(ServicioCategoriaRepository repo, String codigo, String nombre, String descripcion, String icono, int orden) {
        if (repo.findByCodigo(codigo).isEmpty()) {
            ServicioCategoria cat = new ServicioCategoria();
            cat.setCodigo(codigo);
            cat.setNombre(nombre);
            cat.setDescripcion(descripcion);
            cat.setIcono(icono);
            cat.setOrden(orden);
            cat.setActiva(true);
            repo.save(cat);
            log.info(" > Categoría servicio creada: {}", nombre);
        }
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
            log.info(" > Creado item POS: {} [{}]", nombre, tipo);
        } else {
            Producto p = existente.get();
            if (p.getTipo() == null || !p.getTipo().equals(tipo)) {
                p.setTipo(tipo);
                repo.save(p);
                log.info(" > Actualizado tipo de item: {} a {}", nombre, tipo);
            }
        }
    }
}
