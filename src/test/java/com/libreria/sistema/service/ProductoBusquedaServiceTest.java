package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoBusquedaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoBusquedaServiceTest {

    @Mock
    private ProductoBusquedaRepository busquedaRepository;

    @Mock
    private BusquedaAvanzadaHelper busquedaAvanzadaHelper;

    @Test
    void buscarDebePriorizarCoincidenciasFuertesYFiltrarRuido() {
        Producto ruidoCategoria = producto(1L, "Papel lustre rojo", "PAPEL", "CARTULINAS", null, 20);
        Producto exacto = producto(2L, "Cartulina blanca A4", "CART-BLA", "CARTULINAS", null, 3);
        Producto incompleto = producto(3L, "Cartulina roja A4", "CART-ROJ", "CARTULINAS", null, 30);

        when(busquedaRepository.buscarPorCodigoExacto(anyString())).thenReturn(List.of());
        when(busquedaRepository.omnibuscarTokenizado("cartulina", "blanca", "", "", 40))
                .thenReturn(List.of(ruidoCategoria, exacto, incompleto));

        ProductoBusquedaService service = new ProductoBusquedaService(busquedaRepository, busquedaAvanzadaHelper);

        List<Producto> resultados = service.buscar("cartulina blanca", 10);

        assertEquals(1, resultados.size());
        assertEquals(exacto.getId(), resultados.get(0).getId());
    }

    @Test
    void buscarDebeMantenerCodigoExactoComoPrimerCamino() {
        Producto lapicero = producto(9L, "Lapicero azul", "LAPICERO_AZUL", "UTILES", null, 100);
        when(busquedaRepository.buscarPorCodigoExacto("LAPICERO_AZUL")).thenReturn(List.of(lapicero));

        ProductoBusquedaService service = new ProductoBusquedaService(busquedaRepository, busquedaAvanzadaHelper);

        List<Producto> resultados = service.buscar("LAPICERO_AZUL", 10);

        assertEquals(1, resultados.size());
        assertEquals("Lapicero azul", resultados.get(0).getNombre());
    }

    @Test
    void buscarConTextoMuyCortoNoDebeSugerirCoincidenciasVagas() {
        Producto anillado = producto(4L, "Anillado", "ANILLADO", "SERVICIOS", null, 999);
        Producto ruido = producto(5L, "Papel bond", "PAPEL", "ALMACEN", null, 40);

        when(busquedaRepository.buscarPorCodigoExacto(anyString())).thenReturn(List.of());
        when(busquedaRepository.omnibuscarSimple("a", 40)).thenReturn(List.of(ruido, anillado));

        ProductoBusquedaService service = new ProductoBusquedaService(busquedaRepository, busquedaAvanzadaHelper);

        List<Producto> resultados = service.buscar("a", 10);

        assertEquals(1, resultados.size());
        assertEquals(anillado.getId(), resultados.get(0).getId());
    }

    @Test
    void buscarPuedeAceptarFuzzySoloCuandoLaSimilitudEsAlta() {
        Producto cuaderno = producto(6L, "Cuaderno cuadriculado", "CUAD-100", "UTILES", null, 12);

        when(busquedaRepository.buscarPorCodigoExacto(anyString())).thenReturn(List.of());
        when(busquedaRepository.omnibuscarSimple("cuadeno", 40)).thenReturn(List.of());
        when(busquedaAvanzadaHelper.buscarFullText("cuadeno", 40)).thenReturn(List.of());
        when(busquedaAvanzadaHelper.buscarFuzzyTokenizado("cuadeno", "", "", 0.35, 40))
                .thenReturn(List.of(cuaderno));

        ProductoBusquedaService service = new ProductoBusquedaService(busquedaRepository, busquedaAvanzadaHelper);

        List<Producto> resultados = service.buscar("cuadeno", 10);

        assertEquals(1, resultados.size());
        assertTrue(resultados.get(0).getNombre().startsWith("Cuaderno"));
    }

    private Producto producto(Long id, String nombre, String codigoInterno, String categoria, String color, int stock) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setCodigoInterno(codigoInterno);
        p.setCategoria(categoria);
        p.setColor(color);
        p.setStockActual(stock);
        p.setStockMinimo(5);
        p.setPrecioCompra(BigDecimal.ONE);
        p.setPrecioVenta(new BigDecimal("2.00"));
        p.setActivo(true);
        return p;
    }
}
