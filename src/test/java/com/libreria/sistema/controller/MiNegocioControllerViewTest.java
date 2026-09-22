package com.libreria.sistema.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ordenes-view;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
class MiNegocioControllerViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void indexRenderizaLecturaFinanciera() throws Exception {
        mockMvc.perform(get("/mi-negocio")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lectura financiera del negocio")))
                .andExpect(content().string(containsString("Ticket promedio")))
                .andExpect(content().string(containsString("Punto equilibrio")))
                .andExpect(content().string(containsString("Guardar reposicion")))
                .andExpect(content().string(containsString("Reposicion y caja")))
                .andExpect(content().string(containsString("Prestamos/bancos")))
                .andExpect(content().string(containsString("Alertas administrativas")))
                .andExpect(content().string(containsString("Inventario estancado")))
                .andExpect(content().string(containsString("Cierre contador")))
                .andExpect(content().string(containsString("/mi-negocio/cierre-mensual/excel")))
                .andExpect(content().string(containsString("/mi-negocio/cierre-mensual/pdf")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void exportaCierreMensualExcel() throws Exception {
        mockMvc.perform(get("/mi-negocio/cierre-mensual/excel")
                        .param("inicio", "2026-09-01")
                        .param("fin", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("cierre_contador_2026-09-01_2026-09-30.xlsx")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void exportaCierreMensualPdf() throws Exception {
        mockMvc.perform(get("/mi-negocio/cierre-mensual/pdf")
                        .param("inicio", "2026-09-01")
                        .param("fin", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("cierre_contador_2026-09-01_2026-09-30.pdf")))
                .andExpect(header().string("Content-Type", startsWith("application/pdf")));
    }
}
