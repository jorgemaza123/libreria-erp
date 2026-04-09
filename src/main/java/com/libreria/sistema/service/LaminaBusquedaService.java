package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LaminaBusquedaService {

    private final ProductoRepository productoRepository;

    public List<Producto> buscar(String termino, int limite) {
        if (termino == null || termino.isBlank()) {
            return Collections.emptyList();
        }

        String terminoNormalizado = normalizar(termino);
        String[] tokens = Arrays.stream(terminoNormalizado.split("\\s+"))
                .filter(token -> !token.isBlank())
                .limit(6)
                .toArray(String[]::new);

        return productoRepository.findLaminasActivasOrdenadas().stream()
                .map(producto -> new LaminaScore(producto, calcularScore(producto, terminoNormalizado, tokens)))
                .filter(score -> score.score > 0)
                .sorted(Comparator
                        .comparingInt(LaminaScore::prioridadStock)
                        .thenComparing(Comparator.comparingInt(LaminaScore::score).reversed())
                        .thenComparing(score -> normalizar(score.producto().getLaminaTitulo()), Comparator.nullsLast(String::compareTo)))
                .limit(limite)
                .map(LaminaScore::producto)
                .collect(Collectors.toList());
    }

    private int calcularScore(Producto producto, String termino, String[] tokens) {
        String numero = normalizar(producto.getLaminaNumero());
        String titulo = normalizar(producto.getLaminaTitulo());
        String marca = normalizar(producto.getLaminaMarca());
        String categoria = normalizar(producto.getLaminaCategoria());
        String proveedor = normalizar(producto.getLaminaProveedorRef());
        String nombre = normalizar(producto.getNombre());
        String codigoInterno = normalizar(producto.getCodigoInterno());
        String codigoBarra = normalizar(producto.getCodigoBarra());

        String combinado = unir(numero, titulo, marca, categoria, proveedor, nombre, codigoInterno, codigoBarra);
        int score = 0;

        if (numero.equals(termino)) score += 1600;
        else if (!numero.isBlank() && numero.startsWith(termino)) score += 1300;

        if (codigoInterno.equals(termino) || codigoBarra.equals(termino)) score += 1100;
        else if ((!codigoInterno.isBlank() && codigoInterno.contains(termino))
                || (!codigoBarra.isBlank() && codigoBarra.contains(termino))) score += 650;

        if (titulo.equals(termino)) score += 1200;
        else if (!titulo.isBlank() && titulo.startsWith(termino)) score += 1000;
        else if (!titulo.isBlank() && titulo.contains(termino)) score += 850;

        if (!categoria.isBlank() && categoria.contains(termino)) score += 480;
        if (!marca.isBlank() && marca.contains(termino)) score += 500;
        if (!proveedor.isBlank() && proveedor.contains(termino)) score += 450;
        if (!nombre.isBlank() && nombre.contains(termino)) score += 300;

        int tokensCoincidentes = 0;
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            boolean coincide = false;

            if (!numero.isBlank() && numero.contains(token)) {
                score += 260;
                coincide = true;
            }
            if (!titulo.isBlank() && titulo.contains(token)) {
                score += 220;
                coincide = true;
            }
            if (!categoria.isBlank() && categoria.contains(token)) {
                score += 140;
                coincide = true;
            }
            if (!marca.isBlank() && marca.contains(token)) {
                score += 120;
                coincide = true;
            }
            if (!proveedor.isBlank() && proveedor.contains(token)) {
                score += 100;
                coincide = true;
            }
            if (!nombre.isBlank() && nombre.contains(token)) {
                score += 80;
                coincide = true;
            }

            if (coincide) {
                tokensCoincidentes++;
            } else {
                score -= 25;
            }
        }

        if (tokens.length > 1 && tokensCoincidentes == tokens.length) {
            score += 350;
        }

        double similitudTitulo = similitud(termino, titulo);
        if (similitudTitulo >= 0.92d) score += 700;
        else if (similitudTitulo >= 0.82d) score += 420;
        else if (similitudTitulo >= 0.72d) score += 260;

        double similitudMarca = similitud(termino, marca);
        if (similitudMarca >= 0.88d) score += 180;

        if (!combinado.isBlank() && combinado.contains(termino)) {
            score += 140;
        }

        return Math.max(score, 0);
    }

    private String unir(String... valores) {
        return Arrays.stream(valores)
                .filter(valor -> valor != null && !valor.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).trim();
    }

    private double similitud(String base, String comparado) {
        if (base.isBlank() || comparado.isBlank()) {
            return 0d;
        }
        if (comparado.contains(base) || base.contains(comparado)) {
            return 0.95d;
        }

        int distancia = levenshtein(base, comparado);
        int maxLength = Math.max(base.length(), comparado.length());
        if (maxLength == 0) {
            return 1d;
        }
        return 1d - ((double) distancia / maxLength);
    }

    private int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int previous = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int current = costs[j];
                int replace = previous + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                int insert = costs[j] + 1;
                int delete = costs[j - 1] + 1;
                costs[j] = Math.min(Math.min(insert, delete), replace);
                previous = current;
            }
        }
        return costs[b.length()];
    }

    private record LaminaScore(Producto producto, int score) {
        int prioridadStock() {
            Integer stock = producto.getStockActual();
            return stock != null && stock > 0 ? 0 : 1;
        }
    }
}
