package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.Compra;
import com.libreria.sistema.model.DetalleCompra;
import com.libreria.sistema.model.Kardex;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.CompraRepository;
import com.libreria.sistema.repository.KardexRepository;
import com.libreria.sistema.repository.ProductoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class CompraService {

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private KardexRepository kardexRepository;

    /**
     * Anular una compra (solo ADMIN)
     * - Cambia el estado a ANULADA
     * - Revierte el stock (decrementa)
     * - Registra kardex con tipo SALIDA
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

        log.info("Compra {} anulada exitosamente", compraId);
    }
}
