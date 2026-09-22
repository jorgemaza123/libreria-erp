package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.CosteoProductoDTO;
import com.libreria.sistema.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CosteoEmpresarialService costeoEmpresarialService;

    public ProductoService(ProductoRepository productoRepository,
                           CosteoEmpresarialService costeoEmpresarialService) {
        this.productoRepository = productoRepository;
        this.costeoEmpresarialService = costeoEmpresarialService;
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
            producto.setCodigoInterno(generarCodigoInternoSecuencial());
        }

        // 2. NUEVO: Autogenerar código de barras si está vacío
        // Formato: INT-{timestamp} para productos sin código de barras asignado
        if (producto.getCodigoBarra() == null || producto.getCodigoBarra().trim().isEmpty()) {
            String codigoGenerado = generarCodigoBarrasInternoUnico();
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

        actualizarCosteoEmpresarial(producto);
        productoRepository.save(producto);
    }

    @Transactional
    public int asegurarCodigosParaEtiquetas(List<Long> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) {
            return 0;
        }
        int generados = 0;
        for (Producto producto : productoRepository.findAllById(productoIds)) {
            boolean cambio = false;
            if (producto.getCodigoInterno() == null || producto.getCodigoInterno().trim().isEmpty()) {
                producto.setCodigoInterno(generarCodigoInternoSecuencial());
                cambio = true;
            }
            if (producto.getCodigoBarra() == null || producto.getCodigoBarra().trim().isEmpty()) {
                producto.setCodigoBarra(generarCodigoBarrasInternoUnico());
                cambio = true;
            }
            if (cambio) {
                productoRepository.save(producto);
                generados++;
            }
        }
        return generados;
    }

    private void actualizarCosteoEmpresarial(Producto producto) {
        CosteoProductoDTO analisis = costeoEmpresarialService.actualizarSnapshotProducto(producto);
        producto.setCostoIndirectoEstimado(analisis.getCostoIndirectoUnitario());
        producto.setCostoTotalEstimado(analisis.getCostoTotalUnitario());
        producto.setPrecioMinimoEmpresarial(analisis.getPrecioMinimo());
        producto.setPrecioSugeridoEmpresarial(analisis.getPrecioSugerido());
    }

    private void normalizarProducto(Producto producto) {
        producto.setClasificacion(Producto.normalizarClasificacionInventario(producto.getClasificacion()));

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

        if (producto.esServicioInventario()) {
            producto.setClasificacion(Producto.CLASIFICACION_SERVICIO);
            producto.setTipo("SERVICIO");
            producto.setStockActual(producto.getStockActual() != null ? producto.getStockActual() : 0);
            producto.setStockMinimo(0);
            producto.setStockMaximo(null);
            if (producto.getUnidadMedida() == null || producto.getUnidadMedida().isBlank()) {
                producto.setUnidadMedida("SERVICIO");
            }
            producto.setUbicacionEstante(null);
            producto.setUbicacionFila(null);
            producto.setUbicacionColumna(null);
        } else if (producto.esInactivoInventario()) {
            producto.setClasificacion(Producto.CLASIFICACION_INACTIVO);
            producto.setActivo(false);
            producto.setPosRapido(false);
            producto.setTemporadaActiva(false);
        } else if (producto.esInsumo()) {
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

    private String generarCodigoBarrasInternoUnico() {
        String codigo;
        do {
            codigo = generarCodigoBarrasInterno();
        } while (productoRepository.findByCodigoBarra(codigo).isPresent());
        return codigo;
    }

    private String generarCodigoInternoSecuencial() {
        long candidato = productoRepository.count() + 1;
        String codigo;
        do {
            codigo = "PROD-" + String.format("%04d", candidato++);
        } while (productoRepository.findByCodigoInterno(codigo).isPresent());
        return codigo;
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
