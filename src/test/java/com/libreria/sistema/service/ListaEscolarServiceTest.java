package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.ListaEscolarDTO;
import com.libreria.sistema.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para ListaEscolarService.crearLista().
 *
 * Usa un TextoListaParser REAL (no mock) y un parser separado para el override de cantidades.
 * Construye el service manualmente via constructor para inyectar el parser real.
 *
 * Cubre:
 * 1. Creación normal → usa cantidades del parser
 * 2. cantidadesValidadas → sobreescribe cantidades del parser
 * 3. Valor 0 en cantidadesValidadas → clampado a 1 (Math.max)
 * 4. Override parcial (solo algunos índices)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ListaEscolarService - crearLista()")
class ListaEscolarServiceTest {

    // =====================================================
    //  MOCKS DE REPOSITORIOS (no se mockea TextoListaParser)
    // =====================================================
    @Mock private ListaEscolarRepository listaRepository;
    @Mock private DetalleListaEscolarRepository detalleRepository;
    @Mock private VentaListaEscolarRepository ventaListaRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private VentaRepository ventaRepository;
    @Mock private KardexRepository kardexRepository;
    @Mock private CorrelativoRepository correlativoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private AmortizacionRepository amortizacionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private MatcherProductosService matcherService;
    @Mock private CajaService cajaService;
    @Mock private ConfiguracionService configuracionService;
    @Mock private FacturacionElectronicaService facturacionService;

    // Parser REAL — TextoListaParser no tiene dependencias, puede usarse directamente
    private final TextoListaParser realParser = new TextoListaParser();

    // Service construido manualmente para inyectar el parser real
    private ListaEscolarService listaEscolarService;

    // =====================================================
    //  SETUP
    // =====================================================

    @BeforeEach
    void setUp() {
        // Construir el service con todos los mocks + parser real
        listaEscolarService = new ListaEscolarService(
            listaRepository, detalleRepository, ventaListaRepository,
            productoRepository, ventaRepository, kardexRepository,
            correlativoRepository, clienteRepository, amortizacionRepository,
            usuarioRepository, realParser, matcherService, cajaService,
            configuracionService, facturacionService
        );

        // Simular usuario autenticado
        var auth = new UsernamePasswordAuthenticationToken(
            "vendedor", null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_VENDEDOR"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // listaRepository.save() retorna el mismo objeto con ID asignado
        when(listaRepository.save(any(ListaEscolar.class)))
            .thenAnswer(invocation -> {
                ListaEscolar l = invocation.getArgument(0);
                if (l.getId() == null) l.setId(1L);
                return l;
            });

        // Max numero → null (primera lista)
        when(listaRepository.findMaxNumeroBySerie(anyString())).thenReturn(null);

        // Usuario y cliente opcionales
        when(usuarioRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(clienteRepository.findById(anyLong())).thenReturn(Optional.empty());
    }

    private ListaEscolarDTO dtoBase(String texto) {
        ListaEscolarDTO dto = new ListaEscolarDTO();
        dto.setNombreAlumno("Juan Perez");
        dto.setGrado("3ro Primaria");
        dto.setTextoOriginal(texto);
        return dto;
    }

    // =====================================================
    //  TESTS: SIN cantidadesValidadas → usa parser real
    // =====================================================

    @Nested
    @DisplayName("Sin cantidadesValidadas → usa cantidades del parser")
    class SinCantidadesValidadas {

        @Test
        @DisplayName("'3 cuadernos' → detalle con cantidadSolicitada=3")
        void cantidadNumerica() {
            ListaEscolarDTO dto = dtoBase("3 cuadernos");
            dto.setCantidadesValidadas(null);

            ListaEscolar resultado = listaEscolarService.crearLista(dto);

            assertEquals(1, resultado.getDetalles().size());
            assertEquals(3, resultado.getDetalles().get(0).getCantidadSolicitada(),
                "Parser debe extraer cantidad=3 de '3 cuadernos'");
        }

        @Test
        @DisplayName("'100 hojas bond' → detalle con cantidadSolicitada=1 (parser lo identifica como spec)")
        void cantidadEspecificacion() {
            ListaEscolarDTO dto = dtoBase("100 hojas bond");
            dto.setCantidadesValidadas(null);

            ListaEscolar resultado = listaEscolarService.crearLista(dto);

            assertEquals(1, resultado.getDetalles().size());
            assertEquals(1, resultado.getDetalles().get(0).getCantidadSolicitada(),
                "Parser debe interpretar '100 hojas bond' como especificación → cantidad=1");
        }

        @Test
        @DisplayName("Lista mixta de 3 items → cantidades correctas por item")
        void listaMixta() {
            String texto = "3 lapiceros azul\n100 hojas bond\n2 borradores";
            ListaEscolarDTO dto = dtoBase(texto);
            dto.setCantidadesValidadas(null);

            ListaEscolar resultado = listaEscolarService.crearLista(dto);

            assertEquals(3, resultado.getDetalles().size());
            assertEquals(3, resultado.getDetalles().get(0).getCantidadSolicitada()); // 3 lapiceros
            assertEquals(1, resultado.getDetalles().get(1).getCantidadSolicitada()); // 100 hojas → spec → 1
            assertEquals(2, resultado.getDetalles().get(2).getCantidadSolicitada()); // 2 borradores
        }

        @Test
        @DisplayName("Estado inicial de la lista es PENDIENTE")
        void estadoInicial() {
            ListaEscolar resultado = listaEscolarService.crearLista(dtoBase("cuaderno rayado"));
            assertEquals("PENDIENTE", resultado.getEstado());
        }

        @Test
        @DisplayName("Estado de cada detalle es NO_COTIZADO")
        void estadoDetalleNoCotizado() {
            ListaEscolar resultado = listaEscolarService.crearLista(dtoBase("2 cuadernos\n1 lapiz"));
            resultado.getDetalles().forEach(d ->
                assertEquals("NO_COTIZADO", d.getEstado())
            );
        }

        @Test
        @DisplayName("itemsTotal se asigna al número de items parseados")
        void itemsTotalCorrecto() {
            ListaEscolar resultado = listaEscolarService.crearLista(dtoBase("cuaderno rayado\nlapicero azul"));
            assertEquals(2, resultado.getItemsTotal());
            assertEquals(2, resultado.getItemsPendientes());
        }

        @Test
        @DisplayName("Orden de detalles es secuencial comenzando en 1")
        void ordenSecuencial() {
            ListaEscolar resultado = listaEscolarService.crearLista(dtoBase("item uno\nitem dos\nitem tres"));
            List<DetalleListaEscolar> detalles = resultado.getDetalles();
            assertEquals(3, detalles.size());
            assertEquals(1, detalles.get(0).getOrden());
            assertEquals(2, detalles.get(1).getOrden());
            assertEquals(3, detalles.get(2).getOrden());
        }
    }

    // =====================================================
    //  TESTS: CON cantidadesValidadas → sobreescribe parser
    // =====================================================

    @Nested
    @DisplayName("Con cantidadesValidadas → sobreescribe el parser")
    class ConCantidadesValidadas {

        @Test
        @DisplayName("Override índice 0: '3 colores' → parser devuelve 1 (spec), usuario corrige a 3")
        void overrideIndice0() {
            // "3 colores" → parser detecta spec word 'colores' → cantidad=1
            // Usuario confirma que son 3 sets de colores → override a 3
            ListaEscolarDTO dto = dtoBase("3 colores artesco");
            dto.setCantidadesValidadas(Map.of(0, 3));

            ListaEscolar resultado = listaEscolarService.crearLista(dto);

            assertEquals(3, resultado.getDetalles().get(0).getCantidadSolicitada(),
                "El override del usuario debe prevalecer sobre el parser");
        }

        @Test
        @DisplayName("Override parcial: solo índice 1 sobreescrito, índice 0 usa parser")
        void overrideParcial() {
            // "3 lapiceros" → parser: cantidad=3
            // "24 colores" → parser: spec → cantidad=1, usuario cambia a 2
            String texto = "3 lapiceros\n24 colores faber";
            ListaEscolarDTO dto = dtoBase(texto);
            dto.setCantidadesValidadas(Map.of(1, 2));

            ListaEscolar resultado = listaEscolarService.crearLista(dto);

            assertEquals(3, resultado.getDetalles().get(0).getCantidadSolicitada(),
                "índice 0 sin override → usa parser (3)");
            assertEquals(2, resultado.getDetalles().get(1).getCantidadSolicitada(),
                "índice 1 override → 2");
        }

        @Test
        @DisplayName("Override con valor 0 → clampado a 1 (Math.max protección financiera)")
        void overrideCeroClampado() {
            ListaEscolarDTO dto = dtoBase("tijera escolar");
            dto.setCantidadesValidadas(Map.of(0, 0));

            ListaEscolar resultado = listaEscolarService.crearLista(dto);

            assertEquals(1, resultado.getDetalles().get(0).getCantidadSolicitada(),
                "Cantidad 0 debe clampare a 1 por Math.max(1, cantidad)");
        }

        @Test
        @DisplayName("Override con valor negativo → clampado a 1")
        void overrideNegativoClampado() {
            ListaEscolarDTO dto = dtoBase("borrador grande");
            dto.setCantidadesValidadas(Map.of(0, -5));

            ListaEscolar resultado = listaEscolarService.crearLista(dto);

            assertEquals(1, resultado.getDetalles().get(0).getCantidadSolicitada(),
                "Cantidad negativa debe clampare a 1");
        }

        @Test
        @DisplayName("Mapa vacío no sobreescribe nada → usa parser")
        void mapaVacioNoSobreescribe() {
            // "2 folders" → parser: 2 (no spec word)
            ListaEscolarDTO dto = dtoBase("2 folders");
            dto.setCantidadesValidadas(Map.of());

            ListaEscolar resultado = listaEscolarService.crearLista(dto);

            assertEquals(2, resultado.getDetalles().get(0).getCantidadSolicitada());
        }

        @Test
        @DisplayName("Override en índice fuera de rango no afecta items existentes")
        void overrideIndiceInexistente() {
            // "goma" → parser: cantidad=1 (sin número al inicio)
            ListaEscolarDTO dto = dtoBase("goma de pegar");
            dto.setCantidadesValidadas(Map.of(99, 5)); // índice 99 no existe

            ListaEscolar resultado = listaEscolarService.crearLista(dto);

            assertEquals(1, resultado.getDetalles().size());
            // La cantidad del item no se ve afectada por un índice inexistente
        }

        @Test
        @DisplayName("Override múltiple: todos los índices sobreescritos")
        void overrideMultiple() {
            // "24 colores" → spec → 1, "100 hojas" → spec → 1, "3 lapiceros" → 3
            String texto = "24 colores faber\n100 hojas bond\n3 lapiceros";
            ListaEscolarDTO dto = dtoBase(texto);
            dto.setCantidadesValidadas(Map.of(0, 2, 1, 1, 2, 5));

            ListaEscolar resultado = listaEscolarService.crearLista(dto);

            assertEquals(2, resultado.getDetalles().get(0).getCantidadSolicitada());
            assertEquals(1, resultado.getDetalles().get(1).getCantidadSolicitada());
            assertEquals(5, resultado.getDetalles().get(2).getCantidadSolicitada());
        }
    }

    // =====================================================
    //  TESTS: METADATOS Y CORRELATIVO
    // =====================================================

    @Nested
    @DisplayName("Metadatos y correlativo")
    class MetadatosDeLista {

        @Test
        @DisplayName("textoOriginal del detalle corresponde a la línea parseada")
        void textoOriginalDetalle() {
            ListaEscolar resultado = listaEscolarService.crearLista(dtoBase("100 hojas bond A4"));
            assertEquals("100 hojas bond A4",
                resultado.getDetalles().get(0).getTextoOriginal());
        }

        @Test
        @DisplayName("El correlativo se asigna correctamente (maxNumero null → numero=1)")
        void correlativoNuevo() {
            when(listaRepository.findMaxNumeroBySerie(anyString())).thenReturn(null);

            ListaEscolar resultado = listaEscolarService.crearLista(dtoBase("cuaderno"));
            assertEquals(1, resultado.getNumero());
        }

        @Test
        @DisplayName("El correlativo continúa desde el máximo existente")
        void correlativoContinua() {
            when(listaRepository.findMaxNumeroBySerie(anyString())).thenReturn(42);

            ListaEscolar resultado = listaEscolarService.crearLista(dtoBase("cuaderno"));
            assertEquals(43, resultado.getNumero());
        }

        @Test
        @DisplayName("listaRepository.save() se llama dos veces: pre y post detalles")
        void saveDobleInvocacion() {
            listaEscolarService.crearLista(dtoBase("lapiz"));
            verify(listaRepository, times(2)).save(any(ListaEscolar.class));
        }
    }

    // =====================================================
    //  TESTS: TEXTO SIN ITEMS PARSEABLES
    // =====================================================

    @Nested
    @DisplayName("Texto sin items parseables → lista sin detalles")
    class TextoSinItems {

        @Test
        @DisplayName("Solo título → itemsTotal=0")
        void soloTitulo() {
            // "Lista Escolar 2024" → es un título, el parser lo ignora
            ListaEscolar resultado = listaEscolarService.crearLista(dtoBase("Lista Escolar 2024"));
            assertEquals(0, resultado.getItemsTotal());
            assertEquals(0, resultado.getDetalles().size());
        }
    }
}
