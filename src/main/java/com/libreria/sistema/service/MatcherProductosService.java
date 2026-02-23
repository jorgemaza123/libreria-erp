package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio para hacer matching automático entre texto de lista escolar y productos en BD.
 * Genera cotizaciones en 3 niveles: Económico, Medio, Premium.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MatcherProductosService {

    private final ProductoBusquedaService busquedaService;
    private final TextoListaParser parser;

    // Configuración
    private static final int MAX_RESULTADOS_BUSQUEDA = 50;
    private static final double UMBRAL_CONFIANZA_MINIMO = 0.3;

    /**
     * Resultado del matching de un item.
     */
    @Data
    public static class ResultadoMatch {
        private String textoOriginal;
        private int cantidad;
        private double confianza;

        // Producto principal (mejor match general)
        private Producto productoPrincipal;

        // Productos por nivel de precio
        private Producto productoEconomico;
        private BigDecimal precioEconomico;

        private Producto productoMedio;
        private BigDecimal precioMedio;

        private Producto productoPremium;
        private BigDecimal precioPremium;

        // Lista de todas las alternativas encontradas
        private List<Producto> alternativas = new ArrayList<>();

        // Indica si se encontró al menos un match
        private boolean tieneMatch;

        // Mensaje informativo
        private String mensaje;
    }

    /**
     * Busca productos que coincidan con un texto de item.
     *
     * @param textoItem Texto del item (ya normalizado)
     * @param cantidad Cantidad solicitada
     * @return Resultado con productos en 3 niveles de precio
     */
    public ResultadoMatch buscarMatch(String textoItem, int cantidad) {
        ResultadoMatch resultado = new ResultadoMatch();
        resultado.setTextoOriginal(textoItem);
        resultado.setCantidad(cantidad);

        // Buscar productos usando búsqueda flexible multi-pass (exclusiva listas escolares)
        List<Producto> encontrados = busquedaService.buscarFlexibleEscolar(textoItem, MAX_RESULTADOS_BUSQUEDA);

        if (encontrados.isEmpty()) {
            resultado.setTieneMatch(false);
            resultado.setConfianza(0.0);
            resultado.setMensaje("No se encontraron productos para: " + textoItem);
            log.debug("Sin matches para: {}", textoItem);
            return resultado;
        }

        // Filtrar productos con stock > 0 (no exige stock >= cantidad para no perder matches)
        List<Producto> conStock = encontrados.stream()
            .filter(p -> "SERVICIO".equals(p.getTipo()) ||
                        (p.getStockActual() != null && p.getStockActual() > 0))
            .collect(Collectors.toList());

        // Si no hay con stock suficiente, usar todos los encontrados pero marcar
        List<Producto> paraClasificar = conStock.isEmpty() ? encontrados : conStock;

        // Clasificar por rangos de precio
        clasificarPorNiveles(resultado, paraClasificar, cantidad);

        // Establecer producto principal (el mejor match)
        resultado.setProductoPrincipal(paraClasificar.get(0));
        resultado.setAlternativas(paraClasificar);
        resultado.setTieneMatch(true);
        resultado.setConfianza(calcularConfianza(textoItem, paraClasificar.get(0)));

        if (conStock.isEmpty()) {
            resultado.setMensaje("Productos encontrados pero sin stock suficiente");
        } else {
            resultado.setMensaje("OK - " + paraClasificar.size() + " productos encontrados");
        }

        log.debug("Match para '{}': {} productos, confianza={}",
            textoItem, paraClasificar.size(), resultado.getConfianza());

        return resultado;
    }

    /**
     * Clasifica los productos en 3 niveles de precio.
     */
    private void clasificarPorNiveles(ResultadoMatch resultado, List<Producto> productos, int cantidad) {
        if (productos.isEmpty()) return;

        // Ordenar por precio
        List<Producto> ordenados = productos.stream()
            .filter(p -> p.getPrecioVenta() != null && p.getPrecioVenta().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing(Producto::getPrecioVenta))
            .collect(Collectors.toList());

        if (ordenados.isEmpty()) {
            // Si ninguno tiene precio, usar el primero encontrado
            Producto primero = productos.get(0);
            resultado.setProductoEconomico(primero);
            resultado.setProductoMedio(primero);
            resultado.setProductoPremium(primero);
            resultado.setPrecioEconomico(primero.getPrecioVenta());
            resultado.setPrecioMedio(primero.getPrecioVenta());
            resultado.setPrecioPremium(primero.getPrecioVenta());
            return;
        }

        int total = ordenados.size();

        // Económico: primer tercio (más barato)
        Producto economico = ordenados.get(0);
        resultado.setProductoEconomico(economico);
        resultado.setPrecioEconomico(economico.getPrecioVenta());

        // Premium: último tercio (más caro)
        Producto premium = ordenados.get(total - 1);
        resultado.setProductoPremium(premium);
        resultado.setPrecioPremium(premium.getPrecioVenta());

        // Medio: tercio del medio o promedio
        int indiceMedio = total / 2;
        Producto medio = ordenados.get(indiceMedio);
        resultado.setProductoMedio(medio);
        resultado.setPrecioMedio(medio.getPrecioVenta());

        // Si solo hay 1 producto, todos los niveles son el mismo
        if (total == 1) {
            resultado.setProductoMedio(economico);
            resultado.setProductoPremium(economico);
            resultado.setPrecioMedio(economico.getPrecioVenta());
            resultado.setPrecioPremium(economico.getPrecioVenta());
        }
        // Si solo hay 2, medio = premium
        else if (total == 2) {
            resultado.setProductoMedio(premium);
            resultado.setPrecioMedio(premium.getPrecioVenta());
        }
    }

    /**
     * Calcula la confianza del match basándose en similitud de texto.
     */
    private double calcularConfianza(String textoBuscado, Producto producto) {
        if (producto == null || producto.getNombre() == null) return 0.0;

        String textoNorm = parser.normalizarParaBusqueda(textoBuscado);
        String nombreNorm = parser.normalizarParaBusqueda(producto.getNombre());

        // Calcular palabras coincidentes
        Set<String> palabrasBuscadas = new HashSet<>(Arrays.asList(textoNorm.split("\\s+")));
        Set<String> palabrasProducto = new HashSet<>(Arrays.asList(nombreNorm.split("\\s+")));

        // Incluir marca y tags si existen
        if (producto.getMarca() != null) {
            palabrasProducto.addAll(Arrays.asList(
                parser.normalizarParaBusqueda(producto.getMarca()).split("\\s+")
            ));
        }
        if (producto.getTags() != null) {
            palabrasProducto.addAll(Arrays.asList(
                parser.normalizarParaBusqueda(producto.getTags()).split("[,\\s]+")
            ));
        }

        // Contar coincidencias
        long coincidencias = palabrasBuscadas.stream()
            .filter(p -> p.length() >= 3)
            .filter(p -> palabrasProducto.stream().anyMatch(pp -> pp.contains(p) || p.contains(pp)))
            .count();

        // Calcular ratio
        double total = palabrasBuscadas.stream().filter(p -> p.length() >= 3).count();
        if (total == 0) return 0.5;

        return Math.min(1.0, coincidencias / total);
    }

    /**
     * Procesa múltiples items de una lista escolar.
     */
    public List<ResultadoMatch> procesarLista(List<TextoListaParser.ItemParseado> items) {
        List<ResultadoMatch> resultados = new ArrayList<>();

        for (TextoListaParser.ItemParseado item : items) {
            ResultadoMatch match = buscarMatch(item.getTextoNormalizado(), item.getCantidad());
            match.setTextoOriginal(item.getTextoOriginal()); // Mantener texto original
            resultados.add(match);
        }

        // Log resumen
        long conMatch = resultados.stream().filter(ResultadoMatch::isTieneMatch).count();
        log.info("Procesados {} items: {} con match, {} sin match",
            items.size(), conMatch, items.size() - conMatch);

        return resultados;
    }

    /**
     * Busca productos alternativos para un item específico.
     * Útil cuando el usuario quiere ver más opciones.
     */
    public List<Producto> buscarAlternativas(String texto, int limite) {
        return busquedaService.buscar(texto, limite);
    }

    /**
     * Busca un producto específico por ID con verificación de stock.
     */
    public Optional<Producto> obtenerProductoConStock(Long productoId, int cantidadRequerida) {
        // Esto debería llamar al repositorio directamente
        // Por ahora retornamos vacío, se implementará en el servicio principal
        return Optional.empty();
    }
}
