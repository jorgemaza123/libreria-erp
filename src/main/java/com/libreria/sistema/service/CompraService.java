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
     * FIX ERROR-2: guardarCompra movido al servicio con @Transactional.
     * Garantiza que stock, kardex, compra y movimiento de caja se confirmen
     * o se reviertan juntos. Si la caja está cerrada, toda la operación falla.
     *
     * FIX ERROR-4: tipo de kardex corregido a "INGRESO" (era "ENTRADA").
     */
    @Transactional
    @Auditable(modulo = "COMPRAS", accion = "CREAR", descripcion = "Registrar nueva compra")
    public Compra guardarCompra(CompraDTO dto) {
        Proveedor prov = proveedorRepository.findById(dto.getProveedorId())
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));

        Compra compra = new Compra();
        compra.setProveedor(prov);
        compra.setTipoComprobante(dto.getTipoComprobante());
        compra.setNumeroComprobante(dto.getNumeroComprobante());
        compra.setObservaciones(dto.getObservaciones());

        BigDecimal totalCompra = BigDecimal.ZERO;

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
                CategoriaMovimiento.COMPRA_MERCADERIA
        );

        log.info("Compra {} registrada exitosamente, total: {}", guardada.getId(), totalCompra);
        return guardada;
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
                    log.error("Stock insuficiente para reversar compra. Producto: {}, Stock actual: {}, Cantidad a reversar: {}",
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
        // Si la caja está cerrada se registra una advertencia pero no se bloquea la anulación,
        // ya que el stock ya fue revertido y la compra marcada como ANULADA.
        try {
            String concepto = "ANULACIÓN COMPRA " + compra.getNumeroComprobante() + " (ID: " + compraId + ")";
            cajaService.registrarMovimiento("INGRESO", concepto, compra.getTotal(), CategoriaMovimiento.OTRO_INGRESO);
        } catch (Exception e) {
            log.warn("No se pudo registrar ingreso de anulación en caja (¿caja cerrada?): {}", e.getMessage());
        }

        log.info("Compra {} anulada exitosamente", compraId);
    }
}
