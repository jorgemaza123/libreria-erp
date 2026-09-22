package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.ReposicionSugeridaDTO;
import com.libreria.sistema.repository.DetalleVentaRepository;
import com.libreria.sistema.repository.KardexRepository;
import com.libreria.sistema.repository.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private KardexRepository kardexRepository;

    @Mock
    private DetalleVentaRepository detalleVentaRepository;

    @InjectMocks
    private StockService stockService;

    @Test
    @DisplayName("Sugiere reposicion por stock bajo, rotacion y temporada activa")
    void obtenerReposicionSugerida_DebePriorizarProductosAccionables() {
        Producto cartulina = producto(1L, "CARTULINA ROJA", 5, 10, 30, false, null, "1.00");
        Producto lazo = producto(2L, "LAZO AMARILLO", 8, 2, null, true, 24, "0.50");
        Producto lento = producto(3L, "CUADERNO LENTO", 100, 5, 120, false, null, "3.00");

        when(productoRepository.findByActivoTrue()).thenReturn(List.of(cartulina, lazo, lento));
        when(detalleVentaRepository.cantidadVendidaPorProducto(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(
                        List.<Object[]>of(new Object[]{1L, new BigDecimal("40.00")}),
                        List.<Object[]>of(new Object[]{1L, new BigDecimal("12.00")})
                );

        List<ReposicionSugeridaDTO> sugerencias = stockService.obtenerReposicionSugerida();

        assertEquals(2, sugerencias.size());
        ReposicionSugeridaDTO primera = sugerencias.get(0);
        assertEquals(1L, primera.getProductoId());
        assertEquals("URGENTE", primera.getPrioridad());
        assertEquals(25, primera.getCantidadSugerida());
        assertEquals(0, new BigDecimal("25.00").compareTo(primera.getCostoReposicion()));

        ReposicionSugeridaDTO temporada = sugerencias.stream()
                .filter(s -> s.getProductoId().equals(2L))
                .findFirst()
                .orElseThrow();
        assertTrue(temporada.getTemporadaActiva());
        assertEquals("ALTA", temporada.getPrioridad());
        assertEquals(16, temporada.getCantidadSugerida());
        assertEquals(0, new BigDecimal("8.00").compareTo(temporada.getCostoReposicion()));
    }

    @Test
    @DisplayName("Actualiza configuracion de reposicion sin tocar el stock real")
    void actualizarConfiguracionReposicion_DebeGuardarMinimoMaximoYTemporada() {
        Producto producto = producto(5L, "PLUMON", 12, 4, 20, false, null, "2.00");
        when(productoRepository.findById(5L)).thenReturn(Optional.of(producto));

        stockService.actualizarConfiguracionReposicion(5L, -1, 0, true, 18, "admin");

        assertEquals(0, producto.getStockMinimo());
        assertNull(producto.getStockMaximo());
        assertTrue(producto.getTemporadaActiva());
        assertEquals(18, producto.getStockObjetivoTemporada());
        assertEquals(12, producto.getStockActual());
        verify(productoRepository).save(producto);
    }

    @Test
    @DisplayName("Actualiza visibilidad y orden del POS rapido desde Control de Stock")
    void actualizarConfiguracionReposicion_DebeGuardarPosRapido() {
        Producto producto = producto(7L, "CARTULINA A4", 12, 4, 20, false, null, "0.50");
        when(productoRepository.findById(7L)).thenReturn(Optional.of(producto));

        stockService.actualizarConfiguracionReposicion(7L, 4, 20, true, 30, true, 2, "admin");

        assertTrue(producto.getTemporadaActiva());
        assertEquals(30, producto.getStockObjetivoTemporada());
        assertTrue(producto.getPosRapido());
        assertEquals(2, producto.getPosRapidoOrden());
        verify(productoRepository).save(producto);
    }

    @Test
    @DisplayName("Permite apagar temporada conservando niveles normales")
    void actualizarConfiguracionReposicion_DebeApagarTemporada() {
        Producto producto = producto(6L, "PAPEL FOTO", 6, 3, 15, true, 30, "0.80");
        producto.setPosRapido(true);
        producto.setPosRapidoOrden(4);
        when(productoRepository.findById(6L)).thenReturn(Optional.of(producto));

        stockService.actualizarConfiguracionReposicion(6L, 3, 15, false, null, "admin");

        assertEquals(3, producto.getStockMinimo());
        assertEquals(15, producto.getStockMaximo());
        assertFalse(producto.getTemporadaActiva());
        assertNull(producto.getStockObjetivoTemporada());
        assertTrue(producto.getPosRapido());
        assertEquals(4, producto.getPosRapidoOrden());
        verify(productoRepository).save(producto);
    }

    private Producto producto(Long id,
                              String nombre,
                              Integer stock,
                              Integer minimo,
                              Integer maximo,
                              boolean temporada,
                              Integer objetivoTemporada,
                              String precioCompra) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setCategoria("UTILES");
        p.setActivo(true);
        p.setEsLamina(false);
        p.setStockActual(stock);
        p.setStockMinimo(minimo);
        p.setStockMaximo(maximo);
        p.setTemporadaActiva(temporada);
        p.setStockObjetivoTemporada(objetivoTemporada);
        p.setPrecioCompra(new BigDecimal(precioCompra));
        return p;
    }
}
