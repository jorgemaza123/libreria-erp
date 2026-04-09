package com.libreria.sistema.service;

import com.libreria.sistema.model.LaminaCategoria;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.LaminaCargaMasivaDTO;
import com.libreria.sistema.model.dto.LaminaFormDTO;
import com.libreria.sistema.repository.LaminaCategoriaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LaminaService {

    public static final String CATEGORIA_PRODUCTO_LAMINAS = "LAMINAS ESCOLARES";
    public static final String CATEGORIA_SIN_DEFINIR = "SIN CATEGORIA";
    public static final BigDecimal PRECIO_VENTA_DEFAULT = new BigDecimal("0.50");

    private final ProductoRepository productoRepository;
    private final ProductoService productoService;
    private final LaminaCategoriaRepository laminaCategoriaRepository;

    @Transactional(readOnly = true)
    public List<Producto> listarActivas() {
        return productoRepository.findLaminasActivasOrdenadas();
    }

    @Transactional(readOnly = true)
    public Optional<Producto> obtenerLamina(Long id) {
        return productoService.obtenerPorId(id).filter(Producto::esLamina);
    }

    @Transactional(readOnly = true)
    public List<LaminaCategoria> listarCategoriasActivas() {
        return laminaCategoriaRepository.findByActivoTrueOrderByOrdenAscNombreAsc();
    }

    @Transactional(readOnly = true)
    public List<LaminaCategoria> listarCategorias() {
        return laminaCategoriaRepository.findAllByOrderByActivoDescOrdenAscNombreAsc();
    }

    @Transactional
    public LaminaCategoria guardarCategoria(String nombre) {
        String normalizada = normalizarTexto(nombre);
        if (normalizada == null) {
            throw new IllegalArgumentException("Escribe un nombre de categoria para la lamina.");
        }

        return laminaCategoriaRepository.findByNombreIgnoreCase(normalizada)
                .map(existente -> {
                    if (!existente.isActivo()) {
                        existente.setActivo(true);
                    }
                    return laminaCategoriaRepository.save(existente);
                })
                .orElseGet(() -> {
                    LaminaCategoria categoria = new LaminaCategoria();
                    categoria.setNombre(normalizada);
                    categoria.setActivo(true);
                    return laminaCategoriaRepository.save(categoria);
                });
    }

    @Transactional
    public void alternarCategoria(Long id) {
        LaminaCategoria categoria = laminaCategoriaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La categoria de lamina no existe."));
        categoria.setActivo(!categoria.isActivo());
        laminaCategoriaRepository.save(categoria);
    }

    @Transactional
    public Producto guardarLamina(LaminaFormDTO dto, boolean rapido) throws Exception {
        validar(dto, rapido);

        boolean esEdicion = dto.getId() != null;
        Optional<Producto> coincidenciaExacta = !esEdicion ? buscarCoincidenciaExacta(dto) : Optional.empty();

        Producto producto = esEdicion
                ? obtenerLamina(dto.getId()).orElseThrow(() -> new IllegalArgumentException("La lamina no existe."))
                : coincidenciaExacta.orElseGet(Producto::new);

        boolean esNuevo = producto.getId() == null;
        boolean sumarStockSobreExistente = !esEdicion && coincidenciaExacta.isPresent();

        producto.setEsLamina(true);
        producto.setActivo(dto.getActivo() == null || dto.getActivo());
        producto.setClasificacion(Producto.CLASIFICACION_MERCADERIA);
        producto.setOrigenCatalogo(Producto.ORIGEN_CATALOGO_GENERAL);
        producto.setUnidadMedida("UNIDAD");
        producto.setTipo("ESTANDAR");
        producto.setTipoAfectacionIgv("GRAVADO");
        producto.setCategoria(CATEGORIA_PRODUCTO_LAMINAS);
        producto.setLaminaCategoria(obtenerCategoriaNormalizada(dto.getLaminaCategoria()));

        if (dto.getCodigoInterno() != null && !dto.getCodigoInterno().isBlank()) {
            producto.setCodigoInterno(dto.getCodigoInterno().trim().toUpperCase());
        } else if (esNuevo) {
            producto.setCodigoInterno(generarCodigoLamina(dto.getLaminaNumero()));
        }

        producto.setCodigoBarra(resolverTexto(dto.getCodigoBarra(), producto.getCodigoBarra()));
        producto.setLaminaNumero(resolverTexto(dto.getLaminaNumero(), producto.getLaminaNumero()));
        producto.setLaminaTitulo(resolverTexto(dto.getLaminaTitulo(), producto.getLaminaTitulo()));
        producto.setLaminaMarca(resolverTexto(dto.getLaminaMarca(), producto.getLaminaMarca()));
        producto.setLaminaProveedorRef(resolverTexto(dto.getLaminaProveedorRef(), producto.getLaminaProveedorRef()));
        producto.setLaminaZona(resolverTexto(dto.getLaminaZona(), producto.getLaminaZona()));
        producto.setLaminaContenedor(resolverTexto(dto.getLaminaContenedor(), producto.getLaminaContenedor()));
        producto.setLaminaPosicion(resolverTexto(dto.getLaminaPosicion(), producto.getLaminaPosicion()));
        producto.setMarca(producto.getLaminaMarca());
        producto.setNombre(construirNombreLamina(producto));

        producto.setPrecioCompra(BigDecimal.ZERO);
        BigDecimal precioVenta = dto.getPrecioVenta() != null && dto.getPrecioVenta().compareTo(BigDecimal.ZERO) > 0
                ? dto.getPrecioVenta()
                : PRECIO_VENTA_DEFAULT;
        producto.setPrecioVenta(precioVenta);
        producto.setPrecioMayorista(precioVenta);
        producto.setStockMinimo(0);

        if (esNuevo) {
            producto.setStockActual(dto.getStockActual() != null ? dto.getStockActual() : 0);
        } else if (sumarStockSobreExistente && dto.getStockActual() != null && dto.getStockActual() > 0) {
            int stockActual = producto.getStockActual() != null ? producto.getStockActual() : 0;
            producto.setStockActual(stockActual + dto.getStockActual());
        } else if (rapido && dto.getStockActual() != null) {
            producto.setStockActual(dto.getStockActual());
        }

        productoService.guardar(producto);
        return producto;
    }

    @Transactional
    public Map<String, Object> guardarCargaMasiva(LaminaCargaMasivaDTO dto) throws Exception {
        String categoria = obtenerCategoriaNormalizada(dto.getLaminaCategoria());
        String marca = normalizarTexto(dto.getLaminaMarca());
        String contenedor = normalizarTexto(dto.getLaminaContenedor());
        int stock = dto.getStockActual() != null && dto.getStockActual() > 0 ? dto.getStockActual() : 1;
        BigDecimal precio = dto.getPrecioVenta() != null && dto.getPrecioVenta().compareTo(BigDecimal.ZERO) > 0
                ? dto.getPrecioVenta()
                : PRECIO_VENTA_DEFAULT;

        if (dto.getLineas() == null || dto.getLineas().isBlank()) {
            throw new IllegalArgumentException("Pega al menos una linea para registrar laminas.");
        }

        List<String> errores = new ArrayList<>();
        List<String> actualizadasDetalle = new ArrayList<>();
        int creadas = 0;
        int actualizadas = 0;

        String[] lineas = dto.getLineas().replace("\r", "").split("\n");
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i] != null ? lineas[i].trim() : "";
            if (linea.isBlank()) {
                continue;
            }

            try {
                LaminaFormDTO item = convertirLinea(linea);
                item.setLaminaCategoria(categoria);
                if (item.getLaminaMarca() == null) {
                    item.setLaminaMarca(marca);
                }
                if (item.getLaminaContenedor() == null) {
                    item.setLaminaContenedor(contenedor);
                }
                item.setStockActual(stock);
                item.setPrecioVenta(precio);
                item.setActivo(true);
                boolean existeCoincidenciaExacta = buscarCoincidenciaExacta(item).isPresent();
                Producto guardada = guardarLamina(item, false);
                if (existeCoincidenciaExacta) {
                    actualizadas++;
                    actualizadasDetalle.add("Linea " + (i + 1) + ": "
                            + guardada.getLaminaEtiquetaTexto()
                            + " en "
                            + guardada.getLaminaUbicacionTexto()
                            + " sumo +" + stock
                            + " y quedo con stock " + (guardada.getStockActual() != null ? guardada.getStockActual() : 0) + ".");
                } else {
                    creadas++;
                }
            } catch (Exception ex) {
                errores.add("Linea " + (i + 1) + ": " + ex.getMessage());
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("creadas", creadas);
        resultado.put("actualizadas", actualizadas);
        resultado.put("actualizadasDetalle", actualizadasDetalle);
        resultado.put("errores", errores);
        resultado.put("categoria", categoria);
        resultado.put("contenedor", contenedor);
        return resultado;
    }

    public LaminaFormDTO convertirADto(Producto producto) {
        LaminaFormDTO dto = new LaminaFormDTO();
        dto.setId(producto.getId());
        dto.setCodigoInterno(producto.getCodigoInterno());
        dto.setCodigoBarra(producto.getCodigoBarra());
        dto.setLaminaNumero(producto.getLaminaNumero());
        dto.setLaminaTitulo(producto.getLaminaTitulo());
        dto.setLaminaMarca(producto.getLaminaMarca());
        dto.setLaminaCategoria(producto.getLaminaCategoria());
        dto.setLaminaProveedorRef(producto.getLaminaProveedorRef());
        dto.setLaminaZona(producto.getLaminaZona());
        dto.setLaminaContenedor(producto.getLaminaContenedor());
        dto.setLaminaPosicion(producto.getLaminaPosicion());
        dto.setPrecioCompra(producto.getPrecioCompra());
        dto.setPrecioVenta(producto.getPrecioVenta());
        dto.setStockActual(producto.getStockActual());
        dto.setStockMinimo(producto.getStockMinimo());
        dto.setActivo(producto.isActivo());
        return dto;
    }

    public String construirNombreLamina(LaminaFormDTO dto) {
        String numero = dto.getLaminaNumero() != null && !dto.getLaminaNumero().isBlank()
                ? ("LAMINA " + dto.getLaminaNumero().trim())
                : "LAMINA";
        String titulo = dto.getLaminaTitulo() != null && !dto.getLaminaTitulo().isBlank()
                ? dto.getLaminaTitulo().trim().toUpperCase()
                : null;
        return titulo != null ? numero + " - " + titulo : numero;
    }

    public String construirNombreLamina(Producto producto) {
        String numero = producto.getLaminaNumero() != null && !producto.getLaminaNumero().isBlank()
                ? ("LAMINA " + producto.getLaminaNumero().trim())
                : "LAMINA";
        String titulo = producto.getLaminaTitulo() != null && !producto.getLaminaTitulo().isBlank()
                ? producto.getLaminaTitulo().trim().toUpperCase()
                : null;
        return titulo != null ? numero + " - " + titulo : numero;
    }

    public String generarCodigoLamina(String numero) {
        String base = (numero != null && !numero.isBlank())
                ? "LAM-" + numero.trim().replaceAll("[^A-Za-z0-9]", "")
                : "LAM-" + System.currentTimeMillis();
        String candidato = base;
        int secuencia = 1;
        while (productoRepository.findByCodigoInterno(candidato).isPresent()) {
            candidato = base + "-" + secuencia;
            secuencia++;
        }
        return candidato;
    }

    public String normalizarTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio.toUpperCase();
    }

    public String obtenerCategoriaNormalizada(String valorCategoria) {
        String categoria = normalizarTexto(valorCategoria);
        if (categoria == null) {
            return CATEGORIA_SIN_DEFINIR;
        }
        guardarCategoria(categoria);
        return categoria;
    }

    private void validar(LaminaFormDTO dto, boolean rapido) {
        if ((dto.getLaminaNumero() == null || dto.getLaminaNumero().isBlank())
                && (dto.getLaminaTitulo() == null || dto.getLaminaTitulo().isBlank())) {
            throw new IllegalArgumentException("Debes indicar al menos el numero o el titulo de la lamina.");
        }
        if (dto.getStockActual() != null && dto.getStockActual() < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        }
        if (rapido && (dto.getStockActual() == null || dto.getStockActual() <= 0)) {
            throw new IllegalArgumentException("El stock inicial debe ser mayor a cero para vender la lamina.");
        }
    }

    private Optional<Producto> buscarCoincidenciaExacta(LaminaFormDTO dto) {
        return productoRepository.findLaminasCoincidenciaExacta(
                normalizarTexto(dto.getLaminaNumero()),
                normalizarTexto(dto.getLaminaTitulo()),
                normalizarTexto(dto.getLaminaContenedor())
        ).stream().findFirst();
    }

    private String resolverTexto(String nuevoValor, String valorActual) {
        String normalizado = normalizarTexto(nuevoValor);
        return normalizado != null ? normalizado : valorActual;
    }

    private LaminaFormDTO convertirLinea(String linea) {
        String[] partes;
        if (linea.contains("|")) {
            partes = linea.split("\\|", 2);
        } else if (linea.contains("\t")) {
            partes = linea.split("\t", 2);
        } else if (linea.contains(";")) {
            partes = linea.split(";", 2);
        } else {
            partes = new String[]{linea};
        }

        LaminaFormDTO dto = new LaminaFormDTO();
        if (partes.length == 1) {
            String valor = partes[0].trim();
            if (valor.matches("^\\d+[A-Za-z0-9-]*$")) {
                dto.setLaminaNumero(valor);
            } else {
                dto.setLaminaTitulo(valor);
            }
        } else {
            String numero = partes[0] != null ? partes[0].trim() : "";
            String titulo = partes[1] != null ? partes[1].trim() : "";
            if (!numero.isBlank()) {
                dto.setLaminaNumero(numero);
            }
            if (!titulo.isBlank()) {
                dto.setLaminaTitulo(titulo);
            }
        }
        return dto;
    }
}
