package com.libreria.sistema.service;

import com.libreria.sistema.model.ServicioCategoria;
import com.libreria.sistema.model.dto.ServicioCategoriaDTO;
import com.libreria.sistema.repository.ServicioCategoriaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class ServicioCategoriaService {

    private static final String ICONO_DEFAULT = "fas fa-briefcase";

    private final ServicioCategoriaRepository servicioCategoriaRepository;

    public ServicioCategoriaService(ServicioCategoriaRepository servicioCategoriaRepository) {
        this.servicioCategoriaRepository = servicioCategoriaRepository;
    }

    public List<ServicioCategoria> listarActivas() {
        return servicioCategoriaRepository.findByActivaTrueOrderByOrdenAsc();
    }

    public List<ServicioCategoria> listarTodasOrdenadas() {
        return servicioCategoriaRepository.findAll(Sort.by(
                Sort.Order.asc("orden"),
                Sort.Order.asc("nombre")
        ));
    }

    @Transactional
    public ServicioCategoria guardarTipo(ServicioCategoriaDTO dto) {
        if (dto == null) {
            throw new RuntimeException("Datos del tipo de trabajo no recibidos.");
        }

        String nombre = normalizarNombreVisible(dto.getNombre());
        if (nombre.isBlank()) {
            throw new RuntimeException("El nombre del tipo de trabajo es obligatorio.");
        }

        ServicioCategoria categoria = dto.getId() != null
                ? servicioCategoriaRepository.findById(dto.getId())
                .orElseThrow(() -> new RuntimeException("Tipo de trabajo no encontrado."))
                : new ServicioCategoria();

        servicioCategoriaRepository.findByNombreIgnoreCase(nombre)
                .filter(existente -> !Objects.equals(existente.getId(), categoria.getId()))
                .ifPresent(existente -> {
                    throw new RuntimeException("Ya existe un tipo de trabajo con ese nombre.");
                });

        if (categoria.getId() == null) {
            categoria.setCodigo(generarCodigoUnico(nombre, null));
            categoria.setOrden(dto.getOrden() != null ? dto.getOrden() : siguienteOrden());
        } else if (dto.getOrden() != null) {
            categoria.setOrden(dto.getOrden());
        }

        categoria.setNombre(nombre);
        categoria.setDescripcion(texto(dto.getDescripcion()));
        categoria.setIcono(texto(dto.getIcono()).isBlank() ? ICONO_DEFAULT : texto(dto.getIcono()));
        categoria.setActiva(dto.getActiva() == null || dto.getActiva());
        return servicioCategoriaRepository.save(categoria);
    }

    @Transactional
    public ServicioCategoria cambiarActivo(Long id, boolean activa) {
        ServicioCategoria categoria = servicioCategoriaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tipo de trabajo no encontrado."));
        categoria.setActiva(activa);
        return servicioCategoriaRepository.save(categoria);
    }

    @Transactional
    public ServicioCategoria obtenerOCrearTipoParaOrden(String entrada) {
        String visible = normalizarNombreVisible(texto(entrada).isBlank() ? "Otro" : entrada);
        String codigoEntrada = normalizarCodigo(visible);

        return servicioCategoriaRepository.findByCodigo(codigoEntrada)
                .or(() -> servicioCategoriaRepository.findByNombreIgnoreCase(visible))
                .map(categoria -> {
                    if (!Boolean.TRUE.equals(categoria.getActiva())) {
                        categoria.setActiva(true);
                        return servicioCategoriaRepository.save(categoria);
                    }
                    return categoria;
                })
                .orElseGet(() -> {
                    ServicioCategoria categoria = new ServicioCategoria();
                    categoria.setCodigo(generarCodigoUnico(visible, null));
                    categoria.setNombre(visible);
                    categoria.setDescripcion("");
                    categoria.setIcono(ICONO_DEFAULT);
                    categoria.setActiva(true);
                    categoria.setOrden(siguienteOrden());
                    return servicioCategoriaRepository.save(categoria);
                });
    }

    public Map<String, String> construirMapaNombres(Collection<String> tiposUsados) {
        Map<String, String> nombres = new LinkedHashMap<>();
        for (ServicioCategoria categoria : listarTodasOrdenadas()) {
            if (!texto(categoria.getCodigo()).isBlank()) {
                nombres.put(categoria.getCodigo(), normalizarNombreVisible(categoria.getNombre()));
            }
        }
        if (tiposUsados != null) {
            for (String tipo : tiposUsados) {
                String codigo = texto(tipo);
                if (!codigo.isBlank()) {
                    nombres.putIfAbsent(codigo, humanizarCodigo(codigo));
                }
            }
        }
        return nombres;
    }

    private String generarCodigoUnico(String nombre, Long idIgnorado) {
        String base = normalizarCodigo(nombre);
        String candidato = base;
        int sufijo = 2;
        while (existeCodigoEnOtroRegistro(candidato, idIgnorado)) {
            candidato = base + "_" + sufijo++;
        }
        return candidato;
    }

    private boolean existeCodigoEnOtroRegistro(String codigo, Long idIgnorado) {
        return servicioCategoriaRepository.findByCodigo(codigo)
                .filter(categoria -> !Objects.equals(categoria.getId(), idIgnorado))
                .isPresent();
    }

    private int siguienteOrden() {
        return servicioCategoriaRepository.findMaxOrden() + 10;
    }

    private String normalizarNombreVisible(String valor) {
        String limpio = texto(valor).replaceAll("\\s+", " ");
        if (limpio.isBlank()) {
            return "";
        }
        if (limpio.equals(limpio.toUpperCase(Locale.ROOT)) || limpio.equals(limpio.toLowerCase(Locale.ROOT))) {
            return humanizarCodigo(limpio);
        }
        return limpio;
    }

    private String humanizarCodigo(String codigo) {
        String limpio = texto(codigo).replace('_', ' ').replaceAll("\\s+", " ").trim();
        if (limpio.isBlank()) {
            return "";
        }
        String[] partes = limpio.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder resultado = new StringBuilder();
        for (String parte : partes) {
            if (parte.isBlank()) {
                continue;
            }
            if (resultado.length() > 0) {
                resultado.append(' ');
            }
            resultado.append(parte.substring(0, 1).toUpperCase(Locale.ROOT));
            if (parte.length() > 1) {
                resultado.append(parte.substring(1));
            }
        }
        return resultado.toString();
    }

    private String normalizarCodigo(String valor) {
        String sinAcentos = Normalizer.normalize(texto(valor), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String codigo = sinAcentos.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return codigo.isBlank() ? "TIPO" : codigo;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
