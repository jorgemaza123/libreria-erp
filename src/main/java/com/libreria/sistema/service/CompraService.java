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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CompraService {

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private KardexRepository kardexRepository;

    // FIX ERROR-2+3: necesitamos ProveedorRepository y CajaService en el servicio
    @Autowired
    private ProveedorRepository proveedorRepository;

    @Autowired
    private CajaService cajaService;

    /**
     * Resultado enriquecido de una compra: incluye la entidad y alertas de precios.
     */
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

    /**
     * FIX ERROR-2: guardarCompra movido al servicio con @Transactional.
     * Garantiza que stock, kardex, compra y movimiento de caja se confirmen
     * o se reviertan juntos. Si la caja está cerrada, toda la operación falla.
     *
     * FIX ERROR-4: tipo de kardex corregido a "INGRESO" (era "ENTRADA").
     */
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

        // ALTO-2 FIX: Validar cantidades y costos antes de procesar (fail-fast)
        for (CompraDTO.DetalleDTO item : dto.getItems()) {
            if (item.getCantidad() == null || item.getCantidad() <= 0) {
                throw new RuntimeException("Cantidad inválida o cero para uno de los productos. Verifique los datos.");
            }
            if (item.getCosto() == null || item.getCosto().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Costo inválido o cero para uno de los productos. Verifique los datos.");
            }
        }

        for (CompraDTO.DetalleDTO item : dto.getItems()) {
            Producto prod = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + item.getProductoId()));

            DetalleCompra det = new DetalleCompra();
            det.setCompra(compra);
            det.setProducto(prod);
            det.setCantidad(item.getCantidad());
            det.setPrecioUnitario(item.getCosto());

            BigDecimal subtotal = item.getCosto().multiply(new BigDecimal(item.getCantidad()));
            det.setSubtotal(subtotal);
            compra.getDetalles().add(det);
            totalCompra = totalCompra.add(subtotal);

            // FIX ERROR-4: tipo corregido de "ENTRADA" a "INGRESO"
            Kardex kardex = new Kardex();
            kardex.setProducto(prod);
            kardex.setTipo("INGRESO");
            kardex.setMotivo("COMPRA " + compra.getTipoComprobante() + " " + compra.getNumeroComprobante());
            kardex.setCantidad(item.getCantidad());
            kardex.setStockAnterior(prod.getStockActual());
            kardex.setStockActual(prod.getStockActual() + item.getCantidad());
            kardexRepository.save(kardex);

            // Costo promedio ponderado
            BigDecimal costoActual = prod.getPrecioCompra() != null ? prod.getPrecioCompra() : BigDecimal.ZERO;
            // Guardar valores anteriores para alerta
            BigDecimal precioVentaActual = prod.getPrecioVenta();
            int stockAntes = prod.getStockActual() != null ? prod.getStockActual() : 0;
            int cantidadNueva = item.getCantidad();
            BigDecimal costoNuevo = item.getCosto();

            if (stockAntes + cantidadNueva > 0) {
                BigDecimal valorActual = costoActual.multiply(BigDecimal.valueOf(stockAntes));
                BigDecimal valorNuevo = costoNuevo.multiply(BigDecimal.valueOf(cantidadNueva));
                BigDecimal costoPromedio = valorActual.add(valorNuevo)
                        .divide(BigDecimal.valueOf(stockAntes + cantidadNueva), 2, java.math.RoundingMode.HALF_UP);
                prod.setPrecioCompra(costoPromedio);
            } else {
                prod.setPrecioCompra(costoNuevo);
            }

            // Detectar cambio significativo de costo (> 10%) para alertar al usuario
            BigDecimal costoFinal = prod.getPrecioCompra(); // ya actualizado
            if (costoActual.compareTo(BigDecimal.ZERO) > 0 && precioVentaActual != null) {
                BigDecimal cambioPct = costoFinal.subtract(costoActual).abs()
                        .divide(costoActual, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                if (cambioPct.compareTo(new BigDecimal("10")) > 0) {
                    // Calcular precio sugerido manteniendo el margen actual
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

        // Movimiento de caja — si la caja está cerrada lanza excepción y revierte todo
        cajaService.registrarMovimiento(
                "EGRESO",
                "COMPRA PROV: " + prov.getRazonSocial() + " DOC: " + guardada.getNumeroComprobante(),
                totalCompra,
                CategoriaMovimiento.COMPRA_MERCADERIA);

        log.info("Compra {} registrada exitosamente, total: {}", guardada.getId(), totalCompra);
        return new ResultadoCompra(guardada, alertasPrecios);
    }

    /**
     * Anular una compra (solo ADMIN)
     * - Cambia el estado a ANULADA
     * - Revierte el stock (decrementa)
     * - Registra kardex con tipo SALIDA
     * FIX ERROR-3: también revierte el egreso en caja.
     */
    @Transactional
    @Auditable(modulo = "COMPRAS", accion = "ANULAR", descripcion = "Anular compra")
    public void anularCompra(Long compraId) {
        // Obtener la compra
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

        // Validar que no esté ya anulada
        if ("ANULADA".equals(compra.getEstado())) {
            throw new RuntimeException("La compra ya está anulada");
        }

        // Cambiar estado
        compra.setEstado("ANULADA");

        // Reversar stock (decrementar lo que se había agregado)
        for (DetalleCompra detalle : compra.getDetalles()) {
            Producto producto = detalle.getProducto();
            if (producto != null) {
                int cantidadComprada = detalle.getCantidad();

                // Verificar que hay suficiente stock para reversar
                if (producto.getStockActual() < cantidadComprada) {
                    log.error(
                            "Stock insuficiente para reversar compra. Producto: {}, Stock actual: {}, Cantidad a reversar: {}",
                            producto.getNombre(), producto.getStockActual(), cantidadComprada);
                    throw new RuntimeException("Stock insuficiente para reversar la compra del producto: " +
                            producto.getNombre() + ". Stock actual: " + producto.getStockActual() +
                            ", Se requiere: " + cantidadComprada);
                }

                // Decrementar stock
                producto.setStockActual(producto.getStockActual() - cantidadComprada);
                productoRepository.save(producto);

                // Registrar en kardex
                Kardex kardex = new Kardex();
                kardex.setProducto(producto);
                kardex.setTipo("SALIDA");
                kardex.setMotivo("ANULACIÓN COMPRA " + compra.getNumeroComprobante() +
                        " (ID: " + compra.getId() + ")");
                kardex.setCantidad(cantidadComprada);
                kardex.setStockAnterior(producto.getStockActual() + cantidadComprada);
                kardex.setStockActual(producto.getStockActual());
                kardexRepository.save(kardex);
            }
        }

        // Guardar compra con estado anulado
        compraRepository.save(compra);

        // FIX ERROR-3: revertir el egreso de caja registrado al crear la compra.
        // Si la caja está cerrada se registra una advertencia pero no se bloquea la
        // anulación,
        // ya que el stock ya fue revertido y la compra marcada como ANULADA.
        try {
            String concepto = "ANULACIÓN COMPRA " + compra.getNumeroComprobante() + " (ID: " + compraId + ")";
            cajaService.registrarMovimiento("INGRESO", concepto, compra.getTotal(), CategoriaMovimiento.OTRO_INGRESO);
        } catch (Exception e) {
            log.warn("No se pudo registrar ingreso de anulación en caja (¿caja cerrada?): {}", e.getMessage());
        }

        log.info("Compra {} anulada exitosamente", compraId);
    }

    /**
     * CRÍTICO-3 FIX: Actualizar precios de venta con @Transactional (atomicidad
     * garantizada).
     * Antes se hacía directamente en el controller sin transacción:
     * si fallaba el producto 3 de 5, los 2 primeros quedaban actualizados y el
     * resto no.
     */
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
            log.info("Precio venta actualizado (transaccional): {} \u2192 S/{}", p.getNombre(), nuevoPrecio);
        }
    }

    /**
     * Crea un producto rápido desde el formulario de compras.
     * stockActual = 0; la propia compra sumará el stock al registrarse.
     */
    @Transactional
    public Map<String, Object> crearProductoRapido(Map<String, Object> datos) {
        String nombre = (String) datos.get("nombre");
        if (nombre == null || nombre.isBlank())
            throw new RuntimeException("El nombre del producto es obligatorio");

        String precioVentaStr = datos.getOrDefault("precioVenta", "0").toString();
        BigDecimal precioVenta = new BigDecimal(precioVentaStr);
        if (precioVenta.compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("El precio de venta debe ser mayor a cero");

        Producto p = new Producto();
        p.setNombre(nombre.toUpperCase().trim());
        p.setActivo(true);
        p.setStockActual(0);
        p.setPrecioVenta(precioVenta);

        parseBD(datos, "precioCompra").ifPresent(p::setPrecioCompra);
        parseBD(datos, "precioMayorista").ifPresent(p::setPrecioMayorista);
        parseInt(datos, "stockMinimo").ifPresent(p::setStockMinimo);

        setIfPresent(datos, "categoria", v -> p.setCategoria(v.trim()));
        setIfPresent(datos, "codigoBarra", v -> p.setCodigoBarra(v.trim()));
        setIfPresent(datos, "codigoInterno", v -> p.setCodigoInterno(v.trim()));
        setIfPresent(datos, "marca", v -> p.setMarca(v.trim()));
        setIfPresent(datos, "modelo", v -> p.setModelo(v.trim()));
        setIfPresent(datos, "color", v -> p.setColor(v.trim()));
        setIfPresent(datos, "descripcion", v -> p.setDescripcion(v.trim()));
        setIfPresent(datos, "ubicacionEstante", v -> p.setUbicacionEstante(v.trim()));
        setIfPresent(datos, "ubicacionFila", v -> p.setUbicacionFila(v.trim()));
        setIfPresent(datos, "ubicacionColumna", v -> p.setUbicacionColumna(v.trim()));
        p.setTipo(strOrDefault(datos, "tipo", "ESTANDAR"));
        p.setUnidadMedida(strOrDefault(datos, "unidadMedida", "UNIDAD"));
        p.setTipoAfectacionIgv(strOrDefault(datos, "tipoAfectacionIgv", "GRAVADO"));

        Producto guardado = productoRepository.save(p);
        log.info("Producto rápido creado desde Compras: id={}, nombre={}", guardado.getId(), guardado.getNombre());

        Map<String, Object> result = new HashMap<>();
        result.put("id", guardado.getId());
        result.put("nombre", guardado.getNombre());
        result.put("codigoBarra", guardado.getCodigoBarra() != null ? guardado.getCodigoBarra() : "");
        result.put("precioCompra", guardado.getPrecioCompra() != null ? guardado.getPrecioCompra() : BigDecimal.ZERO);
        result.put("precioVenta", guardado.getPrecioVenta());
        return result;
    }

    // ── helpers privados ──────────────────────────────────────────────────────

    private java.util.Optional<BigDecimal> parseBD(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null)
            return java.util.Optional.empty();
        try {
            BigDecimal bd = new BigDecimal(v.toString());
            return bd.compareTo(BigDecimal.ZERO) > 0 ? java.util.Optional.of(bd) : java.util.Optional.empty();
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<Integer> parseInt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null)
            return java.util.Optional.empty();
        try {
            return java.util.Optional.of(Integer.parseInt(v.toString()));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private void setIfPresent(Map<String, Object> m, String key, java.util.function.Consumer<String> setter) {
        Object v = m.get(key);
        if (v != null && !v.toString().isBlank())
            setter.accept(v.toString());
    }

    private String strOrDefault(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : def;
    }
}
