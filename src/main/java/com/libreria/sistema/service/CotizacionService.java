package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.CorrelativoRepository;
import com.libreria.sistema.repository.CotizacionRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import com.libreria.sistema.repository.VentaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CotizacionService {

    private final CotizacionRepository cotizacionRepository;
    private final ProductoRepository productoRepository;
    private final CorrelativoRepository correlativoRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final CajaService cajaService;

    public CotizacionService(CotizacionRepository cotizacionRepository, 
                             ProductoRepository productoRepository,
                             CorrelativoRepository correlativoRepository, 
                             UsuarioRepository usuarioRepository,
                             VentaRepository ventaRepository,
                             CajaService cajaService) {
        this.cotizacionRepository = cotizacionRepository;
        this.productoRepository = productoRepository;
        this.correlativoRepository = correlativoRepository;
        this.usuarioRepository = usuarioRepository;
        this.ventaRepository = ventaRepository;
        this.cajaService = cajaService;
    }

    public Page<Cotizacion> listar(Pageable pageable) {
        return cotizacionRepository.findAll(pageable);
    }

    public Cotizacion obtenerPorId(Long id) {
        return cotizacionRepository.findById(id).orElse(null);
    }

    @Transactional
    public Cotizacion crearCotizacion(VentaDTO dto) throws Exception {
        Cotizacion c = new Cotizacion();
        
        Correlativo correlativo = correlativoRepository.findByCodigoAndSerie("COTIZACION", "C001")
                .orElseThrow(() -> new Exception("No existe serie C001 configurada"));
        
        c.setSerie("C001");
        c.setNumero(correlativo.getUltimoNumero() + 1);
        correlativo.setUltimoNumero(c.getNumero());
        correlativoRepository.save(correlativo);

        c.setClienteDocumento(dto.getClienteDoc());
        c.setClienteNombre(dto.getClienteNombre());
        c.setClienteTelefono(dto.getClienteTelefono());
        
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        usuarioRepository.findByUsername(username).ifPresent(c::setUsuario);

        BigDecimal total = BigDecimal.ZERO;

        for (VentaDTO.ItemDTO item : dto.getItems()) {
            Producto p = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new Exception("Producto no encontrado"));

            DetalleCotizacion det = new DetalleCotizacion();
            det.setCotizacion(c);
            det.setProducto(p);
            if (item.getNombre() != null && !item.getNombre().isEmpty()) {
                det.setDescripcion(item.getNombre());
            } else {
                det.setDescripcion(p.getNombre());
            }
            det.setCantidad(item.getCantidad());
            det.setPrecioUnitario(item.getPrecio());
            det.setSubtotal(item.getCantidad().multiply(item.getPrecio()));

            c.getItems().add(det);
            total = total.add(det.getSubtotal());
        }

        c.setTotal(total);
        return cotizacionRepository.save(c);
    }

    @Transactional
    public void actualizarCotizacion(Long id, VentaDTO dto) throws Exception {
        Cotizacion c = cotizacionRepository.findById(id)
                .orElseThrow(() -> new Exception("Cotización no encontrada"));

        if (!"EMITIDO".equals(c.getEstado())) {
            throw new Exception("No se puede editar una cotización ya vendida o anulada.");
        }

        c.setClienteDocumento(dto.getClienteDoc());
        c.setClienteNombre(dto.getClienteNombre());
        c.setClienteTelefono(dto.getClienteTelefono());
        c.getItems().clear();

        BigDecimal total = BigDecimal.ZERO;
        
        for (VentaDTO.ItemDTO item : dto.getItems()) {
            Producto p = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new Exception("Producto no encontrado ID: " + item.getProductoId()));

            DetalleCotizacion det = new DetalleCotizacion();
            det.setCotizacion(c);
            det.setProducto(p);
            
            String desc = (item.getProductoId() != null && p.getCodigoInterno().equals("SERV-001")) 
                          ? "SERVICIO MANUAL" 
                          : p.getNombre();
            
            det.setDescripcion(desc);
            det.setCantidad(item.getCantidad());
            det.setPrecioUnitario(item.getPrecio());
            det.setSubtotal(item.getCantidad().multiply(item.getPrecio()));

            c.getItems().add(det);
            total = total.add(det.getSubtotal());
        }
        
        c.setTotal(total);
        cotizacionRepository.save(c);
    }

    @Transactional
    public Long convertirAVenta(Long cotizacionId, String tipoComprobante) throws Exception {
        Cotizacion c = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new Exception("Cotización no encontrada"));

        if (!"EMITIDO".equals(c.getEstado())) {
            throw new Exception("Esta cotización ya fue procesada o vencida.");
        }

        Venta v = new Venta();
        v.setClienteNumeroDocumento(c.getClienteDocumento());
        v.setClienteDenominacion(c.getClienteNombre());
        v.setClienteTipoDocumento(tipoComprobante.equals("FACTURA") ? "6" : "1");
        
        String serie = tipoComprobante.equals("FACTURA") ? "F001" : "B001";
        Correlativo corr = correlativoRepository.findByCodigoAndSerie(tipoComprobante, serie)
                .orElseThrow(() -> new Exception("Serie no configurada"));
        
        v.setTipoComprobante(tipoComprobante);
        v.setSerie(serie);
        v.setNumero(corr.getUltimoNumero() + 1);
        corr.setUltimoNumero(v.getNumero());
        correlativoRepository.save(corr);

        v.setUsuario(c.getUsuario());

        BigDecimal total = BigDecimal.ZERO;

        for (DetalleCotizacion itemCoti : c.getItems()) {
            Producto p = itemCoti.getProducto();

            if (!"SERV-001".equals(p.getCodigoInterno())) {
                if (p.getStockActual() < itemCoti.getCantidad().intValue()) {
                    throw new Exception("Stock insuficiente para convertir: " + p.getNombre());
                }
                p.setStockActual(p.getStockActual() - itemCoti.getCantidad().intValue());
                productoRepository.save(p);
            }

            DetalleVenta dv = new DetalleVenta();
            dv.setVenta(v);
            dv.setProducto(p);
            dv.setDescripcion(itemCoti.getDescripcion()); 
            dv.setCantidad(itemCoti.getCantidad());
            dv.setPrecioUnitario(itemCoti.getPrecioUnitario());
            
            BigDecimal valorUnitario = itemCoti.getPrecioUnitario().divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
            dv.setValorUnitario(valorUnitario);
            dv.setSubtotal(itemCoti.getSubtotal());

            v.getItems().add(dv);
            total = total.add(dv.getSubtotal());
        }

        BigDecimal gravada = total.divide(new BigDecimal("1.18"), 2, java.math.RoundingMode.HALF_UP);
        v.setTotal(total);
        v.setTotalGravada(gravada);
        v.setTotalIgv(total.subtract(gravada));

        ventaRepository.save(v);
        
        c.setEstado("CONVERTIDO_VENTA");
        cotizacionRepository.save(c);

        cajaService.registrarMovimiento(
            "INGRESO", 
            "VENTA (Desde Cotiz.): " + v.getTipoComprobante() + " " + v.getSerie() + "-" + v.getNumero(), 
            v.getTotal()
        );

        return v.getId();
    }

    

}