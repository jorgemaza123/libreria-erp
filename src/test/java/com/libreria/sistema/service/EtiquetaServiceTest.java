package com.libreria.sistema.service;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.EtiquetaPdfOpcionesDTO;
import com.libreria.sistema.repository.ProductoRepository;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtiquetaServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private ConfiguracionService configuracionService;

    @InjectMocks
    private EtiquetaService etiquetaService;

    @Test
    void generaPdfConDisenoGlobalYOverridePorProducto() throws Exception {
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setCodigoBarra("7751234567890");
        producto.setCodigoInterno("PROD-0001");
        producto.setNombre("Cartulina escolar azul");
        producto.setCategoria("UTILES");
        producto.setPrecioVenta(new BigDecimal("1.50"));

        Configuracion configuracion = new Configuracion();
        configuracion.setFormatoImpresion("A4");
        configuracion.setFormatoMoneda("S/");

        EtiquetaPdfOpcionesDTO opciones = new EtiquetaPdfOpcionesDTO();
        opciones.setProductoIds(List.of(1L));
        opciones.setCantidad(1);
        opciones.setPlantilla("SIN_PRECIO");
        opciones.setFormato("A4");
        opciones.setMostrarCategoria(true);
        opciones.setMostrarCodigoInterno(true);
        opciones.setColorTexto("#111827");
        opciones.setColorPrecio("#0f766e");
        opciones.setCodigoAltoMm(new BigDecimal("14"));

        EtiquetaPdfOpcionesDTO.ProductoEtiquetaOverrideDTO override = new EtiquetaPdfOpcionesDTO.ProductoEtiquetaOverrideDTO();
        override.setProductoId(1L);
        override.setCantidad(2);
        override.setNombrePersonalizado("Cartulina azul");
        override.setPrecioPersonalizado(new BigDecimal("1.80"));
        override.setMostrarPrecio(true);
        override.setMostrarCategoria(true);
        override.setMostrarCodigoInterno(true);
        override.setFuente("COURIER");
        override.setPrecioFontSize(new BigDecimal("14"));
        override.setCodigoAnchoMm(new BigDecimal("55"));
        override.setCodigoAltoMm(new BigDecimal("16"));
        override.setColorPrecio("#dc2626");
        opciones.setProductos(List.of(override));

        when(configuracionService.obtenerConfiguracion()).thenReturn(configuracion);
        when(productoRepository.findAllById(List.of(1L))).thenReturn(List.of(producto));

        byte[] pdf = etiquetaService.generarPdfEtiquetas(List.of(1L), 1, opciones);

        assertThat(pdf).isNotEmpty();
        assertThat(pdf[0]).isEqualTo((byte) '%');
        assertThat(new String(pdf, 0, Math.min(pdf.length, 5))).startsWith("%PDF");
    }

    @Test
    void generaA4ConVeinticuatroEtiquetasEnUnaSolaPagina() throws Exception {
        Producto producto1 = producto(1L, "7750000000011", "Lapicero azul", "1.00");
        Producto producto2 = producto(2L, "7750000000028", "Cartulina roja", "0.80");
        Producto producto3 = producto(3L, "7750000000035", "Cuaderno A4", "4.50");

        Configuracion configuracion = new Configuracion();
        configuracion.setFormatoImpresion("A4");
        configuracion.setFormatoMoneda("S/");

        EtiquetaPdfOpcionesDTO opciones = new EtiquetaPdfOpcionesDTO();
        opciones.setProductoIds(List.of(1L, 2L, 3L));
        opciones.setCantidad(8);
        opciones.setFormato("A4");

        when(configuracionService.obtenerConfiguracion()).thenReturn(configuracion);
        when(productoRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(producto1, producto2, producto3));

        byte[] pdf = etiquetaService.generarPdfEtiquetas(List.of(1L, 2L, 3L), 8, opciones);

        assertThat(contarPaginas(pdf)).isEqualTo(1);
    }

    @Test
    void creaNuevaHojaA4SoloCuandoSuperaLaCapacidad() throws Exception {
        Producto producto = producto(1L, "7750000000011", "Lapicero azul", "1.00");

        Configuracion configuracion = new Configuracion();
        configuracion.setFormatoImpresion("A4");
        configuracion.setFormatoMoneda("S/");

        EtiquetaPdfOpcionesDTO opciones = new EtiquetaPdfOpcionesDTO();
        opciones.setProductoIds(List.of(1L));
        opciones.setCantidad(25);
        opciones.setFormato("A4");

        when(configuracionService.obtenerConfiguracion()).thenReturn(configuracion);
        when(productoRepository.findAllById(List.of(1L))).thenReturn(List.of(producto));

        byte[] pdf = etiquetaService.generarPdfEtiquetas(List.of(1L), 25, opciones);

        assertThat(contarPaginas(pdf)).isEqualTo(2);
    }

    @Test
    void permiteEmpezarDespuesDeCasillasYaUsadasEnA4() throws Exception {
        Producto producto = producto(1L, "7750000000011", "Lapicero azul", "1.00");

        Configuracion configuracion = new Configuracion();
        configuracion.setFormatoImpresion("A4");
        configuracion.setFormatoMoneda("S/");

        EtiquetaPdfOpcionesDTO opciones = new EtiquetaPdfOpcionesDTO();
        opciones.setProductoIds(List.of(1L));
        opciones.setCantidad(19);
        opciones.setFormato("A4");
        opciones.setSaltarEtiquetasA4(5);

        when(configuracionService.obtenerConfiguracion()).thenReturn(configuracion);
        when(productoRepository.findAllById(List.of(1L))).thenReturn(List.of(producto));

        byte[] pdf = etiquetaService.generarPdfEtiquetas(List.of(1L), 19, opciones);

        assertThat(contarPaginas(pdf)).isEqualTo(1);
    }

    private Producto producto(Long id, String codigoBarra, String nombre, String precio) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setCodigoBarra(codigoBarra);
        producto.setCodigoInterno("PROD-" + id);
        producto.setNombre(nombre);
        producto.setCategoria("UTILES");
        producto.setPrecioVenta(new BigDecimal(precio));
        return producto;
    }

    private int contarPaginas(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            return reader.getNumberOfPages();
        } finally {
            reader.close();
        }
    }
}
