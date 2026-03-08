package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.DevolucionDTO;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests de integración unitaria para DevolucionService.revertirItemsListaEscolar().
 *
 * El método es privado, se prueba indirectamente vía procesarDevolucion().
 *
 * Cubre:
 * 1. Items devueltos vuelven a estado COTIZADO
 * 2. venta y detalleVentaId se limpian del detalle
 * 3. montoVendido de la lista se reduce correctamente
 * 4. Lista COMPLETADA → EN_PROCESO si quedan items pendientes
 * 5. Venta sin lista escolar → no se toca nada de lista
 * 6. Devolución parcial → solo los items con producto devuelto se revierten
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DevolucionService - revertirItemsListaEscolar (vía procesarDevolucion)")
class DevolucionListaEscolarTest {

    // =====================================================
    //  MOCKS
    // =====================================================
    @Mock private DevolucionVentaRepository devolucionRepository;
    @Mock private VentaRepository ventaRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private KardexRepository kardexRepository;
    @Mock private CorrelativoRepository correlativoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CajaService cajaService;
    @Mock private FacturacionElectronicaService facturacionService;
    @Mock private VentaListaEscolarRepository ventaListaRepository;
    @Mock private DetalleListaEscolarRepository detalleListaRepository;
    @Mock private ListaEscolarRepository listaEscolarRepository;

    @InjectMocks
    private DevolucionService devolucionService;

    // =====================================================
    //  DATOS DE PRUEBA COMPARTIDOS
    // =====================================================
    private Venta ventaOriginal;
    private Producto producto1;
    private Producto producto2;
    private ListaEscolar lista;
    private VentaListaEscolar ventaLista;
    private DetalleListaEscolar detalleVendido1;
    private DetalleListaEscolar detalleVendido2;
    private Correlativo correlativo;

    @BeforeEach
    void setUp() {
        // Usuario autenticado
        var auth = new UsernamePasswordAuthenticationToken(
            "vendedor", null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_VENDEDOR"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Facturación electrónica desactivada (evita llamada SUNAT)
        when(facturacionService.isFacturacionElectronicaActiva()).thenReturn(false);

        // Correlativo NC
        correlativo = new Correlativo("NOTA_CREDITO", "NC01", 10);
        when(correlativoRepository.findByCodigoAndSerieWithLock(anyString(), anyString()))
            .thenReturn(Optional.of(correlativo));
        when(correlativoRepository.save(any(Correlativo.class))).thenReturn(correlativo);

        // Usuario no encontrado (OK, es opcional)
        when(usuarioRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        // Productos en inventario
        producto1 = new Producto();
        producto1.setId(10L);
        producto1.setNombre("Cuaderno Rayado");
        producto1.setStockActual(5);
        producto1.setTipo("PRODUCTO");

        producto2 = new Producto();
        producto2.setId(20L);
        producto2.setNombre("Lapicero Azul");
        producto2.setStockActual(10);
        producto2.setTipo("PRODUCTO");

        when(productoRepository.findById(10L)).thenReturn(Optional.of(producto1));
        when(productoRepository.findById(20L)).thenReturn(Optional.of(producto2));
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        // Kardex
        when(kardexRepository.save(any(Kardex.class))).thenAnswer(i -> i.getArgument(0));

        // Venta original
        ventaOriginal = new Venta();
        ventaOriginal.setId(100L);
        ventaOriginal.setEstado("EMITIDA");
        ventaOriginal.setFechaEmision(LocalDate.now());
        ventaOriginal.setTotal(new BigDecimal("15.00"));
        ventaOriginal.setFormaPago("CONTADO");
        ventaOriginal.setSerie("B001");
        ventaOriginal.setNumero(55);

        when(ventaRepository.findById(100L)).thenReturn(Optional.of(ventaOriginal));
        doNothing().when(ventaRepository).actualizarEstado(anyLong(), anyString());

        // Devolucion guardada
        DevolucionVenta devGuardada = new DevolucionVenta();
        devGuardada.setId(999L);
        devGuardada.setSerie("NC01");
        devGuardada.setNumero(11);
        devGuardada.setTotalDevuelto(new BigDecimal("10.00"));
        when(devolucionRepository.save(any(DevolucionVenta.class))).thenReturn(devGuardada);

        // Lista escolar con 2 detalles
        lista = new ListaEscolar();
        lista.setId(1L);
        lista.setSerie("LE001");
        lista.setNumero(1);
        lista.setEstado("COMPLETADA");
        lista.setMontoVendido(new BigDecimal("15.00"));
        lista.setItemsTotal(2);
        lista.setItemsVendidos(2);
        lista.setItemsPendientes(0);

        detalleVendido1 = new DetalleListaEscolar();
        detalleVendido1.setId(1001L);
        detalleVendido1.setEstado("VENDIDO");
        detalleVendido1.setProducto(producto1); // productoFinal → producto1
        detalleVendido1.setVenta(ventaOriginal);
        detalleVendido1.setDetalleVentaId(201L);

        detalleVendido2 = new DetalleListaEscolar();
        detalleVendido2.setId(1002L);
        detalleVendido2.setEstado("VENDIDO");
        detalleVendido2.setProducto(producto2); // productoFinal → producto2
        detalleVendido2.setVenta(ventaOriginal);
        detalleVendido2.setDetalleVentaId(202L);

        // Agregar detalles en memoria a la lista
        lista.getDetalles().add(detalleVendido1);
        lista.getDetalles().add(detalleVendido2);

        // VentaListaEscolar join
        ventaLista = new VentaListaEscolar();
        ventaLista.setId(50L);
        ventaLista.setVenta(ventaOriginal);
        ventaLista.setListaEscolar(lista);
        ventaLista.setMontoVendido(new BigDecimal("15.00"));

        when(listaEscolarRepository.findByIdConDetalles(1L)).thenReturn(Optional.of(lista));
        when(detalleListaRepository.save(any(DetalleListaEscolar.class))).thenAnswer(i -> i.getArgument(0));
        when(listaEscolarRepository.save(any(ListaEscolar.class))).thenAnswer(i -> i.getArgument(0));
        when(ventaListaRepository.save(any(VentaListaEscolar.class))).thenAnswer(i -> i.getArgument(0));
    }

    // -----------------------------------------------------------
    //  HELPER: construye DTO de devolución
    // -----------------------------------------------------------
    private DevolucionDTO buildDto(Long... productoIds) {
        DevolucionDTO dto = new DevolucionDTO();
        dto.setVentaOriginalId(100L);
        dto.setMotivoDevolucion("DEFECTO");
        dto.setMetodoReembolso("TRANSFERENCIA"); // evita llamada a cajaService

        List<DevolucionDTO.ItemDevolucion> items = new ArrayList<>();
        for (Long productoId : productoIds) {
            DevolucionDTO.ItemDevolucion item = new DevolucionDTO.ItemDevolucion();
            item.setProductoId(productoId);
            item.setCantidadDevuelta(BigDecimal.ONE);
            item.setPrecioUnitario(new BigDecimal("5.00"));
            items.add(item);
        }
        dto.setItems(items);
        return dto;
    }

    // =====================================================
    //  TESTS: VENTA SIN LISTA ESCOLAR
    // =====================================================

    @Nested
    @DisplayName("Venta sin lista escolar → no se modifica ninguna lista")
    class VentaSinLista {

        @Test
        @DisplayName("ventaListaRepository no encuentra registro → no se toca lista")
        void sinLista_noSeModifica() {
            when(ventaListaRepository.findByVentaId(100L)).thenReturn(Optional.empty());

            devolucionService.procesarDevolucion(buildDto(10L));

            verify(listaEscolarRepository, never()).findByIdConDetalles(anyLong());
            verify(detalleListaRepository, never()).save(any());
            verify(listaEscolarRepository, never()).save(any());
        }
    }

    // =====================================================
    //  TESTS: DEVOLUCIÓN TOTAL (ambos productos)
    // =====================================================

    @Nested
    @DisplayName("Devolución total: todos los items de la venta devueltos")
    class DevolucionTotal {

        @BeforeEach
        void setupLista() {
            when(ventaListaRepository.findByVentaId(100L)).thenReturn(Optional.of(ventaLista));
            when(detalleListaRepository.findByVentaId(100L))
                .thenReturn(List.of(detalleVendido1, detalleVendido2));
        }

        @Test
        @DisplayName("Ambos detalles vuelven a estado COTIZADO")
        void ambosDetallesCotizados() {
            devolucionService.procesarDevolucion(buildDto(10L, 20L));

            assertEquals("COTIZADO", detalleVendido1.getEstado());
            assertEquals("COTIZADO", detalleVendido2.getEstado());
        }

        @Test
        @DisplayName("venta y detalleVentaId se limpian de los detalles revertidos")
        void ventaYDetalleIdLimpiados() {
            devolucionService.procesarDevolucion(buildDto(10L, 20L));

            assertNull(detalleVendido1.getVenta());
            assertNull(detalleVendido1.getDetalleVentaId());
            assertNull(detalleVendido2.getVenta());
            assertNull(detalleVendido2.getDetalleVentaId());
        }

        @Test
        @DisplayName("detalleListaRepository.save() se llama para cada detalle revertido")
        void saveLlamadoPorCadaDetalle() {
            devolucionService.procesarDevolucion(buildDto(10L, 20L));

            verify(detalleListaRepository, times(2)).save(any(DetalleListaEscolar.class));
        }

        @Test
        @DisplayName("montoVendido de la lista se reduce en el total devuelto")
        void montoVendidoReducido() {
            // Total devuelto = 2 items × 5.00 = 10.00
            devolucionService.procesarDevolucion(buildDto(10L, 20L));

            ArgumentCaptor<ListaEscolar> captor = ArgumentCaptor.forClass(ListaEscolar.class);
            verify(listaEscolarRepository).save(captor.capture());
            // 15.00 original - 10.00 devuelto = 5.00
            assertEquals(new BigDecimal("5.00"), captor.getValue().getMontoVendido());
        }

        @Test
        @DisplayName("montoVendido de VentaListaEscolar se reduce")
        void montoVentaListaReducido() {
            devolucionService.procesarDevolucion(buildDto(10L, 20L));

            ArgumentCaptor<VentaListaEscolar> captor = ArgumentCaptor.forClass(VentaListaEscolar.class);
            verify(ventaListaRepository).save(captor.capture());
            // 15.00 - 10.00 = 5.00
            assertEquals(new BigDecimal("5.00"), captor.getValue().getMontoVendido());
        }

        @Test
        @DisplayName("Lista COMPLETADA con items pendientes → pasa a EN_PROCESO")
        void completadaVuelveAEnProceso() {
            // Lista estaba COMPLETADA; después de revertir, tiene items pendientes
            lista.setEstado("COMPLETADA");

            devolucionService.procesarDevolucion(buildDto(10L, 20L));

            // recalcularPendientes() detecta items COTIZADO → itemsPendientes > 0
            ArgumentCaptor<ListaEscolar> captor = ArgumentCaptor.forClass(ListaEscolar.class);
            verify(listaEscolarRepository).save(captor.capture());
            assertEquals("EN_PROCESO", captor.getValue().getEstado());
        }

        @Test
        @DisplayName("listaEscolarRepository.save() se llama exactamente una vez")
        void listaGuardadaUnaVez() {
            devolucionService.procesarDevolucion(buildDto(10L, 20L));
            verify(listaEscolarRepository, times(1)).save(any(ListaEscolar.class));
        }
    }

    // =====================================================
    //  TESTS: DEVOLUCIÓN PARCIAL
    // =====================================================

    @Nested
    @DisplayName("Devolución parcial: solo un producto devuelto")
    class DevolucionParcial {

        @BeforeEach
        void setupLista() {
            when(ventaListaRepository.findByVentaId(100L)).thenReturn(Optional.of(ventaLista));
            when(detalleListaRepository.findByVentaId(100L))
                .thenReturn(List.of(detalleVendido1, detalleVendido2));
        }

        @Test
        @DisplayName("Solo el detalle del producto devuelto vuelve a COTIZADO")
        void soloDetalleDevueltoCotizado() {
            devolucionService.procesarDevolucion(buildDto(10L)); // solo producto1

            assertEquals("COTIZADO", detalleVendido1.getEstado(), "producto1 devuelto → COTIZADO");
            assertEquals("VENDIDO", detalleVendido2.getEstado(), "producto2 NO devuelto → sigue VENDIDO");
        }

        @Test
        @DisplayName("Solo se llama save() para el detalle revertido, no para el otro")
        void saveSoloPorDetalleRevertido() {
            devolucionService.procesarDevolucion(buildDto(10L)); // solo producto1

            ArgumentCaptor<DetalleListaEscolar> captor = ArgumentCaptor.forClass(DetalleListaEscolar.class);
            verify(detalleListaRepository, times(1)).save(captor.capture());
            assertEquals(1001L, captor.getValue().getId(), "Solo se guarda el detalle del producto devuelto");
        }

        @Test
        @DisplayName("Lista COMPLETADA pasa a EN_PROCESO aunque queden items VENDIDO (cualquier pendiente activa transición)")
        void completadaPasaEnProcesoConDevoluccionParcial() {
            // Después de la devolución parcial, detalleVendido1=COTIZADO, detalleVendido2=VENDIDO
            // recalcularPendientes(): itemsPendientes=1 → COMPLETADA → EN_PROCESO
            lista.setEstado("COMPLETADA");

            devolucionService.procesarDevolucion(buildDto(10L));

            ArgumentCaptor<ListaEscolar> captor = ArgumentCaptor.forClass(ListaEscolar.class);
            verify(listaEscolarRepository).save(captor.capture());
            assertEquals("EN_PROCESO", captor.getValue().getEstado(),
                "Devolución parcial: hay items pendientes → lista no puede estar COMPLETADA");
        }
    }

    // =====================================================
    //  TESTS: PROTECCIÓN CONTRA montoVendido NEGATIVO
    // =====================================================

    @Nested
    @DisplayName("montoVendido no puede quedar negativo")
    class ProteccionMontoNegativo {

        @Test
        @DisplayName("Si totalDevuelto supera montoVendido, se clampea a ZERO")
        void montoNuncaNegativo() {
            // Lista con monto bajo (2.00), pero se devuelven 10.00
            lista.setMontoVendido(new BigDecimal("2.00"));
            ventaLista.setMontoVendido(new BigDecimal("2.00"));

            when(ventaListaRepository.findByVentaId(100L)).thenReturn(Optional.of(ventaLista));
            when(detalleListaRepository.findByVentaId(100L)).thenReturn(List.of(detalleVendido1, detalleVendido2));

            devolucionService.procesarDevolucion(buildDto(10L, 20L));

            ArgumentCaptor<ListaEscolar> listaCaptor = ArgumentCaptor.forClass(ListaEscolar.class);
            verify(listaEscolarRepository).save(listaCaptor.capture());
            assertEquals(BigDecimal.ZERO, listaCaptor.getValue().getMontoVendido(),
                "montoVendido nunca debe ser negativo");

            ArgumentCaptor<VentaListaEscolar> ventaListaCaptor = ArgumentCaptor.forClass(VentaListaEscolar.class);
            verify(ventaListaRepository).save(ventaListaCaptor.capture());
            assertEquals(BigDecimal.ZERO, ventaListaCaptor.getValue().getMontoVendido(),
                "montoVendido de VentaLista nunca debe ser negativo");
        }
    }

    // =====================================================
    //  TESTS: LISTA NO ENCONTRADA EN DB
    // =====================================================

    @Nested
    @DisplayName("Lista escolar no encontrada → no se modifica nada")
    class ListaNoEncontrada {

        @Test
        @DisplayName("findByIdConDetalles retorna empty → no se lanza excepción, no se guarda")
        void listaNoEncontrada_noModifica() {
            when(ventaListaRepository.findByVentaId(100L)).thenReturn(Optional.of(ventaLista));
            when(listaEscolarRepository.findByIdConDetalles(anyLong())).thenReturn(Optional.empty());

            // No debe lanzar excepción
            assertDoesNotThrow(() -> devolucionService.procesarDevolucion(buildDto(10L)));

            verify(detalleListaRepository, never()).save(any());
            verify(listaEscolarRepository, never()).save(any());
        }
    }

    // =====================================================
    //  TESTS: LISTA EN ESTADO EN_PROCESO (no COMPLETADA)
    // =====================================================

    @Nested
    @DisplayName("Lista en estado EN_PROCESO no cambia a otro estado")
    class ListaEnProceso {

        @Test
        @DisplayName("Lista EN_PROCESO sigue EN_PROCESO tras devolución parcial")
        void enProcesoSigueEnProceso() {
            lista.setEstado("EN_PROCESO");
            when(ventaListaRepository.findByVentaId(100L)).thenReturn(Optional.of(ventaLista));
            when(detalleListaRepository.findByVentaId(100L)).thenReturn(List.of(detalleVendido1));

            devolucionService.procesarDevolucion(buildDto(10L));

            ArgumentCaptor<ListaEscolar> captor = ArgumentCaptor.forClass(ListaEscolar.class);
            verify(listaEscolarRepository).save(captor.capture());
            // Solo COMPLETADA → EN_PROCESO es la regla; si ya era EN_PROCESO, se mantiene
            assertEquals("EN_PROCESO", captor.getValue().getEstado());
        }
    }
}
