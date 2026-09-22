package com.libreria.sistema.controller;

import com.libreria.sistema.model.Kardex;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.KardexRepository;
import com.libreria.sistema.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:productos-revision-view;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
class ProductoRevisionControllerViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private KardexRepository kardexRepository;

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void revisionRenderizaFiltrosYTablaEditable() throws Exception {
        mockMvc.perform(get("/productos/revision")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Revision rapida")))
                .andExpect(content().string(containsString("/productos/revision/api")))
                .andExpect(content().string(containsString("Por revisar")))
                .andExpect(content().string(containsString("js-guardar-producto")))
                .andExpect(content().string(containsString("btnGuardarCambios")))
                .andExpect(content().string(containsString("beforeunload")))
                .andExpect(content().string(containsString("confirmarCambiosPendientes")))
                .andExpect(content().string(containsString("Tienes cambios sin guardar")))
                .andExpect(content().string(containsString("Mismo precio")))
                .andExpect(content().string(containsString("categoriasRevisionList")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void apiFiltraYActualizaProductoConKardex() throws Exception {
        Producto producto = new Producto();
        producto.setCodigoInterno("REV-001");
        producto.setCodigoBarra("REV-BAR-001");
        producto.setNombre("CARTULINA CORAL DEMO");
        producto.setCategoria("PAPELES");
        producto.setPrecioCompra(new BigDecimal("0.50"));
        producto.setPrecioVenta(new BigDecimal("1.00"));
        producto.setStockActual(4);
        producto.setStockMinimo(2);
        producto.setActivo(true);
        producto.setPosRapido(false);
        producto.setTemporadaActiva(false);
        producto.setTipo("ESTANDAR");
        producto.setClasificacion(Producto.CLASIFICACION_MERCADERIA);
        producto.setOrigenCatalogo(Producto.ORIGEN_CATALOGO_GENERAL);
        producto.setEsLamina(false);
        producto = productoRepository.saveAndFlush(producto);

        mockMvc.perform(get("/productos/revision/api")
                        .param("termino", "coral")
                        .param("size", "20")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos[0].nombre").value("CARTULINA CORAL DEMO"))
                .andExpect(jsonPath("$.size").value(20));

        String body = """
                {
                  "codigoInterno": "REV-001",
                  "codigoBarra": "REV-BAR-001",
                  "nombre": "cartulina coral actualizada",
                  "categoria": "papeles especiales",
                  "clasificacion": "MERCADERIA",
                  "tipo": "ESTANDAR",
                  "tags": "coral, cartulina especial",
                  "precioCompra": 0.70,
                  "precioVenta": 1.50,
                  "stockActual": 9,
                  "stockMinimo": 3,
                  "activo": true,
                  "posRapido": true,
                  "temporadaActiva": true
                }
                """;

        mockMvc.perform(post("/productos/revision/api/{id}", producto.getId())
                        .contentType("application/json")
                        .content(body)
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("CARTULINA CORAL ACTUALIZADA"))
                .andExpect(jsonPath("$.categoria").value("PAPELES ESPECIALES"))
                .andExpect(jsonPath("$.stockActual").value(9))
                .andExpect(jsonPath("$.posRapido").value(true))
                .andExpect(jsonPath("$.temporadaActiva").value(true));

        Producto actualizado = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(actualizado.getPrecioVenta()).isEqualByComparingTo("1.50");
        assertThat(actualizado.getStockActual()).isEqualTo(9);

        List<Kardex> movimientos = kardexRepository.findByProductoId(producto.getId(), PageRequest.of(0, 5)).getContent();
        assertThat(movimientos).hasSize(1);
        assertThat(movimientos.get(0).getTipo()).isEqualTo("AJUSTE");
        assertThat(movimientos.get(0).getStockAnterior()).isEqualTo(4);
        assertThat(movimientos.get(0).getStockActual()).isEqualTo(9);
    }
}
