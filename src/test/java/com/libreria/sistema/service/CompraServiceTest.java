package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.CompraDTO;
import com.libreria.sistema.model.dto.CosteoProductoDTO;
import com.libreria.sistema.repository.CompraRepository;
import com.libreria.sistema.repository.DetalleCompraRepository;
import com.libreria.sistema.repository.KardexRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.ProveedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompraServiceTest {

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private KardexRepository kardexRepository;

    @Mock
    private ProveedorRepository proveedorRepository;

    @Mock
    private CajaService cajaService;

    @Mock
    private CosteoCalculatorService costeoCalculatorService;

    @Mock
    private CosteoSugerenciaService costeoSugerenciaService;

    @Mock
    private ConfiguracionService configuracionService;

    @Mock
    private ReposicionPendienteService reposicionPendienteService;

    @Mock
    private CosteoEmpresarialService costeoEmpresarialService;

    @Mock
    private DetalleCompraRepository detalleCompraRepository;

    @Mock
    private HistorialPrecioProductoService historialPrecioProductoService;

    @InjectMocks
    private CompraService compraService;

    private Proveedor proveedor;
    private Producto producto;
    private Producto producto2;
    private Configuracion configuracion;

    @BeforeEach
    void setUp() {
        // Mock authentication context
        var auth = new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Proveedor
        proveedor = new Proveedor();
        proveedor.setId(1L);
        proveedor.setRuc("20000000001");
        proveedor.setRazonSocial("Proveedor S.A.C.");
        proveedor.setActivo(true);

        // Producto
        producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Cuaderno");
        producto.setPrecioCompra(new BigDecimal("10.00"));
        producto.setPrecioVenta(new BigDecimal("16.00"));
        producto.setStockActual(10);
        producto.setTipo("PRODUCTO");
        producto.setTipoAfectacionIgv("GRAVADO");

        producto2 = new Producto();
        producto2.setId(2L);
        producto2.setNombre("Cartulina");
        producto2.setPrecioCompra(new BigDecimal("2.00"));
        producto2.setPrecioVenta(new BigDecimal("3.50"));
        producto2.setStockActual(0);
        producto2.setTipo("PRODUCTO");
        producto2.setTipoAfectacionIgv("GRAVADO");

        // Configuracion
        configuracion = new Configuracion();
        configuracion.setCosteoInteligenteActivo(true);
        configuracion.setCosteoSugerenciaComprasActiva(true);

        when(proveedorRepository.findById(1L)).thenReturn(Optional.of(proveedor));
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.findById(2L)).thenReturn(Optional.of(producto2));
        when(configuracionService.obtenerConfiguracion()).thenReturn(configuracion);
        when(costeoSugerenciaService.obtenerFactorSugeridoProveedor(anyLong())).thenReturn(BigDecimal.ZERO);
        when(detalleCompraRepository.ultimasComprasProductos(anyCollection())).thenReturn(List.of());
        
        // Mock CosteoCalculatorService
        when(costeoCalculatorService.resolverGananciaObjetivo(any(Producto.class)))
                .thenReturn(new BigDecimal("60.00"));
        when(costeoCalculatorService.resolverGananciaMinima(any(Producto.class)))
                .thenReturn(new BigDecimal("30.00"));
        when(costeoCalculatorService.aplicarGanancia(any(BigDecimal.class), any(BigDecimal.class)))
                .thenAnswer(inv -> {
                    BigDecimal base = inv.getArgument(0);
                    BigDecimal pct = inv.getArgument(1);
                    return base.multiply(BigDecimal.ONE.add(pct.divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP)));
                });
        when(costeoCalculatorService.aplicarRedondeoRetail(any(BigDecimal.class), any(Configuracion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(costeoEmpresarialService.actualizarSnapshotProducto(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            BigDecimal costo = p.getPrecioCompra() != null ? p.getPrecioCompra() : BigDecimal.ZERO;
            BigDecimal minimo = costo.multiply(new BigDecimal("1.30")).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal sugerido = costo.multiply(new BigDecimal("1.60")).setScale(2, java.math.RoundingMode.HALF_UP);
            p.setCostoIndirectoEstimado(BigDecimal.ZERO);
            p.setCostoTotalEstimado(costo);
            p.setPrecioMinimoEmpresarial(minimo);
            p.setPrecioSugeridoEmpresarial(sugerido);
            return CosteoProductoDTO.builder()
                    .productoId(p.getId())
                    .nombre(p.getNombre())
                    .costoDirectoUnitario(costo)
                    .costoIndirectoUnitario(BigDecimal.ZERO)
                    .costoTotalUnitario(costo)
                    .gananciaMinimaPct(new BigDecimal("30.00"))
                    .gananciaObjetivoPct(new BigDecimal("60.00"))
                    .precioMinimo(minimo)
                    .precioSugerido(sugerido)
                    .precioVentaActual(p.getPrecioVenta() != null ? p.getPrecioVenta() : BigDecimal.ZERO)
                    .diferenciaPrecio(BigDecimal.ZERO)
                    .margenActualPct(BigDecimal.ZERO)
                    .estadoPrecio("OK")
                    .reglaResumen("test")
                    .build();
        });

        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Debe registrar compra sin gastos indirectos")
    void guardarCompra_SinGastosIndirectos_DebeCalcularNormal() {
        // ARRANGE
        CompraDTO dto = new CompraDTO();
        dto.setProveedorId(1L);
        dto.setTipoComprobante("FACTURA");
        dto.setNumeroComprobante("F001-0001");
        dto.setObservaciones("Prueba");
        
        CompraDTO.DetalleDTO item = new CompraDTO.DetalleDTO();
        item.setProductoId(1L);
        item.setCantidad(5);
        item.setCosto(new BigDecimal("10.00"));
        dto.setItems(List.of(item));

        // ACT
        CompraService.ResultadoCompra res = compraService.guardarCompra(dto);

        // ASSERT
        assertNotNull(res);
        Compra compra = res.getCompra();
        assertEquals(0, new BigDecimal("50.00").compareTo(compra.getSubtotalDirecto()));
        assertEquals(0, BigDecimal.ZERO.compareTo(compra.getGastosIndirectosMonto()));
        assertEquals(0, BigDecimal.ZERO.compareTo(compra.getFactorIndirectoAplicadoPct()));
        
        // Verificar que no se lanzó error y se guardó
        verify(compraRepository, times(1)).save(any(Compra.class));
    }

    @Test
    @DisplayName("Debe registrar compra con gastos indirectos por monto fijo")
    void guardarCompra_ConGastosIndirectosMonto_DebeProrratearCostoReal() {
        // ARRANGE
        CompraDTO dto = new CompraDTO();
        dto.setProveedorId(1L);
        dto.setTipoComprobante("FACTURA");
        dto.setNumeroComprobante("F001-0001");
        
        // 5 unidades a S/ 10.00 cada una = Subtotal S/ 50.00
        CompraDTO.DetalleDTO item = new CompraDTO.DetalleDTO();
        item.setProductoId(1L);
        item.setCantidad(5);
        item.setCosto(new BigDecimal("10.00"));
        dto.setItems(List.of(item));

        // Gastos adicionales = S/ 10.00 (debe dar un recargo de 20%)
        dto.setGastosIndirectosMonto(new BigDecimal("10.00"));
        dto.setDetalleGastosIndirectos("Pasajes S/ 10");

        // ACT
        CompraService.ResultadoCompra res = compraService.guardarCompra(dto);

        // ASSERT
        assertNotNull(res);
        Compra compra = res.getCompra();
        assertEquals(0, new BigDecimal("50.00").compareTo(compra.getSubtotalDirecto()));
        assertEquals(0, new BigDecimal("10.00").compareTo(compra.getGastosIndirectosMonto()));
        
        // El factor aplicado debe ser 10 * 100 / 50 = 20%
        assertEquals(0, new BigDecimal("20.000").compareTo(compra.getFactorIndirectoAplicadoPct()));
        assertEquals("Pasajes S/ 10", compra.getDetalleGastosIndirectos());

        // Verificar el detalle
        ArgumentCaptor<Compra> compraCaptor = ArgumentCaptor.forClass(Compra.class);
        verify(compraRepository).save(compraCaptor.capture());
        Compra saved = compraCaptor.getValue();
        assertNotNull(saved.getDetalles());
        assertEquals(1, saved.getDetalles().size());
        
        // El precioUnitario en DetalleCompra representa el costoRealUnit.
        // Costo Real = 10.00 * (1 + 0.20) = 12.00
        BigDecimal costoRealUnit = saved.getDetalles().get(0).getPrecioUnitario();
        assertEquals(0, new BigDecimal("12.0000").compareTo(costoRealUnit));
    }

    @Test
    @DisplayName("Debe registrar compra aplicando factor indirecto directamente")
    void guardarCompra_ConFactorIndirectoAplicado_DebeCalcularGastosYMonto() {
        // ARRANGE
        CompraDTO dto = new CompraDTO();
        dto.setProveedorId(1L);
        dto.setTipoComprobante("FACTURA");
        dto.setNumeroComprobante("F001-0001");
        
        CompraDTO.DetalleDTO item = new CompraDTO.DetalleDTO();
        item.setProductoId(1L);
        item.setCantidad(5);
        item.setCosto(new BigDecimal("10.00"));
        dto.setItems(List.of(item));

        // Aplicamos directamente factor del 10%
        dto.setFactorIndirectoAplicadoPct(new BigDecimal("10.000"));

        // ACT
        CompraService.ResultadoCompra res = compraService.guardarCompra(dto);

        // ASSERT
        assertNotNull(res);
        Compra compra = res.getCompra();
        assertEquals(0, new BigDecimal("50.00").compareTo(compra.getSubtotalDirecto()));
        
        // El monto de gastos indirectos debe ser 50.00 * 10% = 5.00
        assertEquals(0, new BigDecimal("5.00").compareTo(compra.getGastosIndirectosMonto()));
        assertEquals(0, new BigDecimal("10.000").compareTo(compra.getFactorIndirectoAplicadoPct()));

        // Verificar el costo real unitario: 10.00 * (1 + 0.10) = 11.00
        ArgumentCaptor<Compra> compraCaptor = ArgumentCaptor.forClass(Compra.class);
        verify(compraRepository).save(compraCaptor.capture());
        BigDecimal costoRealUnit = compraCaptor.getValue().getDetalles().get(0).getPrecioUnitario();
        assertEquals(0, new BigDecimal("11.0000").compareTo(costoRealUnit));
    }

    @Test
    @DisplayName("Debe usar el total pagado como fuente exacta del costo unitario")
    void guardarCompra_ConTotalPagado_DebeCalcularCostoBaseDesdeTotal() {
        // ARRANGE
        CompraDTO dto = new CompraDTO();
        dto.setProveedorId(1L);
        dto.setTipoComprobante("FACTURA");
        dto.setNumeroComprobante("F001-0002");

        CompraDTO.DetalleDTO item = new CompraDTO.DetalleDTO();
        item.setProductoId(1L);
        item.setCantidad(3);
        item.setCosto(new BigDecimal("3.33"));
        item.setTotalPagado(new BigDecimal("10.00"));
        dto.setItems(List.of(item));

        // ACT
        CompraService.ResultadoCompra res = compraService.guardarCompra(dto);

        // ASSERT
        assertNotNull(res);
        ArgumentCaptor<Compra> compraCaptor = ArgumentCaptor.forClass(Compra.class);
        verify(compraRepository).save(compraCaptor.capture());
        Compra saved = compraCaptor.getValue();
        DetalleCompra detalle = saved.getDetalles().get(0);

        assertEquals(0, new BigDecimal("10.00").compareTo(saved.getSubtotalDirecto()));
        assertEquals(0, new BigDecimal("3.3333").compareTo(detalle.getCostoUnitarioBase()));
        assertEquals(0, new BigDecimal("10.00").compareTo(detalle.getSubtotal()));
    }

    @Test
    @DisplayName("Debe repartir gastos indirectos por compra cuadrando centavos exactos")
    void guardarCompra_ConGastosIndirectosMonto_DebeCuadrarCentavosEntreLineas() {
        // ARRANGE
        CompraDTO dto = new CompraDTO();
        dto.setProveedorId(1L);
        dto.setTipoComprobante("FACTURA");
        dto.setNumeroComprobante("F001-0003");

        CompraDTO.DetalleDTO item1 = new CompraDTO.DetalleDTO();
        item1.setProductoId(1L);
        item1.setCantidad(1);
        item1.setTotalPagado(new BigDecimal("33.33"));

        CompraDTO.DetalleDTO item2 = new CompraDTO.DetalleDTO();
        item2.setProductoId(2L);
        item2.setCantidad(1);
        item2.setTotalPagado(new BigDecimal("66.67"));

        dto.setItems(List.of(item1, item2));
        dto.setGastosIndirectosMonto(new BigDecimal("0.05"));
        dto.setDetalleGastosIndirectos("Bolsa S/ 0.05");

        // ACT
        CompraService.ResultadoCompra res = compraService.guardarCompra(dto);

        // ASSERT
        assertNotNull(res);
        ArgumentCaptor<Compra> compraCaptor = ArgumentCaptor.forClass(Compra.class);
        verify(compraRepository).save(compraCaptor.capture());
        Compra saved = compraCaptor.getValue();

        BigDecimal totalCargo = saved.getDetalles().stream()
                .map(DetalleCompra::getCargoIndirectoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, new BigDecimal("100.00").compareTo(saved.getSubtotalDirecto()));
        assertEquals(0, new BigDecimal("0.05").compareTo(totalCargo));
        assertEquals(0, new BigDecimal("100.05").compareTo(saved.getTotal()));
        assertEquals(0, new BigDecimal("33.3500").compareTo(saved.getDetalles().get(0).getCostoUnitarioReal()));
        assertEquals(0, new BigDecimal("66.7000").compareTo(saved.getDetalles().get(1).getCostoUnitarioReal()));
    }
}
