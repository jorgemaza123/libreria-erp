package com.libreria.sistema.service;

import com.libreria.sistema.model.Kardex;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.ProductoRevisionItemDTO;
import com.libreria.sistema.model.dto.ProductoRevisionResultadoDTO;
import com.libreria.sistema.model.dto.ProductoRevisionUpdateDTO;
import com.libreria.sistema.repository.KardexRepository;
import com.libreria.sistema.repository.ProductoRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class ProductoRevisionService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ProductoRepository productoRepository;
    private final KardexRepository kardexRepository;

    public ProductoRevisionService(ProductoRepository productoRepository,
                                   KardexRepository kardexRepository) {
        this.productoRepository = productoRepository;
        this.kardexRepository = kardexRepository;
    }

    public ProductoRevisionResultadoDTO buscar(String termino,
                                               Long id,
                                               String categoria,
                                               String estado,
                                               String tipo,
                                               String clasificacion,
                                               BigDecimal precioVentaDesde,
                                               BigDecimal precioVentaHasta,
                                               BigDecimal precioCompraDesde,
                                               BigDecimal precioCompraHasta,
                                               Integer stockDesde,
                                               Integer stockHasta,
                                               int page,
                                               int size,
                                               String sort,
                                               String dir) {
        int safeSize = normalizarPageSize(size);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, construirSort(sort, dir));
        Specification<Producto> filtro = construirFiltro(termino, id, categoria, estado, tipo, clasificacion,
                precioVentaDesde, precioVentaHasta, precioCompraDesde, precioCompraHasta, stockDesde, stockHasta);
        Page<Producto> resultado = productoRepository.findAll(filtro, pageable);

        return ProductoRevisionResultadoDTO.builder()
                .productos(resultado.getContent().stream().map(this::toDto).toList())
                .page(resultado.getNumber())
                .size(resultado.getSize())
                .totalPages(resultado.getTotalPages())
                .total(productoRepository.count(baseCatalogoSpec()))
                .filtered(resultado.getTotalElements())
                .first(resultado.isFirst())
                .last(resultado.isLast())
                .resumen(construirResumen(resultado.getTotalElements()))
                .build();
    }

    public List<String> obtenerCategorias() {
        return productoRepository.findDistinctCategoriasCatalogo();
    }

    public List<String> obtenerTipos() {
        return productoRepository.findDistinctTiposCatalogo();
    }

    @Transactional
    public ProductoRevisionItemDTO actualizar(Long productoId,
                                              ProductoRevisionUpdateDTO request,
                                              String usuario) {
        if (request == null) {
            throw new IllegalArgumentException("No se recibieron datos para actualizar.");
        }

        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado."));

        String nombre = normalizarMayusculaObligatorio(request.getNombre(), "El nombre es obligatorio.");
        String codigoInterno = normalizarNullable(request.getCodigoInterno(), true);
        String codigoBarra = normalizarNullable(request.getCodigoBarra(), true);
        validarCodigoInternoUnico(producto, codigoInterno);
        validarCodigoBarraUnico(producto, codigoBarra);

        String clasificacionNormalizada = normalizarClasificacion(request.getClasificacion());
        boolean esServicio = Producto.CLASIFICACION_SERVICIO.equals(clasificacionNormalizada);
        boolean esInactivo = Producto.CLASIFICACION_INACTIVO.equals(clasificacionNormalizada);

        int stockAnterior = normalizarEntero(producto.getStockActual(), 0);
        int stockNuevo = esServicio ? 0 : normalizarEntero(request.getStockActual(), 0);
        if (stockNuevo < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        }

        producto.setCodigoInterno(codigoInterno);
        producto.setCodigoBarra(codigoBarra);
        producto.setNombre(nombre);
        producto.setCategoria(normalizarNullable(request.getCategoria(), true));
        producto.setClasificacion(clasificacionNormalizada);
        producto.setTipo(esServicio ? "SERVICIO" : normalizarTipo(request.getTipo()));
        producto.setTags(normalizarNullable(request.getTags(), false));
        producto.setPrecioCompra(normalizarMonto(request.getPrecioCompra(), "El costo no puede ser negativo."));
        producto.setPrecioVenta(normalizarMonto(request.getPrecioVenta(), "El precio de venta no puede ser negativo."));
        producto.setStockActual(stockNuevo);
        producto.setStockMinimo(esServicio ? 0 : Math.max(normalizarEntero(request.getStockMinimo(), 0), 0));
        if (request.getActivo() != null) {
            producto.setActivo(request.getActivo());
        }
        if (request.getPosRapido() != null) {
            producto.setPosRapido(request.getPosRapido());
        }
        if (request.getTemporadaActiva() != null) {
            producto.setTemporadaActiva(request.getTemporadaActiva());
        }
        if (esServicio) {
            producto.setTemporadaActiva(false);
        }
        if (esInactivo) {
            producto.setActivo(false);
            producto.setPosRapido(false);
            producto.setTemporadaActiva(false);
        }

        productoRepository.save(producto);
        if (!esServicio) {
            registrarAjusteStockSiCambio(producto, stockAnterior, stockNuevo, usuario);
        }
        log.info("Usuario {} actualizo producto {} desde revision rapida", usuario, producto.getId());
        return toDto(producto);
    }

    private Specification<Producto> construirFiltro(String termino,
                                                    Long id,
                                                    String categoria,
                                                    String estado,
                                                    String tipo,
                                                    String clasificacion,
                                                    BigDecimal precioVentaDesde,
                                                    BigDecimal precioVentaHasta,
                                                    BigDecimal precioCompraDesde,
                                                    BigDecimal precioCompraHasta,
                                                    Integer stockDesde,
                                                    Integer stockHasta) {
        return baseCatalogoSpec()
                .and(filtroId(id))
                .and(filtroTexto(termino))
                .and(filtroCategoria(categoria))
                .and(filtroTextoExacto("tipo", tipo))
                .and(filtroTextoExacto("clasificacion", clasificacion))
                .and(filtroRangoMonto("precioVenta", precioVentaDesde, precioVentaHasta))
                .and(filtroRangoMonto("precioCompra", precioCompraDesde, precioCompraHasta))
                .and(filtroRangoEntero("stockActual", stockDesde, stockHasta))
                .and(filtroEstado(estado));
    }

    private Specification<Producto> baseCatalogoSpec() {
        return (root, query, cb) -> cb.or(
                root.get("origenCatalogo").isNull(),
                cb.notEqual(cb.upper(root.get("origenCatalogo")), Producto.ORIGEN_CATALOGO_PERSONALIZADO));
    }

    private Specification<Producto> filtroId(Long id) {
        return (root, query, cb) -> id == null ? cb.conjunction() : cb.equal(root.get("id"), id);
    }

    private Specification<Producto> filtroTexto(String termino) {
        return (root, query, cb) -> {
            String valor = limpiar(termino);
            if (valor == null) {
                return cb.conjunction();
            }

            String like = "%" + valor.toLowerCase(Locale.ROOT) + "%";
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.like(cb.lower(root.get("nombre")), like));
            predicados.add(cb.like(cb.lower(root.get("codigoInterno")), like));
            predicados.add(cb.like(cb.lower(root.get("codigoBarra")), like));
            predicados.add(cb.like(cb.lower(root.get("categoria")), like));
            predicados.add(cb.like(cb.lower(root.get("marca")), like));
            predicados.add(cb.like(cb.lower(root.get("modelo")), like));
            predicados.add(cb.like(cb.lower(root.get("color")), like));
            predicados.add(cb.like(cb.lower(root.get("tags")), like));

            Long idBuscado = parseLong(valor);
            if (idBuscado != null) {
                predicados.add(cb.equal(root.get("id"), idBuscado));
            }
            return cb.or(predicados.toArray(Predicate[]::new));
        };
    }

    private Specification<Producto> filtroCategoria(String categoria) {
        return (root, query, cb) -> {
            String valor = limpiar(categoria);
            if (valor == null) {
                return cb.conjunction();
            }
            return cb.equal(cb.upper(root.get("categoria")), valor.toUpperCase(Locale.ROOT));
        };
    }

    private Specification<Producto> filtroTextoExacto(String campo, String filtro) {
        return (root, query, cb) -> {
            String valor = limpiar(filtro);
            if (valor == null) {
                return cb.conjunction();
            }
            return cb.equal(cb.upper(root.get(campo)), valor.toUpperCase(Locale.ROOT));
        };
    }

    private Specification<Producto> filtroRangoMonto(String campo, BigDecimal desde, BigDecimal hasta) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            Path<BigDecimal> path = root.get(campo);
            if (desde != null) {
                predicados.add(cb.greaterThanOrEqualTo(path, desde));
            }
            if (hasta != null) {
                predicados.add(cb.lessThanOrEqualTo(path, hasta));
            }
            return predicados.isEmpty() ? cb.conjunction() : cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    private Specification<Producto> filtroRangoEntero(String campo, Integer desde, Integer hasta) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            Path<Integer> path = root.get(campo);
            if (desde != null) {
                predicados.add(cb.greaterThanOrEqualTo(path, desde));
            }
            if (hasta != null) {
                predicados.add(cb.lessThanOrEqualTo(path, hasta));
            }
            return predicados.isEmpty() ? cb.conjunction() : cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    private Specification<Producto> filtroEstado(String estado) {
        return (root, query, cb) -> {
            String valor = limpiar(estado);
            if (valor == null || "TODOS".equalsIgnoreCase(valor)) {
                return cb.conjunction();
            }
            return switch (valor.toUpperCase(Locale.ROOT)) {
                case "ACTIVO" -> cb.isTrue(root.get("activo"));
                case "INACTIVO" -> cb.isFalse(root.get("activo"));
                case "SIN_CATEGORIA" -> textoVacio(root, cb, "categoria");
                case "SIN_PRECIO_VENTA" -> montoVacio(root, cb, "precioVenta");
                case "SIN_COSTO" -> montoVacio(root, cb, "precioCompra");
                case "SIN_CODIGO" -> cb.or(textoVacio(root, cb, "codigoInterno"), textoVacio(root, cb, "codigoBarra"));
                case "SIN_STOCK" -> cb.or(root.get("stockActual").isNull(), cb.equal(root.get("stockActual"), 0));
                case "CRITICO" -> cb.and(
                        root.get("stockActual").isNotNull(),
                        root.get("stockMinimo").isNotNull(),
                        cb.greaterThan(root.get("stockActual"), 0),
                        cb.lessThanOrEqualTo(root.get("stockActual"), root.get("stockMinimo")));
                case "TEMPORADA" -> cb.isTrue(root.get("temporadaActiva"));
                case "POS_RAPIDO" -> cb.isTrue(root.get("posRapido"));
                case "INSUMO" -> cb.equal(cb.upper(root.get("clasificacion")), Producto.CLASIFICACION_INSUMO);
                case "SERVICIO" -> cb.or(
                        cb.equal(cb.upper(root.get("tipo")), "SERVICIO"),
                        cb.equal(cb.upper(root.get("clasificacion")), Producto.CLASIFICACION_SERVICIO));
                case "DESCONOCIDO" -> cb.equal(cb.upper(root.get("clasificacion")), Producto.CLASIFICACION_DESCONOCIDO);
                case "LAMINA" -> cb.isTrue(root.get("esLamina"));
                case "POR_REVISAR" -> predicadoPorRevisar(root, cb);
                default -> cb.conjunction();
            };
        };
    }

    private Predicate predicadoPorRevisar(Root<Producto> root, CriteriaBuilder cb) {
        return cb.or(
                textoVacio(root, cb, "nombre"),
                textoVacio(root, cb, "categoria"),
                textoVacio(root, cb, "codigoInterno"),
                montoVacio(root, cb, "precioVenta"),
                costoFaltanteEnProducto(root, cb),
                cb.or(root.get("stockActual").isNull(), cb.lessThan(root.get("stockActual"), 0)),
                cb.and(root.get("stockActual").isNotNull(), root.get("stockMinimo").isNotNull(),
                        cb.greaterThanOrEqualTo(root.get("stockActual"), 0),
                        cb.lessThanOrEqualTo(root.get("stockActual"), root.get("stockMinimo"))),
                cb.isFalse(root.get("activo")));
    }

    private Predicate costoFaltanteEnProducto(Root<Producto> root, CriteriaBuilder cb) {
        Predicate noServicio = cb.or(root.get("tipo").isNull(), cb.notEqual(cb.upper(root.get("tipo")), "SERVICIO"));
        return cb.and(noServicio, montoVacio(root, cb, "precioCompra"));
    }

    private Predicate textoVacio(Root<Producto> root, CriteriaBuilder cb, String campo) {
        Path<String> path = root.get(campo);
        return cb.or(path.isNull(), cb.equal(cb.trim(path), ""));
    }

    private Predicate montoVacio(Root<Producto> root, CriteriaBuilder cb, String campo) {
        Path<BigDecimal> path = root.get(campo);
        return cb.or(path.isNull(), cb.lessThanOrEqualTo(path, BigDecimal.ZERO));
    }

    private Map<String, Object> construirResumen(long filtrados) {
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("totalCatalogo", productoRepository.count(baseCatalogoSpec()));
        resumen.put("filtrados", filtrados);
        resumen.put("porRevisar", productoRepository.count(baseCatalogoSpec().and((root, query, cb) -> predicadoPorRevisar(root, cb))));
        resumen.put("sinCategoria", productoRepository.count(baseCatalogoSpec().and((root, query, cb) -> textoVacio(root, cb, "categoria"))));
        resumen.put("sinPrecioVenta", productoRepository.count(baseCatalogoSpec().and((root, query, cb) -> montoVacio(root, cb, "precioVenta"))));
        resumen.put("sinCosto", productoRepository.count(baseCatalogoSpec().and((root, query, cb) -> costoFaltanteEnProducto(root, cb))));
        resumen.put("inactivos", productoRepository.count(baseCatalogoSpec().and((root, query, cb) -> cb.isFalse(root.get("activo")))));
        return resumen;
    }

    private ProductoRevisionItemDTO toDto(Producto producto) {
        List<String> alertas = calcularAlertas(producto);
        return ProductoRevisionItemDTO.builder()
                .id(producto.getId())
                .codigoInterno(producto.getCodigoInterno())
                .codigoBarra(producto.getCodigoBarra())
                .nombre(producto.getNombre())
                .categoria(producto.getCategoria())
                .clasificacion(producto.getClasificacion())
                .tipo(producto.getTipo())
                .tags(producto.getTags())
                .precioCompra(producto.getPrecioCompra())
                .precioVenta(producto.getPrecioVenta())
                .margenPct(calcularMargen(producto.getPrecioCompra(), producto.getPrecioVenta()))
                .stockActual(normalizarEntero(producto.getStockActual(), 0))
                .stockMinimo(normalizarEntero(producto.getStockMinimo(), 0))
                .activo(producto.isActivo())
                .posRapido(Boolean.TRUE.equals(producto.getPosRapido()))
                .temporadaActiva(Boolean.TRUE.equals(producto.getTemporadaActiva()))
                .esLamina(Boolean.TRUE.equals(producto.getEsLamina()))
                .ubicacionResumen(producto.getUbicacionResumenTexto())
                .estadoStock(calcularEstadoStock(producto))
                .badgeStock(calcularBadgeStock(producto))
                .estadoRevision(alertas.isEmpty() ? "LISTO" : "REVISAR")
                .alertas(alertas)
                .build();
    }

    private List<String> calcularAlertas(Producto producto) {
        List<String> alertas = new ArrayList<>();
        if (estaVacio(producto.getNombre())) alertas.add("SIN_NOMBRE");
        if (estaVacio(producto.getCategoria())) alertas.add("SIN_CATEGORIA");
        if (estaVacio(producto.getCodigoInterno())) alertas.add("SIN_CODIGO_INTERNO");
        if (montoFaltante(producto.getPrecioVenta())) alertas.add("SIN_PRECIO_VENTA");
        if (!"SERVICIO".equalsIgnoreCase(producto.getTipo()) && montoFaltante(producto.getPrecioCompra())) {
            alertas.add("SIN_COSTO");
        }
        int stock = normalizarEntero(producto.getStockActual(), 0);
        int minimo = normalizarEntero(producto.getStockMinimo(), 0);
        if (stock < 0) alertas.add("STOCK_NEGATIVO");
        if (stock >= 0 && stock <= minimo) alertas.add("STOCK_CRITICO");
        if (!producto.isActivo()) alertas.add("INACTIVO");
        return alertas;
    }

    private String calcularEstadoStock(Producto producto) {
        if (!producto.isActivo()) {
            return "INACTIVO";
        }
        int stock = normalizarEntero(producto.getStockActual(), 0);
        int minimo = normalizarEntero(producto.getStockMinimo(), 0);
        if (stock <= 0) {
            return "SIN_STOCK";
        }
        if (stock <= minimo) {
            return "CRITICO";
        }
        if (minimo > 0 && stock <= Math.ceil(minimo * 1.5)) {
            return "BAJO";
        }
        return "OK";
    }

    private String calcularBadgeStock(Producto producto) {
        return switch (calcularEstadoStock(producto)) {
            case "INACTIVO" -> "badge-secondary";
            case "SIN_STOCK" -> "badge-dark";
            case "CRITICO" -> "badge-danger";
            case "BAJO" -> "badge-warning";
            default -> "badge-success";
        };
    }

    private BigDecimal calcularMargen(BigDecimal costo, BigDecimal precioVenta) {
        if (precioVenta == null || precioVenta.compareTo(BigDecimal.ZERO) <= 0 || costo == null) {
            return null;
        }
        return precioVenta.subtract(costo)
                .multiply(BigDecimal.valueOf(100))
                .divide(precioVenta, 2, RoundingMode.HALF_UP);
    }

    private Sort construirSort(String sort, String dir) {
        String campo = switch (limpiar(sort) != null ? sort : "") {
            case "id" -> "id";
            case "codigoInterno" -> "codigoInterno";
            case "codigoBarra" -> "codigoBarra";
            case "categoria" -> "categoria";
            case "precioCompra" -> "precioCompra";
            case "precioVenta" -> "precioVenta";
            case "stockActual" -> "stockActual";
            case "stockMinimo" -> "stockMinimo";
            case "activo" -> "activo";
            case "fechaActualizacion" -> "fechaActualizacion";
            default -> "nombre";
        };
        Sort.Direction direccion = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direccion, campo).nullsLast());
    }

    private int normalizarPageSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private void validarCodigoInternoUnico(Producto producto, String codigoInterno) {
        if (codigoInterno == null) {
            return;
        }
        productoRepository.findByCodigoInterno(codigoInterno)
                .filter(otro -> !Objects.equals(otro.getId(), producto.getId()))
                .ifPresent(otro -> {
                    throw new IllegalArgumentException("El codigo interno ya existe en otro producto.");
                });
    }

    private void validarCodigoBarraUnico(Producto producto, String codigoBarra) {
        if (codigoBarra == null) {
            return;
        }
        productoRepository.findByCodigoBarra(codigoBarra)
                .filter(otro -> !Objects.equals(otro.getId(), producto.getId()))
                .ifPresent(otro -> {
                    throw new IllegalArgumentException("El codigo de barra ya existe en otro producto.");
                });
    }

    private void registrarAjusteStockSiCambio(Producto producto, int stockAnterior, int stockNuevo, String usuario) {
        if (stockAnterior == stockNuevo) {
            return;
        }
        Kardex kardex = new Kardex();
        kardex.setProducto(producto);
        kardex.setTipo("AJUSTE");
        kardex.setMotivo("Revision rapida de productos por " + (usuario != null ? usuario : "sistema"));
        kardex.setCantidad(Math.abs(stockNuevo - stockAnterior));
        kardex.setStockAnterior(stockAnterior);
        kardex.setStockActual(stockNuevo);
        kardexRepository.save(kardex);
    }

    private BigDecimal normalizarMonto(BigDecimal valor, String mensajeNegativo) {
        if (valor == null) {
            return null;
        }
        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(mensajeNegativo);
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private int normalizarEntero(Integer valor, int defecto) {
        return valor != null ? valor : defecto;
    }

    private String normalizarMayusculaObligatorio(String valor, String mensaje) {
        String limpio = limpiar(valor);
        if (limpio == null) {
            throw new IllegalArgumentException(mensaje);
        }
        return limpio.toUpperCase(Locale.ROOT);
    }

    private String normalizarClasificacion(String valor) {
        String limpio = limpiar(valor);
        if (limpio == null) {
            return Producto.CLASIFICACION_MERCADERIA;
        }
        return Producto.normalizarClasificacionInventario(limpio);
    }

    private String normalizarTipo(String valor) {
        String limpio = limpiar(valor);
        return limpio != null ? limpio.toUpperCase(Locale.ROOT) : "ESTANDAR";
    }

    private String normalizarNullable(String valor, boolean upper) {
        String limpio = limpiar(valor);
        if (limpio == null) {
            return null;
        }
        return upper ? limpio.toUpperCase(Locale.ROOT) : limpio;
    }

    private String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private Long parseLong(String valor) {
        try {
            return Long.parseLong(valor);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean estaVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }

    private boolean montoFaltante(BigDecimal valor) {
        return valor == null || valor.compareTo(BigDecimal.ZERO) <= 0;
    }
}
