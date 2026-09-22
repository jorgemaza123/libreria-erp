package com.libreria.sistema.controller;

import com.libreria.sistema.model.DetalleVenta;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.SesionCaja;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.repository.DetalleVentaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.SesionCajaRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import com.libreria.sistema.repository.VentaRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ventas-view;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
class VentaControllerViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SesionCajaRepository sesionCajaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private DetalleVentaRepository detalleVentaRepository;

    @BeforeEach
    void abrirCajaParaAdmin() {
        Usuario admin = usuarioRepository.findByUsername("admin").orElseThrow();
        if (sesionCajaRepository.findByUsuarioAndEstado(admin, "ABIERTA").isEmpty()) {
            SesionCaja sesion = new SesionCaja();
            sesion.setUsuario(admin);
            sesion.setMontoInicial(new BigDecimal("100.00"));
            sesion.setEstado("ABIERTA");
            sesionCajaRepository.save(sesion);
        }
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void nuevaVentaRenderizaAtajosRapidosYVuelto() throws Exception {
        Authentication admin = new UsernamePasswordAuthenticationToken(
                "admin",
                "password",
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"));

        mockMvc.perform(get("/ventas/nueva")
                        .principal(admin)
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ALT+1")))
                .andExpect(content().string(containsString("ALT+1-0")))
                .andExpect(content().string(containsString("F6")))
                .andExpect(content().string(containsString("F7")))
                .andExpect(content().string(containsString("F5")))
                .andExpect(content().string(containsString("modalConfigRapidosPos")))
                .andExpect(content().string(containsString("guardarConfigRapidosPos")))
                .andExpect(content().string(containsString("Impresion rapida")))
                .andExpect(content().string(containsString("modalImpresionRapida")))
                .andExpect(content().string(containsString("servicioVariablePosId")))
                .andExpect(content().string(containsString("agregarImpresionFlexible")))
                .andExpect(content().string(containsString("Alt+B")))
                .andExpect(content().string(containsString("Vuelto opcional")))
                .andExpect(content().string(containsString("swalMontoRecibido")))
                .andExpect(content().string(containsString("calcularVueltoAsistente")))
                .andExpect(content().string(containsString("descripcionProductoParaVenta")))
                .andExpect(content().string(containsString("textoLineaVenta")))
                .andExpect(content().string(containsString("manejarAtajoProductoRapido")));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void productosRapidosSeOrdenanPorVentasRecientesYTendencia() throws Exception {
        Producto producto = new Producto();
        producto.setCodigoInterno("TREND-CART-001");
        producto.setCodigoBarra("7750000000011");
        producto.setNombre("CARTULINA NEON TEST");
        producto.setCategoria("UTILES ESCOLARES");
        producto.setPrecioCompra(new BigDecimal("0.60"));
        producto.setPrecioVenta(new BigDecimal("1.20"));
        producto.setStockActual(25);
        producto.setStockMinimo(5);
        producto.setActivo(true);
        producto.setTipo("ESTANDAR");
        producto.setClasificacion(Producto.CLASIFICACION_MERCADERIA);
        producto.setOrigenCatalogo(Producto.ORIGEN_CATALOGO_GENERAL);
        producto.setEsLamina(false);
        producto = productoRepository.saveAndFlush(producto);

        Venta venta = new Venta();
        venta.setTipoComprobante("NOTA_VENTA");
        venta.setSerie("NV01");
        venta.setNumero(901);
        venta.setFechaEmision(LocalDate.now());
        venta.setFormaPago("Contado");
        venta.setEstado("EMITIDO");
        venta.setEntregaPendiente(false);
        venta.setTotalGravada(new BigDecimal("4.80"));
        venta.setTotalIgv(BigDecimal.ZERO);
        venta.setTotal(new BigDecimal("4.80"));
        venta = ventaRepository.saveAndFlush(venta);

        DetalleVenta detalle = new DetalleVenta();
        detalle.setVenta(venta);
        detalle.setProducto(producto);
        detalle.setDescripcion(producto.getNombre());
        detalle.setCantidad(new BigDecimal("4"));
        detalle.setPrecioUnitario(new BigDecimal("1.20"));
        detalle.setValorUnitario(new BigDecimal("1.20"));
        detalle.setSubtotal(new BigDecimal("4.80"));
        detalleVentaRepository.saveAndFlush(detalle);

        Authentication admin = new UsernamePasswordAuthenticationToken(
                "admin",
                "password",
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"));

        mockMvc.perform(get("/ventas/nueva")
                        .principal(admin)
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CARTULINA NEON TEST")))
                .andExpect(content().string(containsString("Tendencia: Cartulina")))
                .andExpect(content().string(containsString("Orden automatico por temporada, ventas 7/30 dias y campaña.")));
    }
}
