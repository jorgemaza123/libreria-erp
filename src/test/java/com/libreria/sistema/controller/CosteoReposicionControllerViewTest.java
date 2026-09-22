package com.libreria.sistema.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:costeo-reposicion-view;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
class CosteoReposicionControllerViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void costeoRenderizaReporteContableYReglas() throws Exception {
        mockMvc.perform(get("/costeo")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Costeo y Precios")))
                .andExpect(content().string(containsString("Regla de costo")))
                .andExpect(content().string(containsString("Sugerido")))
                .andExpect(content().string(containsString("Excel contador")))
                .andExpect(content().string(containsString("Decisión de precios por producto")))
                .andExpect(content().string(containsString("Aplicar seleccionados")))
                .andExpect(content().string(containsString("Historial de costos")))
                .andExpect(content().string(containsString("Productos vendidos debajo del minimo")))
                .andExpect(content().string(containsString("Historial de cambios de precio")))
                .andExpect(content().string(containsString("/costeo/productos/aplicar-precios-lote")))
                .andExpect(content().string(containsString("/historial")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void reposicionRenderizaTablaVivaYGanancia() throws Exception {
        mockMvc.perform(get("/reposicion")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reposición y Ganancia")))
                .andExpect(content().string(containsString("Separar reposición")))
                .andExpect(content().string(containsString("Ganancia neta est")))
                .andExpect(content().string(containsString("Tabla viva para comprar")))
                .andExpect(content().string(containsString("Crear compra")))
                .andExpect(content().string(containsString("Seleccionar pendientes visibles")))
                .andExpect(content().string(containsString("Sug. compra")))
                .andExpect(content().string(containsString("repo-buy-block")))
                .andExpect(content().string(containsString("Presentación")))
                .andExpect(content().string(containsString("Excel agrupado")))
                .andExpect(content().string(containsString("Qué se vendió en el periodo")));
    }
}
