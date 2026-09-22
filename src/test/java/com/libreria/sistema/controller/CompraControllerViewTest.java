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
        "spring.datasource.url=jdbc:h2:mem:compras-view;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
class CompraControllerViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void nuevaCompraRenderizaCosteoRealDeFaseTres() throws Exception {
        mockMvc.perform(get("/compras/nueva")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gasto repartido")))
                .andExpect(content().string(containsString("Costo real/u")))
                .andExpect(content().string(containsString("Costo promedio")))
                .andExpect(content().string(containsString("Precio minimo")))
                .andExpect(content().string(containsString("Precio sugerido")))
                .andExpect(content().string(containsString("calcularCosteoCarrito")))
                .andExpect(content().string(containsString("sumarItemCarrito")))
                .andExpect(content().string(containsString("REPOSICION_PREFILL")))
                .andExpect(content().string(containsString("compraQuickCargarReposicionPrefill")))
                .andExpect(content().string(containsString("umbral de alerta configurado")))
                .andExpect(content().string(containsString("Proveedor ant.")))
                .andExpect(content().string(containsString("data-stock-actual")));
    }
}
