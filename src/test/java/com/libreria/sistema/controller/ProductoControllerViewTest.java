package com.libreria.sistema.controller;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:productos-view;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
class ProductoControllerViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void productosRenderizaDisenadorAvanzadoDeEtiquetas() throws Exception {
        Producto producto = new Producto();
        producto.setCodigoInterno("ETQ-001");
        producto.setCodigoBarra("7750000000011");
        producto.setNombre("Lapicero azul etiqueta");
        producto.setCategoria("UTILES");
        producto.setPrecioCompra(new BigDecimal("0.50"));
        producto.setPrecioVenta(new BigDecimal("1.00"));
        producto.setStockActual(10);
        producto.setStockMinimo(2);
        producto.setActivo(true);
        producto.setTipo("ESTANDAR");
        producto.setClasificacion(Producto.CLASIFICACION_MERCADERIA);
        producto.setOrigenCatalogo(Producto.ORIGEN_CATALOGO_GENERAL);
        producto.setEsLamina(false);
        productoRepository.saveAndFlush(producto);

        mockMvc.perform(get("/productos")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Diseñar e imprimir etiquetas")))
                .andExpect(content().string(containsString("modal-xl")))
                .andExpect(content().string(containsString("etiquetaColorPrecio")))
                .andExpect(content().string(containsString("Agregar otro producto a esta hoja")))
                .andExpect(content().string(containsString("Completar A4")))
                .andExpect(content().string(containsString("etiquetaSaltarA4")))
                .andExpect(content().string(containsString("Ajuste fino")))
                .andExpect(content().string(containsString("recogerOpcionesEtiquetas")))
                .andExpect(content().string(containsString("nombrePersonalizado")))
                .andExpect(content().string(containsString("data-precio-venta")));
    }
}
