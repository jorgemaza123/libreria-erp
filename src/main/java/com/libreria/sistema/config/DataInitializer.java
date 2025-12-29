package com.libreria.sistema.config;

import com.libreria.sistema.model.Correlativo;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Rol;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.repository.CorrelativoRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.RolRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Set;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(UsuarioRepository usuarioRepo, 
                               RolRepository rolRepo, 
                               CorrelativoRepository correlativoRepo,
                               ProductoRepository productoRepo, // <--- ESTO FALTABA
                               PasswordEncoder passwordEncoder) {
        return args -> {
            
            // 1. Roles
            Rol rolAdmin = rolRepo.findByNombre("ROLE_ADMIN").orElseGet(() -> rolRepo.save(new Rol("ROLE_ADMIN")));
            Rol rolVendedor = rolRepo.findByNombre("ROLE_VENDEDOR").orElseGet(() -> rolRepo.save(new Rol("ROLE_VENDEDOR")));

            // 2. Usuario Admin
            if (usuarioRepo.findByUsername("admin").isEmpty()) {
                Usuario admin = new Usuario();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin"));
                admin.setNombreCompleto("Administrador del Sistema");
                admin.setRoles(Set.of(rolAdmin));
                admin.setActivo(true);
                usuarioRepo.save(admin);
                System.out.println(">>> USUARIO ADMIN CREADO");
            }

            // 3. Correlativos
            if(correlativoRepo.findByCodigoAndSerie("BOLETA", "B001").isEmpty()) {
                correlativoRepo.save(new Correlativo("BOLETA", "B001", 0));
            }
            if(correlativoRepo.findByCodigoAndSerie("FACTURA", "F001").isEmpty()) {
                correlativoRepo.save(new Correlativo("FACTURA", "F001", 0));
            }
            if(correlativoRepo.findByCodigoAndSerie("COTIZACION", "C001").isEmpty()) {
                correlativoRepo.save(new Correlativo("COTIZACION", "C001", 0));
                System.out.println(">>> SERIE C001 (COTIZACIONES) CREADA");
            }

            // 4. Crear Producto Comodín para Servicios
            if(productoRepo.findByCodigoInterno("SERV-001").isEmpty()) {
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
                System.out.println(">>> PRODUCTO 'SERVICIO GENERAL' CREADO");
            }
        };
    }
}