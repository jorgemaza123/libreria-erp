package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class CotizacionService {

    private final CotizacionRepository cotizacionRepository;
    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final CajaRepository cajaRepository;
    private final KardexRepository kardexRepository;
    private final UsuarioRepository usuarioRepository;
    private final CorrelativoRepository correlativoRepository;

    public CotizacionService(CotizacionRepository cotizacionRepository,
                             ProductoRepository productoRepository,
                             VentaRepository ventaRepository,
                             CajaRepository cajaRepository,
                             KardexRepository kardexRepository,
                             UsuarioRepository usuarioRepository,
                             CorrelativoRepository correlativoRepository) {
        this.cotizacionRepository = cotizacionRepository;
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.cajaRepository = cajaRepository;
        this.kardexRepository = kardexRepository;
        this.usuarioRepository = usuarioRepository;
        this.correlativoRepository = correlativoRepository;
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
        
        // --- CORRELATIVO PARA COTIZACIÓN ---
        Correlativo correlativo = correlativoRepository.findByCodigoAndSerie("COTIZACION", "C001")
                .orElse(new Correlativo("COTIZACION", "C001", 0));

        Integer nuevoNumero = correlativo.getUltimoNumero() + 1;
        correlativo.setUltimoNumero(nuevoNumero);
        correlativoRepository.save(correlativo);

        c.setSerie("C001");
        c.setNumero(nuevoNumero);
        // -----------------------------------

        c.setClienteDocumento(dto.getClienteDocumento());
        c.setClienteNombre(dto.getClienteNombre());
        c.setClienteTelefono(dto.getClienteTelefono());
        
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        usuarioRepository.findByUsername(username).ifPresent(c::setUsuario);

        BigDecimal total = BigDecimal.ZERO;

        for (VentaDTO.DetalleDTO item : dto.getItems()) {
            Producto p = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new Exception("Producto no encontrado"));

            DetalleCotizacion det = new DetalleCotizacion();
            det.setCotizacion(c);
            det.setProducto(p);
            det.setDescripcion(p.getNombre());
            
            // Asignación directa BigDecimal
            det.setCantidad(item.getCantidad()); 
            det.setPrecioUnitario(item.getPrecioVenta());
            
            det.setSubtotal(det.getCantidad().multiply(det.getPrecioUnitario()));

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
            throw new Exception("No se puede editar una cotización ya procesada.");
        }

        c.setClienteDocumento(dto.getClienteDocumento());
        c.setClienteNombre(dto.getClienteNombre());
        c.setClienteTelefono(dto.getClienteTelefono());
        c.getItems().clear();

        BigDecimal total = BigDecimal.ZERO;
        
        for (VentaDTO.DetalleDTO item : dto.getItems()) {
            Producto p = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new Exception("Producto no encontrado"));

            DetalleCotizacion det = new DetalleCotizacion();
            det.setCotizacion(c);
            det.setProducto(p);
            det.setDescripcion(p.getNombre());
            det.setCantidad(item.getCantidad());
            det.setPrecioUnitario(item.getPrecioVenta());
            det.setSubtotal(item.getCantidad().multiply(item.getPrecioVenta()));

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
            throw new Exception("Esta cotización ya fue procesada.");
        }

        Venta v = new Venta();
        v.setClienteNumeroDocumento(c.getClienteDocumento());
        v.setClienteDenominacion(c.getClienteNombre());
        v.setClienteTipoDocumento(tipoComprobante.equals("FACTURA") ? "6" : "1");
        
        // --- CORRELATIVO PARA VENTA DESDE COTIZACIÓN ---
        String serie = tipoComprobante.equals("FACTURA") ? "F001" : "B001";
        
        Correlativo correlativo = correlativoRepository.findByCodigoAndSerie(tipoComprobante, serie)
                .orElse(new Correlativo(tipoComprobante, serie, 0));

        Integer nuevoNumero = correlativo.getUltimoNumero() + 1;
        correlativo.setUltimoNumero(nuevoNumero);
        correlativoRepository.save(correlativo);

        v.setTipoComprobante(tipoComprobante);
        v.setSerie(serie);
        v.setNumero(nuevoNumero);
        // -----------------------------------------------

        v.setFechaEmision(LocalDate.now());
        v.setFechaVencimiento(LocalDate.now());
        v.setMoneda("PEN");
        v.setTipoOperacion("0101");
        v.setEstado("EMITIDO");
        v.setUsuario(c.getUsuario());

        BigDecimal totalVenta = BigDecimal.ZERO;
        BigDecimal totalGravada = BigDecimal.ZERO;
        BigDecimal totalIgv = BigDecimal.ZERO;

        for (DetalleCotizacion itemCoti : c.getItems()) {
            Producto p = itemCoti.getProducto();

            // Validar stock
            if (p.getStockActual() < itemCoti.getCantidad().intValue()) {
                throw new Exception("Stock insuficiente: " + p.getNombre());
            }
            
            // Bajar Stock
            p.setStockActual(p.getStockActual() - itemCoti.getCantidad().intValue());
            productoRepository.save(p);

            // Registrar Kardex
            Kardex k = new Kardex();
            k.setProducto(p);
            k.setTipo("SALIDA");
            k.setMotivo("VENTA COTIZ. " + v.getSerie() + "-" + v.getNumero());
            k.setCantidad(itemCoti.getCantidad().intValue());
            k.setStockAnterior(p.getStockActual() + itemCoti.getCantidad().intValue());
            k.setStockActual(p.getStockActual());
            kardexRepository.save(k);

            // Crear Detalle Venta
            DetalleVenta dv = new DetalleVenta();
            dv.setVenta(v);
            dv.setProducto(p);
            dv.setDescripcion(itemCoti.getDescripcion()); 
            dv.setCantidad(itemCoti.getCantidad()); 
            dv.setUnidadMedida("NIU");

            BigDecimal precioFinal = itemCoti.getPrecioUnitario();
            BigDecimal cantidad = itemCoti.getCantidad();
            BigDecimal subtotal = precioFinal.multiply(cantidad);

            BigDecimal valorUnitario = precioFinal.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
            BigDecimal valorVentaItem = valorUnitario.multiply(cantidad);
            BigDecimal igvItem = subtotal.subtract(valorVentaItem);

            dv.setPrecioUnitario(precioFinal);
            dv.setValorUnitario(valorUnitario);
            dv.setSubtotal(subtotal);
            dv.setPorcentajeIgv(new BigDecimal("18.00"));
            dv.setCodigoTipoAfectacionIgv("10");

            v.getItems().add(dv);
            
            totalVenta = totalVenta.add(subtotal);
            totalGravada = totalGravada.add(valorVentaItem);
            totalIgv = totalIgv.add(igvItem);
        }

        v.setTotal(totalVenta);
        v.setTotalGravada(totalGravada);
        v.setTotalIgv(totalIgv);

        ventaRepository.save(v);
        
        c.setEstado("CONVERTIDO_VENTA");
        cotizacionRepository.save(c);

        MovimientoCaja caja = new MovimientoCaja();
        caja.setFecha(LocalDateTime.now());
        caja.setTipo("INGRESO");
        caja.setConcepto("VENTA COTIZ. " + v.getSerie() + "-" + v.getNumero());
        caja.setMonto(totalVenta);
        cajaRepository.save(caja);

        return v.getId();
    }
}