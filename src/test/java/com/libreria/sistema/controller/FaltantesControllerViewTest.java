package com.libreria.sistema.controller;

import com.libreria.sistema.model.SolicitudProducto;
import com.libreria.sistema.repository.SolicitudProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:faltantes-view;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
class FaltantesControllerViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SolicitudProductoRepository solicitudRepository;

    @BeforeEach
    void prepararSolicitudes() {
        solicitudRepository.deleteAll();

        SolicitudProducto solicitud = new SolicitudProducto();
        solicitud.setNombreProducto("CARTULINA DORADA");
        solicitud.setContador(3);
        solicitud.setUltimaSolicitud(LocalDateTime.now().minusHours(1));
        solicitud.setEstado("PENDIENTE");
        solicitudRepository.save(solicitud);
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void faltantesAbreDemandaInsatisfechaConAccionesMasivas() throws Exception {
        Authentication admin = new UsernamePasswordAuthenticationToken(
                "admin",
                "password",
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"));

        mockMvc.perform(get("/faltantes")
                        .principal(admin)
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Demanda Insatisfecha")))
                .andExpect(content().string(containsString("Pedidos de Vendedores")))
                .andExpect(content().string(containsString("CARTULINA DORADA")))
                .andExpect(content().string(containsString("checkAllSolicitudes")))
                .andExpect(content().string(containsString("solicitud-check")))
                .andExpect(content().string(containsString("eliminarSolicitudesSeleccionadas")))
                .andExpect(content().string(containsString("exportarSolicitudesPDF")))
                .andExpect(content().string(containsString("exportarSolicitudesExcel")));
    }
}
