package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tests unitarios para VentaService.
 *
 * Usa JUnit 5 (@ExtendWith) y Mockito para simular dependencias.
 *
 * Casos cubiertos:
 * 1. Venta exitosa con stock suficiente y caja abierta
 * 2. Venta fallida por stock insuficiente (debe lanzar excepcion sin guardar)
 * 3. Venta de servicios (no valida stock)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VentaServiceTest {

    // =====================================================
    //  MOCKS DE REPOSITORIOS Y SERVICIOS
    // =====================================================
    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private VentaRepository ventaRepository;

    @Mock
    private KardexRepository kardexRepository;

    @Mock
    private CorrelativoRepository correlativoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private AmortizacionRepository amortizacionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CajaService cajaService;

    @Mock
    private FacturacionElectronicaService facturacionService;

    @Mock
    private ConfiguracionService configuracionService;

    @InjectMocks
    private VentaService ventaService;

    // =====================================================
    //  DATOS DE PRUEBA
    // =====================================================
    private VentaDTO ventaDTO;
    private Producto productoConStock;
    private Producto productoSinStock;
    private Producto productoServicio;
    private Cliente cliente;
    private Correlativo correlativo;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        // Configurar contexto de seguridad (simula usuario autenticado)
        configurarUsuarioAutenticado("vendedor", "ROLE_VENDEDOR");

        // Producto con stock suficiente
        productoConStock = new Producto();
        productoConStock.setId(1L);
        productoConStock.setNombre("Cuaderno A4");
        productoConStock.setCodigoInterno("CUAD-001");
        productoConStock.setPrecioVenta(new BigDecimal("5.00"));
        productoConStock.setStockActual(100);
        productoConStock.setTipo("PRODUCTO");
        productoConStock.setTipoAfectacionIgv("GRAVADO");
        productoConStock.setUnidadMedida("NIU");

        // Producto sin stock
        productoSinStock = new Producto();
        productoSinStock.setId(2L);
        productoSinStock.setNombre("Lapicero Rojo");
        productoSinStock.setCodigoInterno("LAP-002");
        productoSinStock.setPrecioVenta(new BigDecimal("1.50"));
        productoSinStock.setStockActual(0); // SIN STOCK
        productoSinStock.setTipo("PRODUCTO");
        productoSinStock.setTipoAfectacionIgv("GRAVADO");
        productoSinStock.setUnidadMedida("NIU");

        // Producto tipo servicio (no valida stock)
        productoServicio = new Producto();
        productoServicio.setId(3L);
        productoServicio.setNombre("Impresion A4");
        productoServicio.setCodigoInterno("SERV-001");
        productoServicio.setPrecioVenta(new BigDecimal("0.50"));
        productoServicio.setStockActual(99999);
        productoServicio.setTipo("SERVICIO"); // Es servicio
        productoServicio.setTipoAfectacionIgv("GRAVADO");
        productoServicio.setUnidadMedida("NIU");

        // Cliente
        cliente = new Cliente();
        cliente.setId(1L);
        cliente.setNumeroDocumento("12345678");
        cliente.setNombreRazonSocial("Cliente Test");
        cliente.setTipoDocumento("1");
        cliente.setDireccion("Av. Test 123");

        // Correlativo
        correlativo = new Correlativo("BOLETA", "B001", 100);

        // Usuario
        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setUsername("vendedor");
        usuario.setNombreCompleto("Vendedor Test");

        // DTO de venta base
        ventaDTO = crearVentaDTOBase();
    }

    /**
     * Configura un usuario autenticado en el SecurityContext
     */
    private void configurarUsuarioAutenticado(String username, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                username,
                null,
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Crea un DTO de venta base para las pruebas
     */
    private VentaDTO crearVentaDTOBase() {
        VentaDTO dto = new VentaDTO();
        dto.setClienteNombre("Cliente Test");
        dto.setClienteDocumento("12345678");
        dto.setClienteDireccion("Av. Test 123");
        dto.setTipoComprobante("BOLETA");
        dto.setFormaPago("CONTADO");
        dto.setMetodoPago("EFECTIVO");
        return dto;
    }

    /**
     * Crea un item de detalle para el DTO
     */
    private VentaDTO.DetalleDTO crearDetalleDTO(Long productoId, int cantidad, BigDecimal precio) {
        VentaDTO.DetalleDTO detalle = new VentaDTO.DetalleDTO();
        detalle.setProductoId(productoId);
        detalle.setCantidad(new BigDecimal(cantidad));
        detalle.setPrecioVenta(precio);
        return detalle;
    }

    /**
     * Configura los mocks comunes para una venta exitosa
     */
    private void configurarMocksComunes() {
        // Configuracion de facturacion
        when(facturacionService.isFacturacionElectronicaActiva()).thenReturn(false);
        when(facturacionService.obtenerSerie(anyString(), anyBoolean())).thenReturn("I001");

        // Configuracion de IGV
        when(configuracionService.getIgvFactor()).thenReturn(new BigDecimal("1.18"));
        when(configuracionService.getIgvPorcentaje()).thenReturn(new BigDecimal("18"));

        // Correlativo
        when(correlativoRepository.findByCodigoAndSerieWithLock(anyString(), anyString()))
                .thenReturn(Optional.of(correlativo));

        // Cliente
        when(clienteRepository.findByNumeroDocumento(anyString()))
                .thenReturn(Optional.of(cliente));

        // Usuario
        when(usuarioRepository.findByUsername(anyString()))
                .thenReturn(Optional.of(usuario));

        // Venta guardada
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta v = invocation.getArgument(0);
            v.setId(1L);
            return v;
        });
    }

    // =====================================================
    //  TESTS DE EXITO
    // =====================================================
    @Nested
    @DisplayName("Casos de Exito")
    class CasosDeExito {

        @Test
        @DisplayName("Debe crear venta exitosamente con stock suficiente")
        void crearVenta_ConStockSuficiente_DebeGuardarVenta() {
            // ARRANGE
            configurarMocksComunes();

            // Agregar item con stock suficiente
            ventaDTO.setItems(List.of(
                    crearDetalleDTO(1L, 5, new BigDecimal("5.00"))
            ));

            // Mock del producto con lock (stock = 100, pedimos 5)
            when(productoRepository.findByIdWithLock(1L))
                    .thenReturn(Optional.of(productoConStock));

            // ACT
            Map<String, Object> resultado = ventaService.crearVenta(ventaDTO);

            // ASSERT
            assertNotNull(resultado, "El resultado no debe ser null");
            assertNotNull(resultado.get("id"), "Debe retornar el ID de la venta");
            assertEquals("I001", resultado.get("serie"), "La serie debe ser I001");
            assertEquals("EFECTIVO", resultado.get("metodoPago"), "El metodo de pago debe ser EFECTIVO");

            // Verificar que se guardo la venta
            verify(ventaRepository, times(1)).save(any(Venta.class));

            // Verificar que se registro en kardex
            verify(kardexRepository, times(1)).save(any(Kardex.class));

            // Verificar que se actualizo el stock del producto
            verify(productoRepository, times(1)).save(any(Producto.class));

            // Verificar que se desconto el stock
            ArgumentCaptor<Producto> productoCaptor = ArgumentCaptor.forClass(Producto.class);
            verify(productoRepository).save(productoCaptor.capture());
            assertEquals(95, productoCaptor.getValue().getStockActual(),
                    "El stock debe reducirse de 100 a 95");
        }

        @Test
        @DisplayName("Debe crear venta de servicio sin validar stock")
        void crearVenta_ConServicio_NoDebeValidarStock() {
            // ARRANGE
            configurarMocksComunes();

            // Agregar item de servicio
            ventaDTO.setItems(List.of(
                    crearDetalleDTO(3L, 10, new BigDecimal("0.50"))
            ));

            // Mock del servicio (tipo = "SERVICIO")
            when(productoRepository.findByIdWithLock(3L))
                    .thenReturn(Optional.of(productoServicio));

            // ACT
            Map<String, Object> resultado = ventaService.crearVenta(ventaDTO);

            // ASSERT
            assertNotNull(resultado, "El resultado no debe ser null");
            assertNotNull(resultado.get("id"), "Debe retornar el ID de la venta");

            // Verificar que se guardo la venta
            verify(ventaRepository, times(1)).save(any(Venta.class));

            // Verificar que NO se registro en kardex (es servicio)
            verify(kardexRepository, never()).save(any(Kardex.class));

            // Verificar que NO se actualizo el stock (es servicio)
            verify(productoRepository, never()).save(any(Producto.class));
        }

        @Test
        @DisplayName("Debe crear venta con multiples items")
        void crearVenta_ConMultiplesItems_DebeGuardarTodos() {
            // ARRANGE
            configurarMocksComunes();

            // Agregar multiples items
            ventaDTO.setItems(List.of(
                    crearDetalleDTO(1L, 2, new BigDecimal("5.00")),
                    crearDetalleDTO(3L, 5, new BigDecimal("0.50"))
            ));

            when(productoRepository.findByIdWithLock(1L))
                    .thenReturn(Optional.of(productoConStock));
            when(productoRepository.findByIdWithLock(3L))
                    .thenReturn(Optional.of(productoServicio));

            // ACT
            Map<String, Object> resultado = ventaService.crearVenta(ventaDTO);

            // ASSERT
            assertNotNull(resultado, "El resultado no debe ser null");
            verify(ventaRepository, times(1)).save(any(Venta.class));

            // Verificar que la venta tiene el total correcto
            // Producto: 2 x 5.00 = 10.00
            // Servicio: 5 x 0.50 = 2.50
            // Total: 12.50
            ArgumentCaptor<Venta> ventaCaptor = ArgumentCaptor.forClass(Venta.class);
            verify(ventaRepository).save(ventaCaptor.capture());
            assertEquals(0, new BigDecimal("12.50").compareTo(ventaCaptor.getValue().getTotal()),
                    "El total debe ser 12.50");
        }
    }

    // =====================================================
    //  TESTS DE FALLO
    // =====================================================
    @Nested
    @DisplayName("Casos de Fallo")
    class CasosDeFallo {

        @Test
        @DisplayName("Debe lanzar excepcion por stock insuficiente sin guardar nada")
        void crearVenta_SinStockSuficiente_DebeLanzarExcepcion() {
            // ARRANGE
            configurarMocksComunes();

            // Agregar item SIN stock (stock = 0, pedimos 5)
            ventaDTO.setItems(List.of(
                    crearDetalleDTO(2L, 5, new BigDecimal("1.50"))
            ));

            // Mock del producto SIN stock
            when(productoRepository.findByIdWithLock(2L))
                    .thenReturn(Optional.of(productoSinStock));

            // ACT & ASSERT
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> ventaService.crearVenta(ventaDTO),
                    "Debe lanzar RuntimeException por stock insuficiente"
            );

            // Verificar mensaje de error
            assertTrue(exception.getMessage().contains("Stock insuficiente"),
                    "El mensaje debe indicar stock insuficiente");
            assertTrue(exception.getMessage().contains("Lapicero Rojo"),
                    "El mensaje debe incluir el nombre del producto");

            // VERIFICAR QUE NO SE GUARDO NADA
            verify(ventaRepository, never()).save(any(Venta.class));
            verify(kardexRepository, never()).save(any(Kardex.class));
            verify(amortizacionRepository, never()).save(any(Amortizacion.class));
        }

        @Test
        @DisplayName("Debe lanzar excepcion si producto no existe")
        void crearVenta_ProductoNoExiste_DebeLanzarExcepcion() {
            // ARRANGE
            configurarMocksComunes();

            // Agregar item con producto inexistente
            ventaDTO.setItems(List.of(
                    crearDetalleDTO(999L, 1, new BigDecimal("10.00"))
            ));

            // Mock: producto no encontrado
            when(productoRepository.findByIdWithLock(999L))
                    .thenReturn(Optional.empty());

            // ACT & ASSERT
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> ventaService.crearVenta(ventaDTO),
                    "Debe lanzar RuntimeException si el producto no existe"
            );

            assertTrue(exception.getMessage().contains("Producto no encontrado"),
                    "El mensaje debe indicar que el producto no fue encontrado");

            // VERIFICAR QUE NO SE GUARDO NADA
            verify(ventaRepository, never()).save(any(Venta.class));
        }

        @Test
        @DisplayName("Debe fallar si cantidad excede stock disponible")
        void crearVenta_CantidadExcedeStock_DebeLanzarExcepcion() {
            // ARRANGE
            configurarMocksComunes();

            // Pedimos 150 cuando solo hay 100
            ventaDTO.setItems(List.of(
                    crearDetalleDTO(1L, 150, new BigDecimal("5.00"))
            ));

            when(productoRepository.findByIdWithLock(1L))
                    .thenReturn(Optional.of(productoConStock)); // stock = 100

            // ACT & ASSERT
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> ventaService.crearVenta(ventaDTO),
                    "Debe lanzar RuntimeException cuando cantidad > stock"
            );

            assertTrue(exception.getMessage().contains("Stock insuficiente"),
                    "El mensaje debe indicar stock insuficiente");
            assertTrue(exception.getMessage().contains("Disponible: 100"),
                    "El mensaje debe mostrar stock disponible");
            assertTrue(exception.getMessage().contains("Requerido: 150"),
                    "El mensaje debe mostrar cantidad requerida");

            // VERIFICAR QUE NO SE GUARDO NADA
            verify(ventaRepository, never()).save(any(Venta.class));
        }
    }

    // =====================================================
    //  TESTS DE VALIDACION DE PRECIOS (SEGURIDAD)
    // =====================================================
    @Nested
    @DisplayName("Validacion de Precios")
    class ValidacionDePrecios {

        @Test
        @DisplayName("Debe rechazar sobreprecio (intento de fraude)")
        void crearVenta_ConSobreprecio_DebeRechazar() {
            // ARRANGE
            configurarMocksComunes();

            // Intentar vender a precio mayor al configurado (5.00 -> 50.00)
            ventaDTO.setItems(List.of(
                    crearDetalleDTO(1L, 1, new BigDecimal("50.00")) // Precio real: 5.00
            ));

            when(productoRepository.findByIdWithLock(1L))
                    .thenReturn(Optional.of(productoConStock));

            // ACT & ASSERT
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> ventaService.crearVenta(ventaDTO),
                    "Debe rechazar intento de sobreprecio"
            );

            assertTrue(exception.getMessage().contains("sobreprecio") ||
                      exception.getMessage().contains("ALERTA DE SEGURIDAD"),
                    "El mensaje debe indicar alerta de seguridad o sobreprecio");

            verify(ventaRepository, never()).save(any(Venta.class));
        }

        @Test
        @DisplayName("Vendedor no puede modificar precios")
        void crearVenta_VendedorModificaPrecio_DebeRechazar() {
            // ARRANGE
            configurarMocksComunes();

            // Vendedor intenta dar descuento (no permitido)
            ventaDTO.setItems(List.of(
                    crearDetalleDTO(1L, 1, new BigDecimal("3.00")) // Precio real: 5.00
            ));

            when(productoRepository.findByIdWithLock(1L))
                    .thenReturn(Optional.of(productoConStock));

            // ACT & ASSERT
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> ventaService.crearVenta(ventaDTO),
                    "Vendedor no debe poder modificar precios"
            );

            assertTrue(exception.getMessage().contains("No autorizado") ||
                      exception.getMessage().contains("Solo administradores"),
                    "El mensaje debe indicar falta de autorizacion");

            verify(ventaRepository, never()).save(any(Venta.class));
        }
    }

    // =====================================================
    //  LIMPIEZA
    // =====================================================
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
}
