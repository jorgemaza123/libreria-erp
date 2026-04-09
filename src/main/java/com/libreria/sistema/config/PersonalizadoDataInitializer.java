package com.libreria.sistema.config;

import com.libreria.sistema.model.CategoriaAdicionalPersonalizado;
import com.libreria.sistema.model.PresentacionCompra;
import com.libreria.sistema.repository.CategoriaAdicionalPersonalizadoRepository;
import com.libreria.sistema.repository.PresentacionCompraRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Locale;

@Configuration
public class PersonalizadoDataInitializer {

    @Bean
    CommandLineRunner initPersonalizadoBase(CategoriaAdicionalPersonalizadoRepository categoriaRepository,
                                            PresentacionCompraRepository presentacionRepository) {
        return args -> {
            seedCategoria(categoriaRepository, "CAJA_BOLSA", "CAJA / BOLSA", "Empaques, cajas y bolsas", 10);
            seedCategoria(categoriaRepository, "CHOCOLATES_DULCES", "CHOCOLATES / DULCES", "Dulces, chocolates y snacks", 20);
            seedCategoria(categoriaRepository, "FLORES_ROSAS", "FLORES / ROSAS", "Flores, rosas y follaje", 30);
            seedCategoria(categoriaRepository, "ENVOLTURA", "ENVOLTURA", "Papel, celofán y acabados", 40);
            seedCategoria(categoriaRepository, "GLOBO", "GLOBO", "Globos y accesorios", 50);
            seedCategoria(categoriaRepository, "IMPRESION_EXTRA", "IMPRESION EXTRA", "Impresiones o acabados adicionales", 60);
            seedCategoria(categoriaRepository, "ENVIO", "ENVIO", "Delivery y despacho", 70);
            seedCategoria(categoriaRepository, "DEDICATORIA_TARJETA", "DEDICATORIA / TARJETA", "Tarjetas, mensajes y dedicatorias", 80);
            seedCategoria(categoriaRepository, "ETIQUETA_NOMBRE", "ETIQUETA / NOMBRE", "Etiquetas, nombres y rotulados", 90);

            seedPresentacion(presentacionRepository, "UNIDAD", "UNIDAD", BigDecimal.ONE, false, true, 10);
            seedPresentacion(presentacionRepository, "CUARTO DE DOCENA", "UNIDAD", new BigDecimal("3"), false, false, 20);
            seedPresentacion(presentacionRepository, "MEDIA DOCENA", "UNIDAD", new BigDecimal("6"), false, false, 30);
            seedPresentacion(presentacionRepository, "DOCENA", "UNIDAD", new BigDecimal("12"), false, false, 40);
            seedPresentacion(presentacionRepository, "CAJA", "UNIDAD", BigDecimal.ONE, false, false, 50);
            seedPresentacion(presentacionRepository, "BOLSA", "UNIDAD", BigDecimal.ONE, true, false, 60);
            seedPresentacion(presentacionRepository, "PAQUETE", "UNIDAD", BigDecimal.ONE, false, false, 70);
            seedPresentacion(presentacionRepository, "ROLLO", "UNIDAD", BigDecimal.ONE, true, false, 80);
            seedPresentacion(presentacionRepository, "KILO", "GR", new BigDecimal("1000"), true, false, 90);
            seedPresentacion(presentacionRepository, "MEDIO KILO", "GR", new BigDecimal("500"), true, false, 100);
            seedPresentacion(presentacionRepository, "CUARTO KILO", "GR", new BigDecimal("250"), true, false, 110);
            seedPresentacion(presentacionRepository, "LITRO", "ML", new BigDecimal("1000"), true, false, 120);
            seedPresentacion(presentacionRepository, "MEDIO LITRO", "ML", new BigDecimal("500"), true, false, 130);
            seedPresentacion(presentacionRepository, "METRO", "CM", new BigDecimal("100"), true, false, 140);
        };
    }

    private void seedCategoria(CategoriaAdicionalPersonalizadoRepository repository,
                               String codigo,
                               String nombre,
                               String descripcion,
                               int orden) {
        repository.findByCodigo(codigo)
                .orElseGet(() -> {
                    CategoriaAdicionalPersonalizado categoria = new CategoriaAdicionalPersonalizado();
                    categoria.setCodigo(codigo);
                    categoria.setNombre(nombre);
                    categoria.setDescripcion(descripcion);
                    categoria.setOrden(orden);
                    categoria.setActivo(true);
                    return repository.save(categoria);
                });
    }

    private void seedPresentacion(PresentacionCompraRepository repository,
                                  String nombre,
                                  String unidadMedida,
                                  BigDecimal factor,
                                  boolean permiteDecimal,
                                  boolean predeterminada,
                                  int orden) {
        boolean existe = repository.findAll().stream().anyMatch(p ->
                PresentacionCompra.TIPO_INSUMO_PERSONALIZADO.equalsIgnoreCase(p.getTipoCatalogo())
                        && p.getProducto() == null
                        && p.getInsumoPersonalizado() == null
                        && nombre.equalsIgnoreCase(p.getNombrePresentacion() != null
                        ? p.getNombrePresentacion().trim().toUpperCase(Locale.ROOT)
                        : ""));

        if (existe) {
            return;
        }

        PresentacionCompra presentacion = new PresentacionCompra();
        presentacion.setTipoCatalogo(PresentacionCompra.TIPO_INSUMO_PERSONALIZADO);
        presentacion.setNombrePresentacion(nombre);
        presentacion.setUnidadMedidaPresentacion(unidadMedida);
        presentacion.setFactorBase(factor);
        presentacion.setPermiteDecimal(permiteDecimal);
        presentacion.setPredeterminada(predeterminada);
        presentacion.setActiva(true);
        presentacion.setOrden(orden);
        repository.save(presentacion);
    }
}
