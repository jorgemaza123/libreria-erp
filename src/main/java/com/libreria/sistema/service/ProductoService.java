package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    public List<Producto> listarTodos() {
        // FIX: usar query con filtro activo=true para evitar cargar productos inactivos
        // que causaba ERR_INCOMPLETE_CHUNKED_ENCODING por exceso de datos en la respuesta
        return productoRepository.findByActivoTrueOrderByNombreAsc();
    }
    
    public List<Producto> listarActivos() {
        return productoRepository.findByActivoTrue();
    }

    public Optional<Producto> obtenerPorId(Long id) {
        return productoRepository.findById(id);
    }

    @Transactional
    @Auditable(modulo = "PRODUCTOS", accion = "MODIFICAR", descripcion = "Guardar producto")
    public void guardar(Producto producto) throws Exception {
        normalizarProducto(producto);

        // 1. Autogenerar código interno si está vacío
        if (producto.getCodigoInterno() == null || producto.getCodigoInterno().trim().isEmpty()) {
            long cantidad = productoRepository.count();
            producto.setCodigoInterno("PROD-" + String.format("%04d", cantidad + 1));
        }

        // 2. NUEVO: Autogenerar código de barras si está vacío
        // Formato: INT-{timestamp} para productos sin código de barras asignado
        if (producto.getCodigoBarra() == null || producto.getCodigoBarra().trim().isEmpty()) {
            String codigoGenerado = generarCodigoBarrasInterno();
            producto.setCodigoBarra(codigoGenerado);
        }

        // 3. Validar unicidad de código de barras
        Optional<Producto> existenteBarra = productoRepository.findByCodigoBarra(producto.getCodigoBarra());
        if (existenteBarra.isPresent() && !existenteBarra.get().getId().equals(producto.getId())) {
            throw new Exception("El código de barras ya existe en el sistema.");
        }

        // 4. Validar unicidad de código interno
        if (producto.getCodigoInterno() != null && !producto.getCodigoInterno().isEmpty()) {
            Optional<Producto> existenteInterno = productoRepository.findByCodigoInterno(producto.getCodigoInterno());
            if (existenteInterno.isPresent() && !existenteInterno.get().getId().equals(producto.getId())) {
                throw new Exception("El código interno ya existe.");
            }
        }

        productoRepository.save(producto);
    }

    private void normalizarProducto(Producto producto) {
        if (producto.getClasificacion() == null || producto.getClasificacion().isBlank()) {
            producto.setClasificacion(Producto.CLASIFICACION_MERCADERIA);
        } else {
            producto.setClasificacion(producto.getClasificacion().trim().toUpperCase());
        }

        if (producto.getOrigenCatalogo() == null || producto.getOrigenCatalogo().isBlank()) {
            producto.setOrigenCatalogo(Producto.ORIGEN_CATALOGO_GENERAL);
        } else {
            producto.setOrigenCatalogo(producto.getOrigenCatalogo().trim().toUpperCase());
        }

        producto.setEsLamina(Boolean.TRUE.equals(producto.getEsLamina()));

        if (producto.esLamina()) {
            producto.setLaminaNumero(normalizarTexto(producto.getLaminaNumero()));
            producto.setLaminaTitulo(normalizarTexto(producto.getLaminaTitulo()));
            producto.setLaminaMarca(normalizarTexto(producto.getLaminaMarca()));
            producto.setLaminaCategoria(normalizarTexto(producto.getLaminaCategoria()));
            producto.setLaminaProveedorRef(normalizarTexto(producto.getLaminaProveedorRef()));
            producto.setLaminaZona(normalizarTexto(producto.getLaminaZona()));
            producto.setLaminaContenedor(normalizarTexto(producto.getLaminaContenedor()));
            producto.setLaminaPosicion(normalizarTexto(producto.getLaminaPosicion()));

            if ((producto.getMarca() == null || producto.getMarca().isBlank()) && producto.getLaminaMarca() != null) {
                producto.setMarca(producto.getLaminaMarca());
            }
        } else {
            producto.setLaminaNumero(null);
            producto.setLaminaTitulo(null);
            producto.setLaminaMarca(null);
            producto.setLaminaCategoria(null);
            producto.setLaminaProveedorRef(null);
            producto.setLaminaZona(null);
            producto.setLaminaContenedor(null);
            producto.setLaminaPosicion(null);
        }

        if (producto.esInsumo()) {
            if (producto.getPrecioVenta() == null) {
                producto.setPrecioVenta(BigDecimal.ZERO);
            }
            if (producto.getPrecioMayorista() == null) {
                producto.setPrecioMayorista(BigDecimal.ZERO);
            }
            if (producto.getStockMinimo() == null) {
                producto.setStockMinimo(0);
            }
            if (producto.getUnidadMedida() == null || producto.getUnidadMedida().isBlank()) {
                producto.setUnidadMedida("UNIDAD");
            }
            if (producto.getTipoAfectacionIgv() == null || producto.getTipoAfectacionIgv().isBlank()) {
                producto.setTipoAfectacionIgv("GRAVADO");
            }
            if (producto.getTipo() == null || producto.getTipo().isBlank() || "SERVICIO".equalsIgnoreCase(producto.getTipo())) {
                producto.setTipo("ESTANDAR");
            }
            producto.setUbicacionEstante(null);
            producto.setUbicacionFila(null);
            producto.setUbicacionColumna(null);
        }
    }

    private String normalizarTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio.toUpperCase();
    }

    /**
     * Genera un código de barras interno único para productos sin código asignado.
     * Formato: INT-{timestamp}{random} - Compatible con Code128 para impresión de etiquetas.
     *
     * @return Código de barras único generado
     */
    private String generarCodigoBarrasInterno() {
        // Usar timestamp en milisegundos + sufijo aleatorio para garantizar unicidad
        long timestamp = System.currentTimeMillis();
        int random = (int) (Math.random() * 1000);
        return String.format("INT-%d%03d", timestamp % 10000000000L, random);
    }

    @Transactional
    @Auditable(modulo = "PRODUCTOS", accion = "ELIMINAR", descripcion = "Eliminar producto")
    public void eliminar(Long id) {
        // Baja lógica: No borramos el registro, solo lo desactivamos
        productoRepository.findById(id).ifPresent(p -> {
            p.setActivo(false);
            productoRepository.save(p);
        });
    }
}
