package com.libreria.sistema.controller;

import com.libreria.sistema.model.SolicitudProducto;
import com.libreria.sistema.repository.SolicitudProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:solicitudes-actions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
class SolicitudControllerActionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SolicitudProductoRepository solicitudRepository;

    private SolicitudProducto solicitud;

    @BeforeEach
    void prepararSolicitudes() {
        solicitudRepository.deleteAll();

        solicitud = new SolicitudProducto();
        solicitud.setNombreProducto("PLUMON BLANCO");
        solicitud.setContador(4);
        solicitud.setUltimaSolicitud(LocalDateTime.now().minusMinutes(20));
        solicitud.setEstado("PENDIENTE");
        solicitud = solicitudRepository.save(solicitud);
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void exportaPedidosSeleccionadosEnPdfYExcel() throws Exception {
        mockMvc.perform(get("/solicitudes/exportar/pdf")
                        .param("ids", solicitud.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("pedidos_vendedores.pdf")));

        mockMvc.perform(get("/solicitudes/exportar/excel")
                        .param("ids", solicitud.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("pedidos_vendedores.xlsx")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void eliminaPedidosSeleccionados() throws Exception {
        mockMvc.perform(post("/solicitudes/eliminar-masivo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + solicitud.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.eliminados").value(1));
    }
}
