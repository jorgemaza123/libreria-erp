package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.CompraDTO;
import com.libreria.sistema.repository.CompraRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public CompraService(CompraRepository compraRepository, ProductoRepository productoRepository, UsuarioRepository usuarioRepository) {
        this.compraRepository = compraRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public void registrarCompra(CompraDTO dto) throws Exception {
        Compra compra = new Compra();
        
        // Cabecera
        compra.setTipoComprobante(dto.getTipoComprobante());
        compra.setSerie(dto.getSerie());
        compra.setNumero(dto.getNumero());
        compra.setFechaEmision(dto.getFechaEmision());
        compra.setProveedorRuc(dto.getProveedorRuc());
        compra.setProveedorRazonSocial(dto.getProveedorRazon());

        // Usuario
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        usuarioRepository.findByUsername(username).ifPresent(compra::setUsuario);

        BigDecimal total = BigDecimal.ZERO;

        for (CompraDTO.ItemCompraDTO itemDto : dto.getItems()) {
            Producto producto = productoRepository.findById(itemDto.getProductoId())
                    .orElseThrow(() -> new Exception("Producto no encontrado ID: " + itemDto.getProductoId()));

            DetalleCompra detalle = new DetalleCompra();
            detalle.setCompra(compra);
            detalle.setProducto(producto);
            detalle.setCantidad(itemDto.getCantidad());
            detalle.setCostoUnitario(itemDto.getCostoUnitario());
            detalle.setSubtotal(itemDto.getCantidad().multiply(itemDto.getCostoUnitario()));

            compra.getItems().add(detalle);
            total = total.add(detalle.getSubtotal());

            // --- ACTUALIZACIÓN DE INVENTARIO ---
            // 1. Aumentar Stock
            producto.setStockActual(producto.getStockActual() + itemDto.getCantidad().intValue());
            
            // 2. Actualizar último precio de compra (Referencia para margenes)
            producto.setPrecioCompra(itemDto.getCostoUnitario());
            
            productoRepository.save(producto);
        }

        // Cálculos simples (Asumiendo que ingresan costos netos o totales según política)
        // Para simplificar: Asumimos que el "Costo" ingresado es base imponible si es factura
        // O total si es boleta. Aquí guardaremos totales directos.
        compra.setTotal(total);
        compra.setTotalGravada(total); // Simplificación inicial
        compra.setTotalIgv(BigDecimal.ZERO); // Se ajustará con lógica contable si se requiere

        compraRepository.save(compra);
    }
}
