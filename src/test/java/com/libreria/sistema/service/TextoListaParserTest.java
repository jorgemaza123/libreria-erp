package com.libreria.sistema.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para TextoListaParser.
 *
 * Cubre la lógica conservadora de interpretación de cantidades:
 * - Números > 5 son siempre especificación del producto (ej: "100 hojas")
 * - Números ≤ 5 con spec word → cantidad=1, alertaCantidad=true
 * - Números ≤ 5 sin spec word → cantidad=N (compra real)
 * - textoNormalizado incluye el número cuando es especificación
 */
@DisplayName("TextoListaParser - Lógica de interpretación de cantidades")
class TextoListaParserTest {

    private TextoListaParser parser;

    @BeforeEach
    void setUp() {
        parser = new TextoListaParser();
    }

    // =========================================================
    //  CASOS BÁSICOS DE CANTIDAD DE COMPRA (1-5, sin spec word)
    // =========================================================

    @Nested
    @DisplayName("Cantidad inequívoca de compra (número ≤ 5, sin spec word)")
    class CantidadCompraInequivoca {

        @Test
        @DisplayName("'3 cuadernos' → cantidad=3, esEspecificacion=false")
        void cuadernos() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("3 cuadernos");
            assertEquals(1, items.size());
            TextoListaParser.ItemParseado item = items.get(0);
            assertEquals(3, item.getCantidad());
            assertFalse(item.isEsEspecificacion());
            assertFalse(item.isAlertaCantidad());
        }

        @Test
        @DisplayName("'2 lapiceros' → cantidad=2, esEspecificacion=false")
        void lapiceros() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("2 lapiceros");
            assertEquals(1, items.size());
            assertEquals(2, items.get(0).getCantidad());
            assertFalse(items.get(0).isEsEspecificacion());
        }

        @Test
        @DisplayName("'5 folders' → cantidad=5, esEspecificacion=false (folders no es spec word)")
        void folders() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("5 folders");
            assertEquals(1, items.size());
            assertEquals(5, items.get(0).getCantidad());
            assertFalse(items.get(0).isEsEspecificacion());
        }

        @Test
        @DisplayName("'1 tijera' → cantidad=1, esEspecificacion=false")
        void tijera() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("1 tijera");
            assertEquals(1, items.size());
            assertEquals(1, items.get(0).getCantidad());
            assertFalse(items.get(0).isEsEspecificacion());
        }

        @Test
        @DisplayName("'4. borrador grande' → cantidad=4 (formato con punto)")
        void borradorConPunto() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("4. borrador grande");
            assertEquals(1, items.size());
            assertEquals(4, items.get(0).getCantidad());
            assertFalse(items.get(0).isEsEspecificacion());
        }
    }

    // =========================================================
    //  ESPECIFICACIÓN INEQUÍVOCA (número > 5)
    // =========================================================

    @Nested
    @DisplayName("Especificación inequívoca (número > 5)")
    class EspecificacionInequivoca {

        @Test
        @DisplayName("'100 hojas bond' → cantidad=1, esEspecificacion=true, alertaCantidad=false")
        void hojasGrande() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("100 hojas bond");
            assertEquals(1, items.size());
            TextoListaParser.ItemParseado item = items.get(0);
            assertEquals(1, item.getCantidad());
            assertTrue(item.isEsEspecificacion());
            assertFalse(item.isAlertaCantidad(), "Número > 5 no es ambiguo, no debe tener alerta");
        }

        @Test
        @DisplayName("'24 colores faber castell' → cantidad=1, esEspecificacion=true")
        void coloresGrande() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("24 colores faber castell");
            assertEquals(1, items.size());
            TextoListaParser.ItemParseado item = items.get(0);
            assertEquals(1, item.getCantidad());
            assertTrue(item.isEsEspecificacion());
            assertFalse(item.isAlertaCantidad());
        }

        @Test
        @DisplayName("'50 paginas cuaderno' → cantidad=1, esEspecificacion=true")
        void paginasGrande() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("50 paginas cuaderno");
            assertEquals(1, items.size());
            assertEquals(1, items.get(0).getCantidad());
            assertTrue(items.get(0).isEsEspecificacion());
            assertFalse(items.get(0).isAlertaCantidad());
        }

        @Test
        @DisplayName("'6 colores' → cantidad=1, esEspecificacion=true (6 > 5, regla 1 aplica)")
        void seis_colores_sobre_umbral() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("6 colores");
            assertEquals(1, items.size());
            assertEquals(1, items.get(0).getCantidad());
            assertTrue(items.get(0).isEsEspecificacion());
            assertFalse(items.get(0).isAlertaCantidad(), "6 > 5, es inequívoco, sin alerta");
        }

        @Test
        @DisplayName("'12 colores artesco' → cantidad=1, textoNormalizado incluye '12'")
        void textoNormalizadoIncluyeNumero() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("12 colores artesco");
            assertEquals(1, items.size());
            TextoListaParser.ItemParseado item = items.get(0);
            assertEquals(1, item.getCantidad());
            assertTrue(item.isEsEspecificacion());
            // El número debe quedar en textoNormalizado para mejor matching de producto
            String norm = item.getTextoNormalizado();
            assertTrue(norm.contains("12"), "textoNormalizado debe incluir el número de especificación: " + norm);
        }
    }

    // =========================================================
    //  CASOS AMBIGUOS (número ≤ 5, pero spec word presente)
    // =========================================================

    @Nested
    @DisplayName("Casos ambiguos (número ≤ 5 + spec word → alertaCantidad=true)")
    class CasosAmbiguos {

        @Test
        @DisplayName("'3 colores' → cantidad=1, alertaCantidad=true (ambiguo: 3 colores o 3 unidades de color)")
        void tresColores() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("3 colores");
            assertEquals(1, items.size());
            TextoListaParser.ItemParseado item = items.get(0);
            assertEquals(1, item.getCantidad());
            assertTrue(item.isEsEspecificacion());
            assertTrue(item.isAlertaCantidad(), "Caso ambiguo: 3 ≤ 5 pero 'colores' es spec word");
        }

        @Test
        @DisplayName("'2 hojas' → cantidad=1, alertaCantidad=true")
        void dosHojas() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("2 hojas");
            assertEquals(1, items.size());
            TextoListaParser.ItemParseado item = items.get(0);
            assertEquals(1, item.getCantidad());
            assertTrue(item.isEsEspecificacion());
            assertTrue(item.isAlertaCantidad());
        }

        @Test
        @DisplayName("'4 plumones delgados' → cantidad=1, alertaCantidad=true")
        void cuatroPlumones() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("4 plumones delgados");
            assertEquals(1, items.size());
            TextoListaParser.ItemParseado item = items.get(0);
            assertEquals(1, item.getCantidad());
            assertTrue(item.isEsEspecificacion());
            assertTrue(item.isAlertaCantidad());
        }

        @Test
        @DisplayName("'1 cm regla' → cantidad=1, alertaCantidad=true (especificación de medida)")
        void unCm() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("1 cm regla");
            // La línea puede parsearse como spec (cm es spec word)
            if (!items.isEmpty()) {
                TextoListaParser.ItemParseado item = items.get(0);
                assertEquals(1, item.getCantidad());
                assertTrue(item.isEsEspecificacion());
                assertTrue(item.isAlertaCantidad());
            }
        }

        @Test
        @DisplayName("'5 unidades lapiz' → cantidad=1, alertaCantidad=true (unidades es spec word)")
        void cincoUnidades() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("5 unidades lapiz");
            assertEquals(1, items.size());
            TextoListaParser.ItemParseado item = items.get(0);
            assertEquals(1, item.getCantidad());
            assertTrue(item.isEsEspecificacion());
            assertTrue(item.isAlertaCantidad());
        }
    }

    // =========================================================
    //  textoOriginal SE PRESERVA INTACTO
    // =========================================================

    @Nested
    @DisplayName("textoOriginal se preserva sin modificar")
    class TextoOriginalPreservado {

        @Test
        @DisplayName("textoOriginal debe ser la línea tal como se recibió")
        void textoOriginalPreservado() {
            String linea = "100 hojas bond A4";
            List<TextoListaParser.ItemParseado> items = parser.parsear(linea);
            assertEquals(1, items.size());
            assertEquals(linea.trim(), items.get(0).getTextoOriginal());
        }

        @Test
        @DisplayName("textoOriginal no se modifica en cantidad de compra normal")
        void textoOriginalCompra() {
            String linea = "3 cuadernos rayados";
            List<TextoListaParser.ItemParseado> items = parser.parsear(linea);
            assertEquals(1, items.size());
            assertEquals(linea.trim(), items.get(0).getTextoOriginal());
        }
    }

    // =========================================================
    //  textoNormalizado — SPEC NUMBER EN TEXTO DE BÚSQUEDA
    // =========================================================

    @Nested
    @DisplayName("textoNormalizado: número de spec queda para mejor matching")
    class TextoNormalizadoConSpec {

        @Test
        @DisplayName("'100 hojas bond' → textoNormalizado contiene '100'")
        void specNumeroEnNormalizado() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("100 hojas bond");
            assertEquals(1, items.size());
            assertTrue(items.get(0).getTextoNormalizado().contains("100"),
                "El número de spec debe quedar en textoNormalizado para búsqueda de producto");
        }

        @Test
        @DisplayName("'3 cuadernos' → textoNormalizado NO contiene '3' (es cantidad de compra)")
        void cantidadCompraNoEnNormalizado() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("3 cuadernos");
            assertEquals(1, items.size());
            // textoNormalizado = descripción sin el número (ej: "cuaderno")
            assertFalse(items.get(0).getTextoNormalizado().contains("3"),
                "La cantidad de compra no debe quedar en textoNormalizado");
        }
    }

    // =========================================================
    //  CANTIDADES EN PALABRAS
    // =========================================================

    @Nested
    @DisplayName("Cantidades expresadas en palabras")
    class CantidadesPalabras {

        @Test
        @DisplayName("'dos lapiceros' → cantidad=2")
        void dosLapiceros() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("dos lapiceros");
            assertEquals(1, items.size());
            assertEquals(2, items.get(0).getCantidad());
            assertFalse(items.get(0).isEsEspecificacion());
        }

        @Test
        @DisplayName("'tres cuadernos' → cantidad=3")
        void tresCuadernos() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("tres cuadernos");
            assertEquals(1, items.size());
            assertEquals(3, items.get(0).getCantidad());
        }

        @Test
        @DisplayName("'una tijera' → cantidad=1")
        void unaTijera() {
            List<TextoListaParser.ItemParseado> items = parser.parsear("una tijera");
            assertEquals(1, items.size());
            assertEquals(1, items.get(0).getCantidad());
        }
    }

    // =========================================================
    //  CASOS LÍMITE Y TEXTO VACÍO
    // =========================================================

    @Nested
    @DisplayName("Casos límite")
    class CasosLimite {

        @Test
        @DisplayName("Texto vacío → lista vacía")
        void textoVacio() {
            assertEquals(0, parser.parsear("").size());
            assertEquals(0, parser.parsear(null).size());
            assertEquals(0, parser.parsear("   ").size());
        }

        @Test
        @DisplayName("Solo símbolos → lista vacía")
        void soloSimbolos() {
            assertEquals(0, parser.parsear("---").size());
            assertEquals(0, parser.parsear("• ○ ◦").size());
        }

        @Test
        @DisplayName("Línea de título es ignorada")
        void tituloIgnorado() {
            String texto = "Lista Escolar 2024\ncuaderno rayado";
            List<TextoListaParser.ItemParseado> items = parser.parsear(texto);
            assertEquals(1, items.size());
            // Solo el cuaderno, no el título
            assertTrue(items.get(0).getTextoNormalizado().contains("cuaderno"));
        }

        @Test
        @DisplayName("Lista con viñetas → extrae correctamente")
        void listaConVinetas() {
            String texto = "• 3 cuadernos\n• 100 hojas bond\n• 2 lapiceros";
            List<TextoListaParser.ItemParseado> items = parser.parsear(texto);
            assertEquals(3, items.size());
            assertEquals(3, items.get(0).getCantidad());    // 3 cuadernos
            assertEquals(1, items.get(1).getCantidad());    // 100 hojas → spec → 1
            assertEquals(2, items.get(2).getCantidad());    // 2 lapiceros
        }

        @Test
        @DisplayName("Lista mixta real de colegio peruano")
        void listaMixta() {
            String texto =
                "1 cuaderno cuadriculado 60 hojas\n" +
                "3 lapiceros azul\n" +
                "24 colores faber\n" +
                "1 regla 30 cm\n" +
                "2 borradores\n" +
                "100 hojas bond A4";

            List<TextoListaParser.ItemParseado> items = parser.parsear(texto);
            assertEquals(6, items.size());

            // cuaderno cuadriculado (sin número al inicio)
            assertEquals(1, items.get(0).getCantidad());

            // 3 lapiceros azul → cantidad=3 (lapiceros no es spec word)
            assertEquals(3, items.get(1).getCantidad());
            assertFalse(items.get(1).isEsEspecificacion());

            // 24 colores faber → spec (>5 Y colores es spec word)
            assertEquals(1, items.get(2).getCantidad());
            assertTrue(items.get(2).isEsEspecificacion());
            assertFalse(items.get(2).isAlertaCantidad()); // 24 > 5, no ambiguo

            // 1 regla 30 cm → cantidad=1
            assertEquals(1, items.get(3).getCantidad());

            // 2 borradores → cantidad=2
            assertEquals(2, items.get(4).getCantidad());

            // 100 hojas bond → spec, cantidad=1
            assertEquals(1, items.get(5).getCantidad());
            assertTrue(items.get(5).isEsEspecificacion());
        }
    }

    // =========================================================
    //  NORMALIZACIÓN Y SINÓNIMOS
    // =========================================================

    @Nested
    @DisplayName("normalizarParaBusqueda - sinónimos y abreviaturas")
    class Normalizacion {

        @Test
        @DisplayName("diurex → cinta adhesiva")
        void diurex() {
            String result = parser.normalizarParaBusqueda("diurex");
            assertEquals("cinta adhesiva", result);
        }

        @Test
        @DisplayName("tajador → sacapuntas")
        void tajador() {
            String result = parser.normalizarParaBusqueda("tajador");
            assertEquals("sacapuntas", result);
        }

        @Test
        @DisplayName("cuad → cuaderno")
        void abreviaturaCuad() {
            String result = parser.normalizarParaBusqueda("cuad rayado");
            assertTrue(result.contains("cuaderno"));
        }

        @Test
        @DisplayName("Acentos eliminados: lápiz → lapiz")
        void acentosEliminados() {
            String result = parser.normalizarParaBusqueda("lápiz");
            assertEquals("lapiz", result);
        }

        @Test
        @DisplayName("Texto en mayúsculas normalizado a minúsculas")
        void mayusculas() {
            String result = parser.normalizarParaBusqueda("CUADERNO RAYADO");
            assertEquals("cuaderno rayado", result);
        }
    }
}
