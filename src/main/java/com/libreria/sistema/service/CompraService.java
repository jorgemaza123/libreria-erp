package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.CategoriaMovimiento;
import com.libreria.sistema.model.Compra;
import com.libreria.sistema.model.DetalleCompra;
import com.libreria.sistema.model.Kardex;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Proveedor;
import com.libreria.sistema.model.dto.CompraDTO;
import com.libreria.sistema.repository.CompraRepository;
import com.libreria.sistema.repository.KardexRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.ProveedorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class CompraService {

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private KardexRepository kardexRepository;

    @Autowired
    private ProveedorRepository proveedorRepository;

    @Autowired
    private CajaService cajaService;

    public static class ResultadoCompra {
        private final Compra compra;
        private final List<Map<String, Object>> alertasPrecios;

        public ResultadoCompra(Compra compra, List<Map<String, Object>> alertasPrecios) {
            this.compra = compra;
            this.alertasPrecios = alertasPrecios;
        }

        public Compra getCompra() {
            return compra;
        }

        public List<Map<String, Object>> getAlertasPrecios() {
            return alertasPrecios;
        }
    }

    @Transactional
    @Auditable(modulo = "COMPRAS", accion = "CREAR", descripcion = "Registrar nueva compra")
    public ResultadoCompra guardarCompra(CompraDTO dto) {
        Proveedor prov = proveedorRepository.findById(dto.getProveedorId())
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));

        Compra compra = new Compra();
        compra.setProveedor(prov);
        compra.setTipoComprobante(dto.getTipoComprobante());
        compra.setNumeroComprobante(dto.getNumeroComprobante());
        compra.setObservaciones(dto.getObservaciones());

        BigDecimal totalCompra = BigDecimal.ZERO;
        List<Map<String, Object>> alertasPrecios = new ArrayList<>();

        for (CompraDTO.DetalleDTO item : dto.getItems()) {
            int cantidadBase = resolverCantidadBase(item);
            BigDecimal costoBase = resolverCostoBase(item, cantidadBase);
            if (cantidadBase <= 0) {
                throw new RuntimeException("Cantidad invalida o cero para uno de los productos. Verifique los datos.");
            }
            if (costoBase.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Costo invalido o cero para uno de los productos. Verifique los datos.");
            }
        }

        for (CompraDTO.DetalleDTO item : dto.getItems()) {
            Producto prod = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + item.getProductoId()));

            int cantidadBase = resolverCantidadBase(item);
            BigDecimal costoBase = resolverCostoBase(item, cantidadBase);
            BigDecimal subtotal = resolverSubtotal(item, cantidadBase, costoBase);

            DetalleCompra det = new DetalleCompra();
            det.setCompra(compra);
            det.setProducto(prod);
            det.setCantidad(cantidadBase);
            det.setPrecioUnitario(costoBase);
            det.setTipoCatalogo(normalizarTexto(item.getTipoCatalogo()).isBlank() ? "PRODUCTO_GENERAL" : normalizarTexto(item.getTipoCatalogo()).toUpperCase());
            det.setPresentacionNombre(normalizarTexto(item.getPresentacionNombre()).isBlank() ? null : normalizarTexto(item.getPresentacionNombre()).toUpperCase());
            det.setCantidadPresentacion(item.getCantidadPresentacion());
            det.setFactorPresentacion(item.getFactorPresentacion());
            det.setPrecioPorPresentacion(item.getPrecioPorPresentacion());
            det.setSubtotal(subtotal);
            compra.getDetalles().add(det);
            totalCompra = totalCompra.add(subtotal);

            int stockAntes = prod.getStockActual() != null ? prod.getStockActual() : 0;
            int cantidadNueva = cantidadBase;
            BigDecimal costoActual = prod.getPrecioCompra() != null ? prod.getPrecioCompra() : BigDecimal.ZERO;
            BigDecimal costoNuevo = costoBase;
            BigDecimal precioVentaActual = prod.getPrecioVenta();

            Kardex kardex = new Kardex();
            kardex.setProducto(prod);
            kardex.setTipo("INGRESO");
            kardex.setMotivo("COMPRA " + compra.getTipoComprobante() + " " + compra.getNumeroComprobante());
            kardex.setCantidad(cantidadNueva);
            kardex.setStockAnterior(stockAntes);
            kardex.setStockActual(stockAntes + cantidadNueva);
            kardexRepository.save(kardex);

            if (stockAntes + cantidadNueva > 0) {
                BigDecimal valorActual = costoActual.multiply(BigDecimal.valueOf(stockAntes));
                BigDecimal valorNuevo = costoNuevo.multiply(BigDecimal.valueOf(cantidadNueva));
                BigDecimal costoPromedio = valorActual.add(valorNuevo)
                        .divide(BigDecimal.valueOf(stockAntes + cantidadNueva), 2, RoundingMode.HALF_UP);
                prod.setPrecioCompra(costoPromedio);
            } else {
                prod.setPrecioCompra(costoNuevo);
            }

            BigDecimal costoFinal = prod.getPrecioCompra();
            if (!prod.esInsumo() && costoActual.compareTo(BigDecimal.ZERO) > 0 && precioVentaActual != null) {
                BigDecimal cambioPct = costoFinal.subtract(costoActual).abs()
                        .divide(costoActual, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                if (cambioPct.compareTo(new BigDecimal("10")) > 0) {
                    BigDecimal margenActual = precioVentaActual.subtract(costoActual)
                            .divide(costoActual, 4, RoundingMode.HALF_UP);
                    BigDecimal precioSugerido = costoFinal.multiply(BigDecimal.ONE.add(margenActual))
                            .setScale(2, RoundingMode.HALF_UP);
                    Map<String, Object> alerta = new HashMap<>();
                    alerta.put("productoId", prod.getId());
                    alerta.put("nombre", prod.getNombre());
                    alerta.put("costoAnterior", costoActual);
                    alerta.put("costoNuevo", costoFinal);
                    alerta.put("cambioPct", cambioPct.setScale(1, RoundingMode.HALF_UP));
                    alerta.put("precioVentaActual", precioVentaActual);
                    alerta.put("precioVentaSugerido", precioSugerido);
                    alertasPrecios.add(alerta);
                }
            }

            prod.setStockActual(stockAntes + cantidadNueva);
            productoRepository.save(prod);
        }

        compra.setTotal(totalCompra);
        Compra guardada = compraRepository.save(compra);

        cajaService.registrarMovimiento(
                "EGRESO",
                "COMPRA PROV: " + prov.getRazonSocial() + " DOC: " + guardada.getNumeroComprobante(),
                totalCompra,
                CategoriaMovimiento.COMPRA_MERCADERIA);

        log.info("Compra {} registrada exitosamente, total: {}", guardada.getId(), totalCompra);
        return new ResultadoCompra(guardada, alertasPrecios);
    }

    @Transactional
    @Auditable(modulo = "COMPRAS", accion = "ANULAR", descripcion = "Anular compra")
    public void anularCompra(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

        if ("ANULADA".equals(compra.getEstado())) {
            throw new RuntimeException("La compra ya esta anulada");
        }

        compra.setEstado("ANULADA");

        for (DetalleCompra detalle : compra.getDetalles()) {
            Producto producto = detalle.getProducto();
            if (producto == null) {
                continue;
            }

            int cantidadComprada = detalle.getCantidad();
            int stockActual = producto.getStockActual() != null ? producto.getStockActual() : 0;
            if (stockActual < cantidadComprada) {
                throw new RuntimeException("Stock insuficiente para reversar la compra del producto: " +
                        producto.getNombre() + ". Stock actual: " + stockActual + ", Se requiere: " + cantidadComprada);
            }

            producto.setStockActual(stockActual - cantidadComprada);
            productoRepository.save(producto);

            Kardex kardex = new Kardex();
            kardex.setProducto(producto);
            kardex.setTipo("SALIDA");
            kardex.setMotivo("ANULACION COMPRA " + compra.getNumeroComprobante() + " (ID: " + compra.getId() + ")");
            kardex.setCantidad(cantidadComprada);
            kardex.setStockAnterior(stockActual);
            kardex.setStockActual(producto.getStockActual());
            kardexRepository.save(kardex);
        }

        compraRepository.save(compra);

        try {
            String concepto = "ANULACION COMPRA " + compra.getNumeroComprobante() + " (ID: " + compraId + ")";
            cajaService.registrarMovimiento("INGRESO", concepto, compra.getTotal(), CategoriaMovimiento.OTRO_INGRESO);
        } catch (Exception e) {
            log.warn("No se pudo registrar ingreso de anulacion en caja: {}", e.getMessage());
        }

        log.info("Compra {} anulada exitosamente", compraId);
    }

    @Transactional
    @Auditable(modulo = "COMPRAS", accion = "ACTUALIZAR_PRECIO", descripcion = "Actualizar precios de venta desde alerta de compra")
    public void actualizarPreciosVenta(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            Long productoId = Long.valueOf(item.get("productoId").toString());
            BigDecimal nuevoPrecio = new BigDecimal(item.get("nuevoPrecioVenta").toString());
            if (nuevoPrecio.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("El precio de venta debe ser mayor a cero.");
            }
            Producto p = productoRepository.findById(productoId)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + productoId));
            p.setPrecioVenta(nuevoPrecio);
            productoRepository.save(p);
            log.info("Precio venta actualizado (transaccional): {} -> S/{}", p.getNombre(), nuevoPrecio);
        }
    }

    @Transactional
    public Map<String, Object> crearProductoRapido(Map<String, Object> datos) {
        Producto guardado = guardarProductoRapido(datos, obtenerSiguienteSkuDisponible());
        log.info("Producto rapido creado desde Compras: id={}, nombre={}", guardado.getId(), guardado.getNombre());
        return mapearProductoCompra(guardado);
    }

    public Map<String, Object> crearProductosRapidosLote(List<Map<String, Object>> items) {
        List<Map<String, Object>> creados = new ArrayList<>();
        List<Map<String, Object>> errores = new ArrayList<>();
        int siguienteSku = obtenerSiguienteNumeroSku();

        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            try {
                Map<String, Object> datos = new HashMap<>(item);
                if (normalizarTexto(datos.get("codigoInterno")).isBlank()) {
                    datos.put("codigoInterno", String.format("SKU-%05d", siguienteSku++));
                }

                Map<String, Object> creado = crearProductoRapido(datos);
                copiarSiPresente(item, creado, "referencia");
                copiarSiPresente(item, creado, "cantidadCompra");
                copiarSiPresente(item, creado, "costoCompra");
                copiarSiPresente(item, creado, "totalCompra");
                copiarSiPresente(item, creado, "nombreOriginal");
                creados.add(creado);
            } catch (Exception e) {
                Map<String, Object> error = new HashMap<>();
                error.put("indice", i);
                error.put("nombre", normalizarTexto(item.get("nombre")));
                error.put("error", e.getMessage());
                copiarSiPresente(item, error, "referencia");
                errores.add(error);
            }
        }

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("creados", creados);
        respuesta.put("errores", errores);
        respuesta.put("totalCreados", creados.size());
        return respuesta;
    }

    @Transactional
    public Map<String, Object> crearProveedorRapido(Map<String, Object> datos) {
        String ruc = normalizarTexto(datos.get("ruc")).replaceAll("\\D", "");
        String razonSocial = normalizarTexto(datos.get("razonSocial")).toUpperCase();

        if (ruc.isBlank()) {
            throw new RuntimeException("El RUC es obligatorio");
        }
        if (ruc.length() != 11) {
            throw new RuntimeException("El RUC debe tener 11 digitos");
        }
        if (razonSocial.isBlank()) {
            throw new RuntimeException("La razon social es obligatoria");
        }

        Optional<Proveedor> existente = proveedorRepository.findByRuc(ruc);
        if (existente.isPresent()) {
            return mapearProveedorCompra(existente.get(), true);
        }

        Proveedor proveedor = new Proveedor();
        proveedor.setActivo(true);
        proveedor.setRuc(ruc);
        proveedor.setRazonSocial(razonSocial);

        try {
            Proveedor guardado = proveedorRepository.save(proveedor);
            return mapearProveedorCompra(guardado, false);
        } catch (DataIntegrityViolationException e) {
            Proveedor guardado = proveedorRepository.findByRuc(ruc)
                    .orElseThrow(() -> new RuntimeException("No se pudo registrar el proveedor. Verifique si el RUC ya existe."));
            return mapearProveedorCompra(guardado, true);
        }
    }

    private Producto guardarProductoRapido(Map<String, Object> datos, String skuSugerido) {
        String nombre = normalizarTexto(datos.get("nombre")).toUpperCase();
        if (nombre.isBlank()) {
            throw new RuntimeException("El nombre del producto es obligatorio");
        }

        String clasificacion = strOrDefault(datos, "clasificacion", Producto.CLASIFICACION_MERCADERIA).trim().toUpperCase();
        boolean esInsumo = Producto.CLASIFICACION_INSUMO.equals(clasificacion);

        BigDecimal precioVenta = esInsumo
                ? parseDecimalOrDefault(datos, "precioVenta", BigDecimal.ZERO)
                : parsePositiveDecimal(datos, "precioVenta", "El precio de venta debe ser mayor a cero");
        String codigoBarra = normalizarTexto(datos.get("codigoBarra"));
        String codigoInterno = normalizarTexto(datos.get("codigoInterno"));
        if (codigoInterno.isBlank()) {
            codigoInterno = skuSugerido;
        }

        if (!codigoBarra.isBlank() && productoRepository.findByCodigoBarra(codigoBarra).isPresent()) {
            throw new RuntimeException("Ya existe un producto con ese codigo de barras");
        }
        if (!codigoInterno.isBlank() && productoRepository.findByCodigoInterno(codigoInterno).isPresent()) {
            throw new RuntimeException("Ya existe un producto con ese SKU / codigo interno");
        }

        Producto p = new Producto();
        p.setNombre(nombre);
        p.setActivo(true);
        p.setStockActual(0);
        p.setPrecioVenta(precioVenta);
        p.setCodigoBarra(codigoBarra.isBlank() ? null : codigoBarra);
        p.setCodigoInterno(codigoInterno.isBlank() ? null : codigoInterno);
        p.setClasificacion(clasificacion);

        parseBD(datos, "precioCompra").ifPresent(p::setPrecioCompra);
        parseBD(datos, "precioMayorista").ifPresent(p::setPrecioMayorista);
        parseInt(datos, "stockMinimo").ifPresent(p::setStockMinimo);

        setIfPresent(datos, "categoria", value -> p.setCategoria(value.trim().toUpperCase()));
        setIfPresent(datos, "marca", value -> p.setMarca(value.trim().toUpperCase()));
        setIfPresent(datos, "modelo", value -> p.setModelo(value.trim()));
        setIfPresent(datos, "color", value -> p.setColor(value.trim()));
        setIfPresent(datos, "descripcion", value -> p.setDescripcion(value.trim()));
        if (!esInsumo) {
            setIfPresent(datos, "ubicacionEstante", value -> p.setUbicacionEstante(value.trim().toUpperCase()));
            setIfPresent(datos, "ubicacionFila", value -> p.setUbicacionFila(value.trim().toUpperCase()));
            setIfPresent(datos, "ubicacionColumna", value -> p.setUbicacionColumna(value.trim().toUpperCase()));
        }
        p.setTipo(esInsumo ? "ESTANDAR" : strOrDefault(datos, "tipo", "ESTANDAR"));
        p.setUnidadMedida(strOrDefault(datos, "unidadMedida", "UNIDAD"));
        p.setTipoAfectacionIgv(strOrDefault(datos, "tipoAfectacionIgv", "GRAVADO"));

        if (esInsumo) {
            p.setUbicacionEstante(null);
            p.setUbicacionFila(null);
            p.setUbicacionColumna(null);
            if (p.getPrecioMayorista() == null) {
                p.setPrecioMayorista(BigDecimal.ZERO);
            }
            if (p.getStockMinimo() == null) {
                p.setStockMinimo(0);
            }
        }

        try {
            return productoRepository.save(p);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("No se pudo crear el producto. Verifique si el codigo de barras o el SKU ya existen.");
        }
    }

    private Map<String, Object> mapearProductoCompra(Producto guardado) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", guardado.getId());
        result.put("nombre", guardado.getNombre());
        result.put("codigoBarra", guardado.getCodigoBarra() != null ? guardado.getCodigoBarra() : "");
        result.put("codigoInterno", guardado.getCodigoInterno() != null ? guardado.getCodigoInterno() : "");
        result.put("precioCompra", guardado.getPrecioCompra() != null ? guardado.getPrecioCompra() : BigDecimal.ZERO);
        result.put("precioVenta", guardado.getPrecioVenta() != null ? guardado.getPrecioVenta() : BigDecimal.ZERO);
        result.put("clasificacion", guardado.getClasificacion() != null ? guardado.getClasificacion() : Producto.CLASIFICACION_MERCADERIA);
        return result;
    }

    private Map<String, Object> mapearProveedorCompra(Proveedor proveedor, boolean existente) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", proveedor.getId());
        result.put("ruc", proveedor.getRuc());
        result.put("razonSocial", proveedor.getRazonSocial());
        result.put("existente", existente);
        return result;
    }

    private int obtenerSiguienteNumeroSku() {
        return productoRepository.findUltimoSku()
                .map(ultimo -> {
                    try {
                        return Integer.parseInt(ultimo.replace("SKU-", "")) + 1;
                    } catch (NumberFormatException e) {
                        return 1;
                    }
                })
                .orElse(1);
    }

    private String obtenerSiguienteSkuDisponible() {
        return String.format("SKU-%05d", obtenerSiguienteNumeroSku());
    }

    private BigDecimal parsePositiveDecimal(Map<String, Object> datos, String key, String errorMessage) {
        Object value = datos.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new RuntimeException(errorMessage);
        }
        try {
            BigDecimal bd = new BigDecimal(value.toString());
            if (bd.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException(errorMessage);
            }
            return bd;
        } catch (NumberFormatException e) {
            throw new RuntimeException(errorMessage);
        }
    }

    private Optional<BigDecimal> parseBD(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return Optional.empty();
        }
        try {
            BigDecimal bd = new BigDecimal(v.toString());
            return bd.compareTo(BigDecimal.ZERO) > 0 ? Optional.of(bd) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private BigDecimal parseDecimalOrDefault(Map<String, Object> datos, String key, BigDecimal def) {
        Object value = datos.get(key);
        if (value == null || value.toString().isBlank()) {
            return def;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Optional<Integer> parseInt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(v.toString()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void setIfPresent(Map<String, Object> m, String key, java.util.function.Consumer<String> setter) {
        Object v = m.get(key);
        if (v != null && !v.toString().isBlank()) {
            setter.accept(v.toString());
        }
    }

    private String normalizarTexto(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private void copiarSiPresente(Map<String, Object> origen, Map<String, Object> destino, String key) {
        if (origen.containsKey(key)) {
            destino.put(key, origen.get(key));
        }
    }

    private String strOrDefault(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : def;
    }

    private int resolverCantidadBase(CompraDTO.DetalleDTO item) {
        if (item.getCantidadPresentacion() != null
                && item.getFactorPresentacion() != null
                && item.getCantidadPresentacion().compareTo(BigDecimal.ZERO) > 0
                && item.getFactorPresentacion().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cantidadBase = item.getCantidadPresentacion()
                    .multiply(item.getFactorPresentacion())
                    .setScale(0, RoundingMode.HALF_UP);
            return cantidadBase.intValueExact();
        }

        if (item.getCantidad() != null) {
            return item.getCantidad();
        }

        throw new RuntimeException("La línea de compra no tiene una cantidad válida.");
    }

    private BigDecimal resolverCostoBase(CompraDTO.DetalleDTO item, int cantidadBase) {
        if (item.getCantidadPresentacion() != null
                && item.getFactorPresentacion() != null
                && item.getPrecioPorPresentacion() != null
                && item.getCantidadPresentacion().compareTo(BigDecimal.ZERO) > 0
                && item.getFactorPresentacion().compareTo(BigDecimal.ZERO) > 0
                && item.getPrecioPorPresentacion().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalLinea = item.getPrecioPorPresentacion().multiply(item.getCantidadPresentacion());
            return totalLinea.divide(BigDecimal.valueOf(cantidadBase), 4, RoundingMode.HALF_UP);
        }

        if (item.getCosto() != null) {
            return item.getCosto().setScale(4, RoundingMode.HALF_UP);
        }

        throw new RuntimeException("La línea de compra no tiene un costo válido.");
    }

    private BigDecimal resolverSubtotal(CompraDTO.DetalleDTO item, int cantidadBase, BigDecimal costoBase) {
        if (item.getCantidadPresentacion() != null
                && item.getPrecioPorPresentacion() != null
                && item.getCantidadPresentacion().compareTo(BigDecimal.ZERO) > 0
                && item.getPrecioPorPresentacion().compareTo(BigDecimal.ZERO) > 0) {
            return item.getPrecioPorPresentacion()
                    .multiply(item.getCantidadPresentacion())
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return costoBase.multiply(BigDecimal.valueOf(cantidadBase)).setScale(2, RoundingMode.HALF_UP);
    }
}
