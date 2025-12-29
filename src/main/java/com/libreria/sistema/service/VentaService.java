package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.CorrelativoRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.VentaRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CorrelativoRepository correlativoRepository;
    private final CajaService cajaService; // <--- AUTOMATIZACIÓN CAJA

    public VentaService(VentaRepository ventaRepository, ProductoRepository productoRepository, 
                        UsuarioRepository usuarioRepository, CorrelativoRepository correlativoRepository,
                        CajaService cajaService) { // <--- Inyectado en Constructor
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.correlativoRepository = correlativoRepository;
        this.cajaService = cajaService;
    }

    public Page<Venta> listarVentas(Pageable pageable) {
        return ventaRepository.findAll(pageable);
    }

    public Venta obtenerPorId(Long id) {
        return ventaRepository.findById(id).orElse(null);
    }

    @Transactional
    public Venta registrarVenta(VentaDTO dto) throws Exception {
        Venta venta = new Venta();
        
        // 1. Datos Cliente
        venta.setClienteNumeroDocumento(dto.getClienteDoc());
        venta.setClienteDenominacion(dto.getClienteNombre());
        venta.setClienteDireccion(dto.getClienteDireccion());
        venta.setClienteTipoDocumento(dto.getClienteTipoDoc());
        
        // 2. Correlativo
        String tipo = dto.getTipoComprobante();
        String serieDefault = tipo.equals("FACTURA") ? "F001" : "B001";
        
        Correlativo correlativo = correlativoRepository.findByCodigoAndSerie(tipo, serieDefault)
                .orElseThrow(() -> new Exception("No existe numeración configurada para " + tipo));
        
        Integer nuevoNumero = correlativo.getUltimoNumero() + 1;
        correlativo.setUltimoNumero(nuevoNumero);
        correlativoRepository.save(correlativo);

        venta.setTipoComprobante(tipo);
        venta.setSerie(serieDefault);
        venta.setNumero(nuevoNumero);

        // 3. Usuario
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        usuarioRepository.findByUsername(username).ifPresent(venta::setUsuario);

        // 4. Procesar Items
        BigDecimal totalVenta = BigDecimal.ZERO;
        
        for (VentaDTO.ItemDTO itemDto : dto.getItems()) {
            Producto producto = productoRepository.findById(itemDto.getProductoId())
                    .orElseThrow(() -> new Exception("Producto no encontrado ID: " + itemDto.getProductoId()));

            if (producto.getStockActual() < itemDto.getCantidad().intValue()) {
                throw new Exception("Stock insuficiente para: " + producto.getNombre());
            }

            producto.setStockActual(producto.getStockActual() - itemDto.getCantidad().intValue());
            productoRepository.save(producto);

            DetalleVenta detalle = new DetalleVenta();
            detalle.setVenta(venta);
            detalle.setProducto(producto);
            detalle.setUnidadMedida("NIU");
            detalle.setDescripcion(producto.getNombre());
            detalle.setCantidad(itemDto.getCantidad());
            
            BigDecimal precioConIgv = itemDto.getPrecio();
            BigDecimal valorUnitario = precioConIgv.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
            
            detalle.setPrecioUnitario(precioConIgv);
            detalle.setValorUnitario(valorUnitario);
            detalle.setSubtotal(precioConIgv.multiply(itemDto.getCantidad()));
            
            venta.getItems().add(detalle);
            totalVenta = totalVenta.add(detalle.getSubtotal());
        }

        BigDecimal totalGravada = totalVenta.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
        BigDecimal totalIgv = totalVenta.subtract(totalGravada);

        venta.setTotal(totalVenta);
        venta.setTotalGravada(totalGravada);
        venta.setTotalIgv(totalIgv);

        Venta ventaGuardada = ventaRepository.save(venta);

        // --- 5. AUTOMATIZACIÓN CAJA (INGRESO AUTOMÁTICO) ---
        cajaService.registrarMovimiento(
            "INGRESO", 
            "VENTA POS: " + venta.getTipoComprobante() + " " + venta.getSerie() + "-" + venta.getNumero(), 
            venta.getTotal()
        );

        return ventaGuardada;
    }

    @Transactional
    public void anularVenta(Long ventaId) throws Exception {
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new Exception("Venta no encontrada"));

        if (venta.getEstado().equals("ANULADO")) {
            throw new Exception("La venta ya está anulada");
        }

        // 1. Devolver Stock
        for (DetalleVenta detalle : venta.getItems()) {
            Producto producto = detalle.getProducto();
            producto.setStockActual(producto.getStockActual() + detalle.getCantidad().intValue());
            productoRepository.save(producto);
        }

        // 2. Cambiar Estado
        venta.setEstado("ANULADO");
        ventaRepository.save(venta);

        // --- 3. AUTOMATIZACIÓN CAJA (EGRESO/REVERSIÓN AUTOMÁTICA) ---
        cajaService.registrarMovimiento(
            "EGRESO", 
            "ANULACIÓN VENTA: " + venta.getSerie() + "-" + venta.getNumero(), 
            venta.getTotal()
        );
    }
}