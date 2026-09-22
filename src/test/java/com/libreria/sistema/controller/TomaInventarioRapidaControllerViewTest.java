package com.libreria.sistema.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:toma-inventario-rapida-view;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
class TomaInventarioRapidaControllerViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void regularizacionRapidaRenderizaInicioYModoConteo() throws Exception {
        mockMvc.perform(get("/toma-inventario/rapida")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Regularizacion Rapida")))
                .andExpect(content().string(containsString("No hay toma abierta")))
                .andExpect(content().string(containsString("Iniciar regularizacion")));

        mockMvc.perform(post("/toma-inventario/rapida/iniciar")
                        .param("observaciones", "Test regularizacion rapida")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/toma-inventario/rapida"));

        mockMvc.perform(get("/toma-inventario/rapida")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Buscar o escanear")))
                .andExpect(content().string(containsString("Stock fisico real")))
                .andExpect(content().string(containsString("Conteo 1")))
                .andExpect(content().string(containsString("Conteo 2")))
                .andExpect(content().string(containsString("Segundo conteo pendiente")))
                .andExpect(content().string(containsString("Guardar datos")))
                .andExpect(content().string(containsString("Crear variante")))
                .andExpect(content().string(containsString("Modo conteo ciego")))
                .andExpect(content().string(containsString("Productos sospechosos")))
                .andExpect(content().string(containsString("Variantes por lote")))
                .andExpect(content().string(containsString("Mercaderia vendible")))
                .andExpect(content().string(containsString("Desconocido")))
                .andExpect(content().string(containsString("Aplicar correcciones")))
                .andExpect(content().string(containsString("Correccion pendiente")))
                .andExpect(content().string(containsString("Guardar zonas")))
                .andExpect(content().string(containsString("Escanear con camara")))
                .andExpect(content().string(containsString("Escanear foto")))
                .andExpect(content().string(containsString("html5-qrcode")))
                .andExpect(content().string(containsString("Html5Qrcode")))
                .andExpect(content().string(containsString("BarcodeDetector")))
                .andExpect(content().string(containsString("URL segura HTTPS/8443")))
                .andExpect(content().string(containsString("/toma-inventario/rapida/movil")))
                .andExpect(content().string(containsString("/toma-inventario/rapida/etiquetas")))
                .andExpect(content().string(containsString("Armar etiquetas A4 de regularizacion")))
                .andExpect(content().string(containsString("/toma-inventario/rapida/api/etiquetas/productos")))
                .andExpect(content().string(containsString("/toma-inventario/rapida/etiquetas/imprimir")))
                .andExpect(content().string(containsString("Completar A4")))
                .andExpect(content().string(containsString("Casillas usadas")))
                .andExpect(content().string(containsString("rapidLabelItems")))
                .andExpect(content().string(containsString("/toma-inventario/rapida/exportar/excel")))
                .andExpect(content().string(containsString("/toma-inventario/rapida/api/deshacer")))
                .andExpect(content().string(containsString("/toma-inventario/rapida/api/aplicar-precios")))
                .andExpect(content().string(containsString("/toma-inventario/rapida/api/zonas/estado")))
                .andExpect(content().string(containsString("/toma-inventario/rapida/api/sospechosos")))
                .andExpect(content().string(containsString("Crear producto con esta busqueda")))
                .andExpect(content().string(containsString("/toma-inventario/rapida/api/buscar")))
                .andExpect(content().string(containsString("Enter guarda y vuelve al buscador")));

        mockMvc.perform(get("/toma-inventario/rapida/movil")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rapid-mobile-body")))
                .andExpect(content().string(containsString("Modo conteo ciego")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void guardarDatosProductoAplicaPrecioEnProductoReal() throws Exception {
        Producto producto = productoRepository.saveAndFlush(productoDemo("REG-PRECIO-001", "LAPICERO PRECIO RAPIDO"));

        mockMvc.perform(post("/toma-inventario/rapida/iniciar")
                        .param("observaciones", "Test precio aplicado")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().is3xxRedirection());

        String request = """
                {
                  "nombre": "LAPICERO PRECIO RAPIDO",
                  "categoria": "UTILES",
                  "marca": "FABER",
                  "color": "AZUL",
                  "modelo": "STD",
                  "tipo": "ESTANDAR",
                  "clasificacion": "MERCADERIA",
                  "precioCompra": 0.90,
                  "precioVenta": 2.50
                }
                """;

        mockMvc.perform(post("/toma-inventario/rapida/api/producto/" + producto.getId() + "/datos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.producto.precioVenta").value(2.50))
                .andExpect(jsonPath("$.producto.correccionAplicada").value(true));

        Producto actualizado = productoRepository.findById(producto.getId()).orElseThrow();
        assertEquals(0, actualizado.getPrecioVenta().compareTo(new BigDecimal("2.50")));
        assertEquals(0, actualizado.getPrecioCompra().compareTo(new BigDecimal("0.90")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void crearProductoDesdeRegularizacionGuardaStockInicial() throws Exception {
        mockMvc.perform(post("/toma-inventario/rapida/iniciar")
                        .param("observaciones", "Test stock inicial")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().is3xxRedirection());

        String request = """
                {
                  "nombre": "CARTULINA VARIANTE STOCK TEST",
                  "codigoInterno": "REG-STOCK-001",
                  "categoria": "PAPELES",
                  "marca": "GENERICO",
                  "color": "VERDE",
                  "modelo": "A4",
                  "tipo": "ESTANDAR",
                  "clasificacion": "MERCADERIA",
                  "precioCompra": 0.40,
                  "precioVenta": 1.00,
                  "cantidadFisica": 7,
                  "zona": "MOSTRADOR"
                }
                """;

        MvcResult result = mockMvc.perform(post("/toma-inventario/rapida/api/producto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.producto.stockSistema").value(7))
                .andExpect(jsonPath("$.producto.stockFisico").value(7))
                .andReturn();

        long productoId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("producto").path("productoId").asLong();
        assertTrue(productoId > 0);
        Producto creado = productoRepository.findById(productoId).orElseThrow();
        assertEquals(7, creado.getStockActual());
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void sospechososDetectaDuplicadosPorSimilitudReal() throws Exception {
        productoRepository.saveAndFlush(productoDemo("DUP-LAPIZ-001", "LAPIZ FABER ROJO"));
        productoRepository.saveAndFlush(productoDemo("DUP-LAPIS-001", "LAPIS FABER ROJO"));

        mockMvc.perform(post("/toma-inventario/rapida/iniciar")
                        .param("observaciones", "Test duplicados fuzzy")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/toma-inventario/rapida/api/sospechosos")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItem("LAPIZ FABER ROJO")))
                .andExpect(jsonPath("$[*].nombre", hasItem("LAPIS FABER ROJO")))
                .andExpect(jsonPath("$[*].alertas[*]", hasItem("DUPLICADO PARECIDO")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void etiquetasRapidasListanProductosYGeneranPdfA4() throws Exception {
        mockMvc.perform(post("/toma-inventario/rapida/iniciar")
                        .param("observaciones", "Test etiquetas rapidas")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().is3xxRedirection());

        MvcResult listado = mockMvc.perform(get("/toma-inventario/rapida/api/etiquetas/productos")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos").isArray())
                .andReturn();

        JsonNode productos = objectMapper.readTree(listado.getResponse().getContentAsString()).path("productos");
        if (productos.size() == 0) {
            throw new AssertionError("La toma abierta debe exponer productos para etiquetas");
        }
        long productoId = productos.get(0).path("productoId").asLong();
        String request = """
                {
                  "productoIds": [%d],
                  "cantidad": 1,
                  "formato": "A4",
                  "plantilla": "UTIL_CHICO",
                  "columnasA4": 3,
                  "filasA4": 8,
                  "saltarEtiquetasA4": 0,
                  "margenHorizontalMm": 8,
                  "margenVerticalMm": 8,
                  "productos": [{
                    "productoId": %d,
                    "cantidad": 2,
                    "nombrePersonalizado": "Etiqueta regularizacion",
                    "precioPersonalizado": 1.50
                  }]
                }
                """.formatted(productoId, productoId);

        mockMvc.perform(post("/toma-inventario/rapida/etiquetas/imprimir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
    }

    private Producto productoDemo(String codigo, String nombre) {
        Producto producto = new Producto();
        producto.setCodigoInterno(codigo);
        producto.setCodigoBarra(codigo + "-BAR");
        producto.setNombre(nombre);
        producto.setCategoria("UTILES");
        producto.setMarca("FABER");
        producto.setColor("ROJO");
        producto.setPrecioCompra(new BigDecimal("0.50"));
        producto.setPrecioVenta(new BigDecimal("1.00"));
        producto.setStockActual(10);
        producto.setStockMinimo(2);
        producto.setActivo(true);
        producto.setTipo("ESTANDAR");
        producto.setClasificacion(Producto.CLASIFICACION_MERCADERIA);
        producto.setOrigenCatalogo(Producto.ORIGEN_CATALOGO_GENERAL);
        producto.setEsLamina(false);
        return producto;
    }
}
