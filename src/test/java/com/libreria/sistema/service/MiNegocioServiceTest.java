package com.libreria.sistema.service;

import com.libreria.sistema.model.CategoriaMovimiento;
import com.libreria.sistema.model.ConfigCuentaFija;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.repository.CompraRepository;
import com.libreria.sistema.repository.DetalleVentaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiNegocioServiceTest {

    @Mock
    private VentaRepository ventaRepository;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private DetalleVentaRepository detalleVentaRepository;

    @Mock
    private CajaRepository cajaRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CuentaFijaService cuentaFijaService;

    @InjectMocks
    private MiNegocioService miNegocioService;

    @Test
    @DisplayName("Calcula indicadores financieros profesionales para un mes completo")
    void obtenerAnalisis_DebeCalcularIndicadoresFinancieros() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 1, 31);
        LocalDate inicioAnt = LocalDate.of(2025, 12, 1);
        LocalDate finAnt = LocalDate.of(2025, 12, 31);

        when(ventaRepository.sumVentasValidasByPeriodo(inicio, fin)).thenReturn(new BigDecimal("1000.00"));
        when(ventaRepository.sumVentasValidasByPeriodo(inicioAnt, finAnt)).thenReturn(new BigDecimal("800.00"));
        when(ventaRepository.countVentasValidasByPeriodo(inicio, fin)).thenReturn(10L);
        when(compraRepository.sumTotalByPeriodo(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("400.00"), new BigDecimal("300.00"));
        when(detalleVentaRepository.sumarUtilidadPorPeriodo(inicio, fin)).thenReturn(new BigDecimal("350.00"));
        when(detalleVentaRepository.sumarUtilidadPorPeriodo(inicioAnt, finAnt)).thenReturn(new BigDecimal("250.00"));
        when(cajaRepository.sumarIngresosPorFechas(inicio, fin)).thenReturn(new BigDecimal("900.00"));
        when(cajaRepository.sumarEgresosPorFechas(inicio, fin)).thenReturn(new BigDecimal("500.00"));
        when(cajaRepository.sumarPorCategoriaYFechas(any(String.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);
        when(cajaRepository.sumarPorCategoriaYFechas(eq(CategoriaMovimiento.GASTO_OPERATIVO), eq(inicio), eq(fin)))
                .thenReturn(new BigDecimal("150.00"));
        when(cajaRepository.sumarPorCategoriaYFechas(eq(CategoriaMovimiento.RETIRO_DUENO), eq(inicio), eq(fin)))
                .thenReturn(new BigDecimal("50.00"));
        when(cajaRepository.sumarPorCategoriaYFechas(eq(CategoriaMovimiento.GASTO_OPERATIVO), eq(inicioAnt), eq(finAnt)))
                .thenReturn(new BigDecimal("100.00"));
        when(cajaRepository.sumarPorCategoriaYFechas(eq(CategoriaMovimiento.RETIRO_DUENO), eq(inicioAnt), eq(finAnt)))
                .thenReturn(new BigDecimal("40.00"));
        when(cuentaFijaService.listarActivas()).thenReturn(List.of(
                cuenta("Alquiler local", "ALQUILER", "600.00"),
                cuenta("Internet", "SERVICIOS", "90.00"),
                cuenta("Banco cuota negocio", "BANCO", "240.00")
        ));
        Producto cartulina = producto("SKU-CART", "Cartulina escolar", "PAPELES", 20, "1.50");
        Producto globo = producto("SKU-GLOBO", "Globo metalizado", "FIESTA", 10, "2.00");
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(cartulina, globo));
        when(productoRepository.countProductosSinMovimiento(any(LocalDate.class))).thenReturn(1L);
        when(productoRepository.calcularCapitalEstancado(any(LocalDate.class))).thenReturn(new BigDecimal("30.00"));
        when(productoRepository.obtenerProductosSinMovimiento(any(LocalDate.class))).thenReturn(List.of(cartulina));
        when(detalleVentaRepository.topProductosPorUtilidad(eq(inicio), eq(fin), any(Pageable.class)))
                .thenReturn(List.of());

        Map<String, Object> analisis = miNegocioService.obtenerAnalisis(inicio, fin);

        assertMoney("100.00", analisis.get("ticketPromedio"));
        assertMoney("650.00", analisis.get("dineroReposicionSugerido"));
        assertMoney("35.0", analisis.get("margenBrutoPct"));
        assertMoney("20.0", analisis.get("margenNetoPct"));
        assertMoney("200.00", analisis.get("utilidadOperativa"));
        assertMoney("400.00", analisis.get("liquidezPeriodo"));
        assertMoney("1.80", analisis.get("ratioLiquidez"));
        assertMoney("13.3", analisis.get("liquidezDias"));
        assertMoney("2657.14", analisis.get("puntoEquilibrio"));
        assertMoney("1657.14", analisis.get("faltaParaEquilibrio"));
        assertMoney("85.71", analisis.get("metaDiaria"));
        assertMoney("106.68", analisis.get("metaDiariaSana"));
        assertMoney("3.00", analisis.get("costoFijoHora"));
        assertMoney("240.00", analisis.get("prestamosBancosMensual"));
        assertMoney("240.00", analisis.get("prestamosBancosPeriodo"));
        assertMoney("24.0", analisis.get("pesoPrestamosSobreVentasPct"));
        assertMoney("600.00", analisis.get("alquilerMensual"));
        assertMoney("90.00", analisis.get("internetMensual"));
        assertMoney("-580.00", analisis.get("utilidadAdministrativa"));
        assertMoney("-250.00", analisis.get("cajaLibreTrasReposicion"));
        assertMoney("250.00", analisis.get("faltanteReposicion"));
        assertMoney("61.5", analisis.get("reposicionCubiertaPct"));
        assertMoney("1580.00", analisis.get("capitalTrabajoSugerido"));
        assertMoney("25.3", analisis.get("capitalTrabajoCubiertoPct"));
        assertMoney("2.50", analisis.get("retornoPorSolInvertido"));
        assertMoney("0.88", analisis.get("gananciaPorSolInvertido"));
        assertMoney("2903.40", analisis.get("proyeccionVentas3Meses"));
        assertMoney("580.50", analisis.get("proyeccionUtilidad3Meses"));
        assertMoney("1161.00", analisis.get("proyeccionFlujoCaja3Meses"));
        assertEquals(1L, analisis.get("productosEstancados"));
        assertMoney("30.00", analisis.get("capitalEstancado"));
        assertMoney("60.0", analisis.get("capitalEstancadoPct"));
        assertEquals("AJUSTADO", analisis.get("estadoNegocio"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inventarioEstancado = (List<Map<String, Object>>) analisis.get("inventarioEstancado");
        assertEquals(1, inventarioEstancado.size());
        assertEquals("Cartulina escolar", inventarioEstancado.get(0).get("nombre"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alertas = (List<Map<String, Object>>) analisis.get("alertasFinancieras");
        assertTrue(alertas.stream().anyMatch(a -> "Descalce de reposicion".equals(a.get("titulo"))));
        assertTrue(alertas.stream().anyMatch(a -> "Bancos pesan demasiado".equals(a.get("titulo"))));
    }

    @Test
    @DisplayName("No divide entre cero cuando aun no hay ventas")
    void obtenerAnalisis_SinVentas_DebeMantenerIndicadoresEnCero() {
        LocalDate inicio = LocalDate.of(2026, 2, 1);
        LocalDate fin = LocalDate.of(2026, 2, 28);

        when(ventaRepository.sumVentasValidasByPeriodo(any(LocalDate.class), any(LocalDate.class))).thenReturn(BigDecimal.ZERO);
        when(ventaRepository.countVentasValidasByPeriodo(inicio, fin)).thenReturn(0L);
        when(compraRepository.sumTotalByPeriodo(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(BigDecimal.ZERO);
        when(detalleVentaRepository.sumarUtilidadPorPeriodo(any(LocalDate.class), any(LocalDate.class))).thenReturn(BigDecimal.ZERO);
        when(cajaRepository.sumarIngresosPorFechas(inicio, fin)).thenReturn(BigDecimal.ZERO);
        when(cajaRepository.sumarEgresosPorFechas(inicio, fin)).thenReturn(BigDecimal.ZERO);
        when(cajaRepository.sumarPorCategoriaYFechas(any(String.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);
        when(cuentaFijaService.listarActivas()).thenReturn(List.of(cuenta("Alquiler local", "ALQUILER", "700.00")));
        when(productoRepository.findByActivoTrue()).thenReturn(List.of());
        when(productoRepository.calcularCapitalEstancado(any(LocalDate.class))).thenReturn(BigDecimal.ZERO);
        when(productoRepository.obtenerProductosSinMovimiento(any(LocalDate.class))).thenReturn(List.of());
        when(detalleVentaRepository.topProductosPorUtilidad(eq(inicio), eq(fin), any(Pageable.class)))
                .thenReturn(List.of());

        Map<String, Object> analisis = miNegocioService.obtenerAnalisis(inicio, fin);

        assertMoney("0.00", analisis.get("ticketPromedio"));
        assertMoney("0.00", analisis.get("puntoEquilibrio"));
        assertMoney("0.00", analisis.get("metaDiaria"));
        assertMoney("0.00", analisis.get("dineroReposicionSugerido"));
        assertEquals("AJUSTADO", analisis.get("estadoNegocio"));
    }

    private void assertMoney(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual));
    }

    private ConfigCuentaFija cuenta(String nombre, String categoria, String montoMensual) {
        ConfigCuentaFija cuenta = new ConfigCuentaFija();
        cuenta.setNombre(nombre);
        cuenta.setCategoria(categoria);
        cuenta.setMontoMensual(new BigDecimal(montoMensual));
        cuenta.setActiva(true);
        return cuenta;
    }

    private Producto producto(String codigo, String nombre, String categoria, int stock, String precioCompra) {
        Producto producto = new Producto();
        producto.setCodigoInterno(codigo);
        producto.setNombre(nombre);
        producto.setCategoria(categoria);
        producto.setStockActual(stock);
        producto.setPrecioCompra(new BigDecimal(precioCompra));
        producto.setEsLamina(false);
        return producto;
    }
}
