package com.libreria.sistema.controller;

import com.libreria.sistema.model.OrdenServicio;
import com.libreria.sistema.repository.OrdenServicioRepository;
import com.libreria.sistema.repository.ServicioCategoriaRepository;
import com.libreria.sistema.service.CajaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class OrdenServicioControllerViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrdenServicioRepository ordenServicioRepository;

    @Autowired
    private ServicioCategoriaRepository servicioCategoriaRepository;

    @Autowired
    private CajaService cajaService;

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void formularioRenderizaTiposYRecordatorio() throws Exception {
        mockMvc.perform(get("/ordenes/nueva")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nuevo Trabajo / Pedido")))
                .andExpect(content().string(containsString("listaServicios")))
                .andExpect(content().string(containsString("fechaRecordatorio")))
                .andExpect(content().string(containsString("recordatorioNota")))
                .andExpect(content().string(containsString("Guardar y entregar")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void listaRenderizaBandejaDeTrabajos() throws Exception {
        OrdenServicio orden = new OrdenServicio();
        orden.setTipoServicio("MAQUETA");
        orden.setTituloTrabajo("Maqueta demo");
        orden.setClienteNombre("Cliente prueba");
        orden.setClienteTelefono("999999999");
        orden.setEstado("EN_PROCESO");
        orden.setPrioridad("ALTA");
        orden.setFechaEntregaEstimada(LocalDate.now().plusDays(1));
        orden.setTotal(new BigDecimal("25.00"));
        orden.setACuenta(new BigDecimal("10.00"));
        orden.setSaldo(new BigDecimal("15.00"));
        ordenServicioRepository.save(orden);

        mockMvc.perform(get("/ordenes/lista")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trabajos y Pedidos")))
                .andExpect(content().string(containsString("tablaOrdenes")))
                .andExpect(content().string(containsString("Maqueta demo")))
                .andExpect(content().string(containsString("EN PROCESO")))
                .andExpect(content().string(containsString("ALTA")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void listaMuestraTrabajosMasRecientesPrimero() throws Exception {
        OrdenServicio antiguo = ordenParaLista("Trabajo antiguo listado");
        OrdenServicio reciente = ordenParaLista("Trabajo reciente listado");

        ordenServicioRepository.saveAndFlush(antiguo);
        ordenServicioRepository.saveAndFlush(reciente);

        antiguo.setFechaRecepcion(LocalDateTime.now().minusDays(1));
        reciente.setFechaRecepcion(LocalDateTime.now());
        ordenServicioRepository.saveAndFlush(antiguo);
        ordenServicioRepository.saveAndFlush(reciente);

        String html = mockMvc.perform(get("/ordenes/lista")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("Trabajo reciente listado", "Trabajo antiguo listado");
        assertThat(html.indexOf("Trabajo reciente listado"))
                .isLessThan(html.indexOf("Trabajo antiguo listado"));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void guardarOrdenCreaTipoLibreYRecordatorio() throws Exception {
        LocalDate entrega = LocalDate.now().plusDays(2);
        LocalDate recordatorio = LocalDate.now();
        String body = """
                {
                  "tipoServicio": "campaña flores amarillas",
                  "tituloTrabajo": "Pedido decoracion colegio",
                  "clienteNombre": "Cliente prueba",
                  "clienteTelefono": "999999999",
                  "fechaEntrega": "%s",
                  "fechaRecordatorio": "%s",
                  "recordatorioActivo": true,
                  "recordatorioNota": "Comprar lazos y revisar entrega",
                  "prioridad": "ALTA",
                  "aCuenta": 0,
                  "items": [
                    { "descripcion": "Preparacion de pedido", "costo": 35.50 }
                  ]
                }
                """.formatted(entrega, recordatorio);

        mockMvc.perform(post("/ordenes/api/guardar")
                        .contentType("application/json")
                        .content(body)
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.saldo").value(35.50));

        assertThat(servicioCategoriaRepository.findByCodigo("CAMPANA_FLORES_AMARILLAS")).isPresent();
        OrdenServicio orden = ordenServicioRepository.findAll().stream()
                .filter(o -> "Pedido decoracion colegio".equals(o.getTituloTrabajo()))
                .findFirst()
                .orElseThrow();
        assertThat(orden.getTipoServicio()).isEqualTo("CAMPANA_FLORES_AMARILLAS");
        assertThat(orden.getFechaRecordatorio()).isEqualTo(recordatorio);
        assertThat(orden.getRecordatorioActivo()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void guardarOrdenMantieneAdelantoInicialCompleto() throws Exception {
        abrirCajaSiHaceFalta();

        String body = """
                {
                  "tipoServicio": "maqueta escolar",
                  "tituloTrabajo": "Pedido con adelanto alto",
                  "clienteNombre": "Cliente adelanto",
                  "clienteTelefono": "999999999",
                  "prioridad": "NORMAL",
                  "aCuenta": 60.00,
                  "metodoPago": "YAPE",
                  "items": [
                    { "descripcion": "Trabajo completo", "costo": 80.00 }
                  ]
                }
                """;

        mockMvc.perform(post("/ordenes/api/guardar")
                        .contentType("application/json")
                        .content(body)
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.abonado").value(60.00))
                .andExpect(jsonPath("$.saldo").value(20.00));

        OrdenServicio orden = ordenServicioRepository.findAll().stream()
                .filter(o -> "Pedido con adelanto alto".equals(o.getTituloTrabajo()))
                .findFirst()
                .orElseThrow();
        assertThat(orden.getACuenta()).isEqualByComparingTo("60.00");
        assertThat(orden.getSaldo()).isEqualByComparingTo("20.00");
        assertThat(orden.getPagos()).hasSize(1);
        assertThat(orden.getPagos().get(0).getMonto()).isEqualByComparingTo("60.00");
        assertThat(orden.getPagos().get(0).getMetodoPago()).isEqualTo("YAPE");
    }

    private OrdenServicio ordenParaLista(String titulo) {
        OrdenServicio orden = new OrdenServicio();
        orden.setTipoServicio("MAQUETA");
        orden.setTituloTrabajo(titulo);
        orden.setClienteNombre("Cliente prueba");
        orden.setClienteTelefono("999999999");
        orden.setEstado("PENDIENTE");
        orden.setPrioridad("NORMAL");
        orden.setFechaEntregaEstimada(LocalDate.now().plusDays(1));
        orden.setTotal(new BigDecimal("10.00"));
        orden.setACuenta(BigDecimal.ZERO);
        orden.setSaldo(new BigDecimal("10.00"));
        return orden;
    }

    private void abrirCajaSiHaceFalta() {
        if (cajaService.obtenerSesionActiva().isPresent()) {
            return;
        }
        cajaService.abrirCaja(new BigDecimal("1.00"));
    }
}
