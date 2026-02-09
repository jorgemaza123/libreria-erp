package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.*;
import com.libreria.sistema.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Servicio principal para gestión de Listas Escolares.
 * Orquesta todo el flujo: creación, cotización, venta parcial/total.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListaEscolarService {

    private final ListaEscolarRepository listaRepository;
    private final DetalleListaEscolarRepository detalleRepository;
    private final VentaListaEscolarRepository ventaListaRepository;
    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final KardexRepository kardexRepository;
    private final CorrelativoRepository correlativoRepository;
    private final ClienteRepository clienteRepository;
    private final AmortizacionRepository amortizacionRepository;
    private final UsuarioRepository usuarioRepository;

    private final TextoListaParser parser;
    private final MatcherProductosService matcherService;
    private final CajaService cajaService;
    private final ConfiguracionService configuracionService;
    private final FacturacionElectronicaService facturacionService;

    // Configuración
    private static final String SERIE_LISTA = "LE001";
    private static final int DIAS_VENCIMIENTO = 15;

    // =========================================================
    //  FASE 1: CREACIÓN DE LISTA
    // =========================================================

    /**
     * Crea una nueva lista escolar a partir del texto OCR.
     * Parsea el texto y crea los items sin cotizar.
     */
    @Transactional
    public ListaEscolar crearLista(ListaEscolarDTO dto) {
        log.info("Creando nueva lista escolar para alumno: {}", dto.getNombreAlumno());

        // Obtener siguiente número
        Integer maxNumero = listaRepository.findMaxNumeroBySerie(SERIE_LISTA);
        int nuevoNumero = (maxNumero != null ? maxNumero : 0) + 1;

        // Crear lista
        ListaEscolar lista = new ListaEscolar();
        lista.setSerie(SERIE_LISTA);
        lista.setNumero(nuevoNumero);
        lista.setNombreAlumno(dto.getNombreAlumno());
        lista.setGrado(dto.getGrado());
        lista.setSeccion(dto.getSeccion());
        lista.setColegio(dto.getColegio());
        lista.setAnioEscolar(dto.getAnioEscolar() != null ?
            dto.getAnioEscolar() : LocalDate.now().getYear());
        lista.setContactoNombre(dto.getContactoNombre());
        lista.setContactoTelefono(dto.getContactoTelefono());
        lista.setContactoEmail(dto.getContactoEmail());
        lista.setTextoOriginal(dto.getTextoOriginal());
        lista.setFuenteTexto(dto.getFuenteTexto());
        lista.setEstado("PENDIENTE");
        lista.setFechaVencimiento(LocalDateTime.now().plusDays(DIAS_VENCIMIENTO));

        // Obtener cliente si existe
        if (dto.getClienteId() != null) {
            clienteRepository.findById(dto.getClienteId())
                .ifPresent(lista::setCliente);
        }

        // Usuario actual
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        usuarioRepository.findByUsername(username).ifPresent(lista::setUsuario);

        // Guardar lista primero
        lista = listaRepository.save(lista);

        // Parsear texto y crear detalles
        List<TextoListaParser.ItemParseado> itemsParseados = parser.parsear(dto.getTextoOriginal());

        int orden = 0;
        for (TextoListaParser.ItemParseado item : itemsParseados) {
            DetalleListaEscolar detalle = new DetalleListaEscolar();
            detalle.setListaEscolar(lista);
            detalle.setTextoOriginal(item.getTextoOriginal());
            detalle.setCantidadSolicitada(item.getCantidad());
            detalle.setEstado("NO_COTIZADO");
            detalle.setOrden(++orden);
            lista.getDetalles().add(detalle);
        }

        lista.setItemsTotal(orden);
        lista.setItemsPendientes(orden);
        lista = listaRepository.save(lista);

        log.info("Lista {} creada con {} items", lista.getCodigoCompleto(), orden);
        return lista;
    }

    // =========================================================
    //  FASE 2: GENERACIÓN DE COTIZACIONES
    // =========================================================

    /**
     * Genera cotizaciones automáticas para todos los items de una lista.
     * Busca productos y asigna 3 niveles de precio.
     */
    @Transactional
    public ListaEscolar generarCotizaciones(Long listaId) {
        ListaEscolar lista = listaRepository.findByIdConDetalles(listaId)
            .orElseThrow(() -> new RuntimeException("Lista no encontrada: " + listaId));

        log.info("Generando cotizaciones para lista {}", lista.getCodigoCompleto());

        int cotizados = 0;
        int sinMatch = 0;

        for (DetalleListaEscolar detalle : lista.getDetalles()) {
            // Buscar match para este item
            MatcherProductosService.ResultadoMatch match =
                matcherService.buscarMatch(detalle.getTextoOriginal(), detalle.getCantidadSolicitada());

            if (match.isTieneMatch()) {
                // Asignar producto principal
                detalle.setProducto(match.getProductoPrincipal());
                detalle.setProductoNombreSnapshot(match.getProductoPrincipal().getNombre());
                detalle.setConfianzaMatch(BigDecimal.valueOf(match.getConfianza()));
                detalle.setMatchAutomatico(true);

                // Asignar niveles
                if (match.getProductoEconomico() != null) {
                    detalle.setProductoEconomico(match.getProductoEconomico());
                    detalle.setPrecioEconomico(match.getPrecioEconomico());
                }
                if (match.getProductoMedio() != null) {
                    detalle.setProductoMedio(match.getProductoMedio());
                    detalle.setPrecioMedio(match.getPrecioMedio());
                }
                if (match.getProductoPremium() != null) {
                    detalle.setProductoPremium(match.getProductoPremium());
                    detalle.setPrecioPremium(match.getPrecioPremium());
                }

                // Estado inicial: nivel medio seleccionado
                detalle.setNivelSeleccionado("MEDIO");
                detalle.setPrecioFinal(match.getPrecioMedio());
                detalle.setEstado("COTIZADO");
                cotizados++;
            } else {
                detalle.setEstado("NO_COTIZADO");
                sinMatch++;
            }
        }

        // Calcular totales
        recalcularTotales(lista);

        lista.setEstado("COTIZADA");
        lista = listaRepository.save(lista);

        log.info("Cotización generada: {} items cotizados, {} sin match", cotizados, sinMatch);
        return lista;
    }

    /**
     * Recalcula los totales de la lista por nivel.
     */
    private void recalcularTotales(ListaEscolar lista) {
        BigDecimal totalEconomico = BigDecimal.ZERO;
        BigDecimal totalMedio = BigDecimal.ZERO;
        BigDecimal totalPremium = BigDecimal.ZERO;

        for (DetalleListaEscolar d : lista.getDetalles()) {
            BigDecimal cantidad = BigDecimal.valueOf(d.getCantidadSolicitada());

            if (d.getPrecioEconomico() != null) {
                totalEconomico = totalEconomico.add(d.getPrecioEconomico().multiply(cantidad));
            }
            if (d.getPrecioMedio() != null) {
                totalMedio = totalMedio.add(d.getPrecioMedio().multiply(cantidad));
            }
            if (d.getPrecioPremium() != null) {
                totalPremium = totalPremium.add(d.getPrecioPremium().multiply(cantidad));
            }
        }

        lista.setTotalEconomico(totalEconomico);
        lista.setTotalMedio(totalMedio);
        lista.setTotalPremium(totalPremium);
    }

    // =========================================================
    //  FASE 3: EDICIÓN DE COTIZACIÓN
    // =========================================================

    /**
     * Actualiza un detalle de lista (producto, cantidad, nivel, estado).
     */
    @Transactional
    public DetalleListaEscolar actualizarDetalle(Long detalleId, DetalleListaEscolarDTO dto) {
        final DetalleListaEscolar detalle = detalleRepository.findById(detalleId)
            .orElseThrow(() -> new RuntimeException("Detalle no encontrado: " + detalleId));

        // Validar que no esté vendido
        if ("VENDIDO".equals(detalle.getEstado())) {
            throw new RuntimeException("No se puede modificar un item ya vendido");
        }

        // Actualizar cantidad
        if (dto.getCantidadSolicitada() != null && dto.getCantidadSolicitada() > 0) {
            detalle.setCantidadSolicitada(dto.getCantidadSolicitada());
        }

        // Actualizar producto principal
        if (dto.getProductoId() != null) {
            Producto prod = productoRepository.findById(dto.getProductoId()).orElse(null);
            if (prod != null) {
                detalle.setProducto(prod);
                detalle.setProductoNombreSnapshot(prod.getNombre());
                detalle.setMatchAutomatico(false);
            }
        }

        // Actualizar niveles si se proporcionan
        if (dto.getProductoEconomicoId() != null) {
            Producto prod = productoRepository.findById(dto.getProductoEconomicoId()).orElse(null);
            if (prod != null) {
                detalle.setProductoEconomico(prod);
                detalle.setPrecioEconomico(prod.getPrecioVenta());
            }
        }
        if (dto.getProductoMedioId() != null) {
            Producto prod = productoRepository.findById(dto.getProductoMedioId()).orElse(null);
            if (prod != null) {
                detalle.setProductoMedio(prod);
                detalle.setPrecioMedio(prod.getPrecioVenta());
            }
        }
        if (dto.getProductoPremiumId() != null) {
            Producto prod = productoRepository.findById(dto.getProductoPremiumId()).orElse(null);
            if (prod != null) {
                detalle.setProductoPremium(prod);
                detalle.setPrecioPremium(prod.getPrecioVenta());
            }
        }

        // Actualizar nivel seleccionado
        if (dto.getNivelSeleccionado() != null) {
            detalle.setNivelSeleccionado(dto.getNivelSeleccionado());
            detalle.setPrecioFinal(detalle.getPrecioSegunNivel());
        }

        // Actualizar precio final directo
        if (dto.getPrecioFinal() != null) {
            detalle.setPrecioFinal(dto.getPrecioFinal());
        }

        // Actualizar estado
        if (dto.getEstado() != null && !dto.getEstado().equals("VENDIDO")) {
            detalle.setEstado(dto.getEstado());
        }

        // Reemplazo de producto
        if (dto.getProductoReemplazoId() != null) {
            Producto prod = productoRepository.findById(dto.getProductoReemplazoId()).orElse(null);
            if (prod != null) {
                detalle.setProductoReemplazo(prod);
                detalle.setMotivoReemplazo(dto.getMotivoReemplazo());
                detalle.setPrecioFinal(prod.getPrecioVenta());
                detalle.setEstado("REEMPLAZADO");
            }
        }

        detalle.setObservaciones(dto.getObservaciones());

        DetalleListaEscolar saved = detalleRepository.save(detalle);

        // Recalcular totales de la lista
        recalcularTotales(saved.getListaEscolar());
        listaRepository.save(saved.getListaEscolar());

        return saved;
    }

    /**
     * Selecciona un nivel de precio para todos los items cotizados.
     */
    @Transactional
    public void seleccionarNivelMasivo(Long listaId, String nivel) {
        if (!List.of("ECONOMICO", "MEDIO", "PREMIUM").contains(nivel)) {
            throw new RuntimeException("Nivel inválido: " + nivel);
        }

        detalleRepository.seleccionarNivelMasivo(listaId, nivel);

        // Actualizar precio final de cada item
        List<DetalleListaEscolar> detalles = detalleRepository.findByListaEscolarIdOrderByOrdenAsc(listaId);
        for (DetalleListaEscolar d : detalles) {
            if ("COTIZADO".equals(d.getEstado())) {
                d.setNivelSeleccionado(nivel);
                d.setPrecioFinal(d.getPrecioSegunNivel());
                detalleRepository.save(d);
            }
        }

        log.info("Nivel {} seleccionado para lista {}", nivel, listaId);
    }

    // =========================================================
    //  FASE 4: VENTA PARCIAL/TOTAL
    // =========================================================

    /**
     * Procesa una venta (parcial o total) de items de una lista escolar.
     * Genera la venta, afecta inventario y actualiza estados.
     */
    @Transactional
    public Map<String, Object> procesarVenta(VentaListaEscolarDTO dto) {
        log.info("Procesando venta para lista {}", dto.getListaEscolarId());

        // 1. Validar caja abierta
        SesionCaja sesion = cajaService.obtenerSesionActiva()
            .orElseThrow(() -> new RuntimeException("CAJA CERRADA: Debe abrir caja antes de registrar ventas"));

        // 2. Obtener lista con lock
        ListaEscolar lista = listaRepository.findByIdWithLock(dto.getListaEscolarId())
            .orElseThrow(() -> new RuntimeException("Lista no encontrada"));

        // 3. Validar estado de lista
        if (!lista.permiteVentas()) {
            throw new RuntimeException("La lista no permite más ventas. Estado: " + lista.getEstado());
        }

        // 4. Validar y preparar items
        List<DetalleListaEscolar> itemsAVender = new ArrayList<>();
        BigDecimal totalVenta = BigDecimal.ZERO;

        for (ItemSeleccionadoDTO item : dto.getItemsSeleccionados()) {
            DetalleListaEscolar detalle = detalleRepository.findById(item.getDetalleListaId())
                .orElseThrow(() -> new RuntimeException("Item no encontrado: " + item.getDetalleListaId()));

            // Validar que pertenece a la lista
            if (!detalle.getListaEscolar().getId().equals(lista.getId())) {
                throw new RuntimeException("Item no pertenece a la lista");
            }

            // Validar que no esté vendido
            if (detalle.estaVendido()) {
                throw new RuntimeException("Item ya vendido: " + detalle.getTextoOriginal());
            }

            // Validar que permita venta
            if (!detalle.permiteVenta() && !"NO_COTIZADO".equals(detalle.getEstado())) {
                throw new RuntimeException("Item no permite venta: " + detalle.getTextoOriginal());
            }

            // Validar producto asignado
            Producto producto = productoRepository.findByIdWithLock(item.getProductoId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + item.getProductoId()));

            // Validar stock (si no es servicio)
            if (!"SERVICIO".equalsIgnoreCase(producto.getTipo())) {
                if (producto.getStockActual() == null || producto.getStockActual() < item.getCantidad()) {
                    throw new RuntimeException("Stock insuficiente para: " + producto.getNombre());
                }
            }

            // Actualizar detalle con selección final
            detalle.setNivelSeleccionado(item.getNivelSeleccionado());
            detalle.setPrecioFinal(item.getPrecioUnitario());

            itemsAVender.add(detalle);
            totalVenta = totalVenta.add(item.getSubtotal());
        }

        // 5. Crear venta
        Venta venta = crearVentaDesdeListaEscolar(dto, lista, totalVenta);

        // 6. Procesar cada item
        BigDecimal totalGravada = BigDecimal.ZERO;
        BigDecimal totalIgv = BigDecimal.ZERO;
        BigDecimal igvFactor = configuracionService.getIgvFactor();

        for (int i = 0; i < itemsAVender.size(); i++) {
            DetalleListaEscolar detalleLista = itemsAVender.get(i);
            ItemSeleccionadoDTO itemDTO = dto.getItemsSeleccionados().get(i);

            Producto producto = productoRepository.findByIdWithLock(itemDTO.getProductoId()).get();

            // Crear detalle venta
            DetalleVenta detalleVenta = new DetalleVenta();
            detalleVenta.setVenta(venta);
            detalleVenta.setProducto(producto);
            detalleVenta.setCantidad(BigDecimal.valueOf(itemDTO.getCantidad()));
            detalleVenta.setDescripcion(producto.getNombre());
            detalleVenta.setUnidadMedida(producto.getUnidadMedida() != null ? producto.getUnidadMedida() : "NIU");
            detalleVenta.setPrecioUnitario(itemDTO.getPrecioUnitario());
            detalleVenta.setSubtotal(itemDTO.getSubtotal());

            // Cálculos IGV
            BigDecimal valorUnitario = itemDTO.getPrecioUnitario().divide(igvFactor, 6, RoundingMode.HALF_UP);
            BigDecimal valorVenta = valorUnitario.multiply(BigDecimal.valueOf(itemDTO.getCantidad()));
            BigDecimal igvItem = itemDTO.getSubtotal().subtract(valorVenta);

            detalleVenta.setValorUnitario(valorUnitario);
            detalleVenta.setPorcentajeIgv(configuracionService.getIgvPorcentaje());

            totalGravada = totalGravada.add(valorVenta);
            totalIgv = totalIgv.add(igvItem);

            venta.getItems().add(detalleVenta);

            // Descontar stock y registrar Kardex
            if (!"SERVICIO".equalsIgnoreCase(producto.getTipo())) {
                int stockAnterior = producto.getStockActual();
                int cantidadVendida = itemDTO.getCantidad();
                producto.setStockActual(stockAnterior - cantidadVendida);

                Kardex kardex = new Kardex();
                kardex.setProducto(producto);
                kardex.setTipo("SALIDA");
                kardex.setMotivo("VENTA LISTA ESC. " + lista.getCodigoCompleto());
                kardex.setCantidad(cantidadVendida);
                kardex.setStockAnterior(stockAnterior);
                kardex.setStockActual(producto.getStockActual());
                kardexRepository.save(kardex);

                productoRepository.save(producto);
            }

            // Marcar item como vendido
            detalleLista.marcarVendido(venta, null); // El ID se asigna después de guardar
            detalleRepository.save(detalleLista);
        }

        // 7. Actualizar totales de venta
        venta.setTotal(totalVenta);
        venta.setTotalGravada(totalGravada);
        venta.setTotalIgv(totalIgv);

        // 8. Procesar forma de pago
        procesarFormaPago(venta, dto, totalVenta);

        // 9. Guardar venta
        venta = ventaRepository.save(venta);

        // Actualizar IDs de detalle venta en items lista
        for (int i = 0; i < venta.getItems().size(); i++) {
            DetalleListaEscolar detalleLista = itemsAVender.get(i);
            detalleLista.setDetalleVentaId(venta.getItems().get(i).getId());
            detalleRepository.save(detalleLista);
        }

        // 10. Registrar pago y caja
        BigDecimal montoAbonado = "CREDITO".equals(dto.getFormaPago()) ?
            (dto.getMontoInicial() != null ? dto.getMontoInicial() : BigDecimal.ZERO) : totalVenta;

        if (montoAbonado.compareTo(BigDecimal.ZERO) > 0) {
            registrarPagoYCaja(venta, montoAbonado, dto.getMetodoPago());
        }

        // 11. Registrar relación lista-venta
        VentaListaEscolar ventaLista = new VentaListaEscolar();
        ventaLista.setListaEscolar(lista);
        ventaLista.setVenta(venta);
        ventaLista.setItemsVendidos(itemsAVender.size());
        ventaLista.setMontoVendido(totalVenta);
        ventaLista.setEsVentaParcial(lista.getItemsPendientes() > itemsAVender.size());
        usuarioRepository.findByUsername(
            SecurityContextHolder.getContext().getAuthentication().getName()
        ).ifPresent(ventaLista::setUsuario);
        ventaListaRepository.save(ventaLista);

        // 12. Actualizar lista
        lista.recalcularPendientes();
        lista.setMontoVendido(lista.getMontoVendido().add(totalVenta));
        lista.setFechaUltimaVenta(LocalDateTime.now());

        if (lista.getItemsPendientes() == 0) {
            lista.setEstado("COMPLETADA");
        } else {
            lista.setEstado("EN_PROCESO");
        }
        listaRepository.save(lista);

        log.info("Venta {} procesada: {} items, total S/ {}",
            venta.getSerie() + "-" + venta.getNumero(),
            itemsAVender.size(), totalVenta);

        return Map.of(
            "success", true,
            "ventaId", venta.getId(),
            "serie", venta.getSerie(),
            "numero", venta.getNumero(),
            "total", totalVenta,
            "itemsVendidos", itemsAVender.size(),
            "itemsPendientes", lista.getItemsPendientes(),
            "estadoLista", lista.getEstado()
        );
    }

    /**
     * Crea la cabecera de venta para una lista escolar.
     */
    private Venta crearVentaDesdeListaEscolar(VentaListaEscolarDTO dto, ListaEscolar lista, BigDecimal total) {
        boolean facturaActiva = facturacionService.isFacturacionElectronicaActiva();
        String tipo = dto.getTipoComprobante() != null ? dto.getTipoComprobante() : "BOLETA";
        String serie = facturacionService.obtenerSerie(tipo, facturaActiva);

        // Correlativo con lock
        Correlativo correlativo = correlativoRepository.findByCodigoAndSerieWithLock(tipo, serie)
            .orElseGet(() -> correlativoRepository.save(new Correlativo(tipo, serie, 0)));

        int nuevoNumero = (correlativo.getUltimoNumero() != null ? correlativo.getUltimoNumero() : 0) + 1;
        correlativo.setUltimoNumero(nuevoNumero);
        correlativoRepository.save(correlativo);

        Venta venta = new Venta();
        venta.setTipoComprobante(tipo);
        venta.setSerie(serie);
        venta.setNumero(nuevoNumero);
        venta.setFechaEmision(LocalDate.now());
        venta.setEstado("EMITIDO");
        venta.setMetodoPago(dto.getMetodoPago() != null ? dto.getMetodoPago() : "EFECTIVO");

        // Datos cliente
        venta.setClienteDenominacion(dto.getClienteNombre() != null ?
            dto.getClienteNombre() : lista.getContactoNombre());
        venta.setClienteNumeroDocumento(dto.getClienteDocumento());
        venta.setClienteDireccion(dto.getClienteDireccion());

        if (dto.getClienteDocumento() != null && !dto.getClienteDocumento().isBlank()) {
            venta.setClienteTipoDocumento(dto.getClienteDocumento().length() == 11 ? "6" : "1");
        }

        // Cliente entity si existe
        if (lista.getCliente() != null) {
            venta.setClienteEntity(lista.getCliente());
        }

        // Usuario
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        usuarioRepository.findByUsername(username).ifPresent(venta::setUsuario);

        return venta;
    }

    /**
     * Procesa forma de pago de la venta.
     */
    private void procesarFormaPago(Venta venta, VentaListaEscolarDTO dto, BigDecimal total) {
        if ("CREDITO".equals(dto.getFormaPago())) {
            venta.setFormaPago("CREDITO");
            BigDecimal inicial = dto.getMontoInicial() != null ? dto.getMontoInicial() : BigDecimal.ZERO;
            venta.setMontoPagado(inicial);
            venta.setSaldoPendiente(total.subtract(inicial));
            int dias = dto.getDiasCredito() != null ? dto.getDiasCredito() : 30;
            venta.setFechaVencimiento(LocalDate.now().plusDays(dias));
        } else {
            venta.setFormaPago("CONTADO");
            venta.setMontoPagado(total);
            venta.setSaldoPendiente(BigDecimal.ZERO);
            venta.setFechaVencimiento(LocalDate.now());
        }
    }

    /**
     * Registra pago y movimiento de caja.
     */
    private void registrarPagoYCaja(Venta venta, BigDecimal monto, String metodoPago) {
        Amortizacion amo = new Amortizacion();
        amo.setVenta(venta);
        amo.setMonto(monto);
        amo.setMetodoPago(metodoPago != null ? metodoPago : "EFECTIVO");
        amo.setObservacion("VENTA LISTA ESCOLAR - " + metodoPago);
        amortizacionRepository.save(amo);

        cajaService.registrarMovimiento("INGRESO",
            "VENTA LISTA ESC. " + venta.getSerie() + "-" + venta.getNumero() + " (" + metodoPago + ")",
            monto);
    }

    // =========================================================
    //  CONSULTAS Y LISTADOS
    // =========================================================

    /**
     * Busca listas con filtros y paginación.
     */
    @Transactional(readOnly = true)
    public Page<ListaEscolar> buscar(String estado, String colegio, String grado,
                                      Integer anio, Pageable pageable) {
        return listaRepository.buscarConFiltros(estado, colegio, grado, anio, pageable);
    }

    /**
     * Obtiene una lista con todos sus detalles.
     */
    @Transactional(readOnly = true)
    public Optional<ListaEscolar> obtenerConDetalles(Long id) {
        return listaRepository.findByIdConDetalles(id);
    }

    /**
     * Obtiene los detalles vendibles (pendientes de venta) de una lista.
     */
    @Transactional(readOnly = true)
    public List<DetalleListaEscolar> obtenerVendibles(Long listaId) {
        return detalleRepository.findVendibles(listaId);
    }

    /**
     * Obtiene los detalles ya vendidos de una lista.
     */
    @Transactional(readOnly = true)
    public List<DetalleListaEscolar> obtenerVendidos(Long listaId) {
        return detalleRepository.findVendidos(listaId);
    }

    /**
     * Obtiene el historial de ventas de una lista.
     */
    @Transactional(readOnly = true)
    public List<VentaListaEscolar> obtenerHistorialVentas(Long listaId) {
        return ventaListaRepository.findByListaIdConDatos(listaId);
    }

    /**
     * Marca todos los items no vendidos como pendientes.
     */
    @Transactional
    public void marcarTodoPendiente(Long listaId) {
        detalleRepository.actualizarEstadoMasivo(listaId, "COTIZADO", "PENDIENTE");
        log.info("Items de lista {} marcados como pendientes", listaId);
    }

    /**
     * Cancela una lista (solo si no tiene ventas).
     */
    @Transactional
    public void cancelarLista(Long listaId) {
        ListaEscolar lista = listaRepository.findById(listaId)
            .orElseThrow(() -> new RuntimeException("Lista no encontrada"));

        if (lista.getItemsVendidos() > 0) {
            throw new RuntimeException("No se puede cancelar una lista con ventas realizadas");
        }

        lista.setEstado("CANCELADA");
        listaRepository.save(lista);
        log.info("Lista {} cancelada", lista.getCodigoCompleto());
    }

    /**
     * Estadísticas del módulo.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerEstadisticas(Integer anio) {
        if (anio == null) anio = LocalDate.now().getYear();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalListas", listaRepository.countByAnioEscolar(anio));
        stats.put("pendientes", listaRepository.countByEstado("PENDIENTE"));
        stats.put("enProceso", listaRepository.countByEstado("EN_PROCESO"));
        stats.put("completadas", listaRepository.countByEstado("COMPLETADA"));
        stats.put("porColegio", listaRepository.countByColegioAndAnio(anio));
        stats.put("porGrado", listaRepository.countByGradoAndAnio(anio));

        return stats;
    }

    // =========================================================
    //  EDICIÓN POR NIVELES
    // =========================================================

    /**
     * Actualiza el producto asignado a un nivel específico.
     */
    @Transactional
    public DetalleListaEscolar actualizarProductoPorNivel(Long detalleId, String nivel, Long productoId) {
        DetalleListaEscolar detalle = detalleRepository.findById(detalleId)
            .orElseThrow(() -> new RuntimeException("Detalle no encontrado: " + detalleId));

        if ("VENDIDO".equals(detalle.getEstado())) {
            throw new RuntimeException("No se puede modificar un item ya vendido");
        }

        Producto producto = null;
        BigDecimal precio = BigDecimal.ZERO;

        if (productoId != null) {
            producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + productoId));
            precio = producto.getPrecioVenta();
        }

        switch (nivel.toUpperCase()) {
            case "ECONOMICO" -> {
                detalle.setProductoEconomico(producto);
                detalle.setPrecioEconomico(precio);
            }
            case "MEDIO" -> {
                detalle.setProductoMedio(producto);
                detalle.setPrecioMedio(precio);
            }
            case "PREMIUM" -> {
                detalle.setProductoPremium(producto);
                detalle.setPrecioPremium(precio);
            }
            default -> throw new RuntimeException("Nivel inválido: " + nivel);
        }

        // Si no estaba cotizado, ahora lo está
        if ("NO_COTIZADO".equals(detalle.getEstado()) && producto != null) {
            detalle.setEstado("COTIZADO");
        }

        detalle = detalleRepository.save(detalle);

        // Recalcular totales de la lista
        recalcularTotales(detalle.getListaEscolar());
        listaRepository.save(detalle.getListaEscolar());

        log.info("Producto {} asignado al nivel {} del detalle {}", productoId, nivel, detalleId);
        return detalle;
    }

    /**
     * Agrega un regalo/oferta a una lista.
     */
    @Transactional
    public DetalleListaEscolar agregarRegalo(Long listaId, String texto, String nivel) {
        ListaEscolar lista = listaRepository.findByIdConDetalles(listaId)
            .orElseThrow(() -> new RuntimeException("Lista no encontrada: " + listaId));

        // Determinar el orden
        int maxOrden = lista.getDetalles().stream()
            .mapToInt(DetalleListaEscolar::getOrden)
            .max()
            .orElse(0);

        DetalleListaEscolar regalo = DetalleListaEscolar.crearRegalo(lista, texto, nivel, maxOrden + 1);
        regalo = detalleRepository.save(regalo);

        lista.getDetalles().add(regalo);
        lista.setItemsTotal(lista.getDetalles().size());
        lista.recalcularPendientes();
        listaRepository.save(lista);

        log.info("Regalo '{}' agregado a lista {} para nivel {}", texto, lista.getCodigoCompleto(), nivel);
        return regalo;
    }

    /**
     * Elimina un regalo de una lista.
     */
    @Transactional
    public void eliminarRegalo(Long detalleId) {
        DetalleListaEscolar detalle = detalleRepository.findById(detalleId)
            .orElseThrow(() -> new RuntimeException("Detalle no encontrado: " + detalleId));

        if (!detalle.esItemRegalo()) {
            throw new RuntimeException("Solo se pueden eliminar items de tipo regalo");
        }

        if ("VENDIDO".equals(detalle.getEstado())) {
            throw new RuntimeException("No se puede eliminar un regalo ya vendido");
        }

        ListaEscolar lista = detalle.getListaEscolar();
        lista.getDetalles().remove(detalle);
        detalleRepository.delete(detalle);

        lista.setItemsTotal(lista.getDetalles().size());
        lista.recalcularPendientes();
        listaRepository.save(lista);

        log.info("Regalo {} eliminado de lista {}", detalleId, lista.getCodigoCompleto());
    }

    /**
     * Copia los productos de un nivel a otro.
     */
    @Transactional
    public void copiarNivel(Long listaId, String nivelOrigen, String nivelDestino) {
        if (!List.of("ECONOMICO", "MEDIO", "PREMIUM").contains(nivelOrigen.toUpperCase()) ||
            !List.of("ECONOMICO", "MEDIO", "PREMIUM").contains(nivelDestino.toUpperCase())) {
            throw new RuntimeException("Niveles inválidos");
        }

        if (nivelOrigen.equalsIgnoreCase(nivelDestino)) {
            throw new RuntimeException("El nivel origen y destino no pueden ser iguales");
        }

        ListaEscolar lista = listaRepository.findByIdConDetalles(listaId)
            .orElseThrow(() -> new RuntimeException("Lista no encontrada: " + listaId));

        for (DetalleListaEscolar detalle : lista.getDetalles()) {
            if ("VENDIDO".equals(detalle.getEstado()) || detalle.esItemRegalo()) {
                continue;
            }

            // Obtener producto y precio del nivel origen
            Producto productoOrigen = switch (nivelOrigen.toUpperCase()) {
                case "ECONOMICO" -> detalle.getProductoEconomico();
                case "MEDIO" -> detalle.getProductoMedio();
                case "PREMIUM" -> detalle.getProductoPremium();
                default -> null;
            };

            BigDecimal precioOrigen = switch (nivelOrigen.toUpperCase()) {
                case "ECONOMICO" -> detalle.getPrecioEconomico();
                case "MEDIO" -> detalle.getPrecioMedio();
                case "PREMIUM" -> detalle.getPrecioPremium();
                default -> BigDecimal.ZERO;
            };

            // Asignar al nivel destino
            switch (nivelDestino.toUpperCase()) {
                case "ECONOMICO" -> {
                    detalle.setProductoEconomico(productoOrigen);
                    detalle.setPrecioEconomico(precioOrigen);
                }
                case "MEDIO" -> {
                    detalle.setProductoMedio(productoOrigen);
                    detalle.setPrecioMedio(precioOrigen);
                }
                case "PREMIUM" -> {
                    detalle.setProductoPremium(productoOrigen);
                    detalle.setPrecioPremium(precioOrigen);
                }
            }

            detalleRepository.save(detalle);
        }

        recalcularTotales(lista);
        listaRepository.save(lista);

        log.info("Nivel {} copiado a {} en lista {}", nivelOrigen, nivelDestino, lista.getCodigoCompleto());
    }

    // =========================================================
    //  COTIZACIÓN MANUAL (PROVEEDOR EXTERNO)
    // =========================================================

    /**
     * Establece una cotización manual de proveedor para un item sin producto en inventario.
     * Permite cotizar productos que no tenemos pero podemos conseguir.
     */
    @Transactional
    public DetalleListaEscolar setCotizacionProveedor(Long detalleId, String nivel, BigDecimal precio,
                                                       String descripcion, String proveedor) {
        DetalleListaEscolar detalle = detalleRepository.findById(detalleId)
            .orElseThrow(() -> new RuntimeException("Detalle no encontrado: " + detalleId));

        if ("VENDIDO".equals(detalle.getEstado())) {
            throw new RuntimeException("No se puede modificar un item ya vendido");
        }

        if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El precio debe ser mayor a cero");
        }

        // Establecer cotización manual
        detalle.setCotizacionProveedor(nivel.toUpperCase(), precio, descripcion, proveedor);

        detalle = detalleRepository.save(detalle);

        // Recalcular totales
        recalcularTotales(detalle.getListaEscolar());
        listaRepository.save(detalle.getListaEscolar());

        log.info("Cotización proveedor establecida: detalle={}, nivel={}, precio={}, proveedor={}",
            detalleId, nivel, precio, proveedor);

        return detalle;
    }

    /**
     * Vincula un producto real del inventario a un item que tenía cotización manual.
     * Se usa cuando ya compramos el producto y lo ingresamos al sistema.
     */
    @Transactional
    public DetalleListaEscolar vincularProductoAManual(Long detalleId, String nivel, Long productoId) {
        DetalleListaEscolar detalle = detalleRepository.findById(detalleId)
            .orElseThrow(() -> new RuntimeException("Detalle no encontrado: " + detalleId));

        if ("VENDIDO".equals(detalle.getEstado())) {
            throw new RuntimeException("No se puede modificar un item ya vendido");
        }

        Producto producto = productoRepository.findById(productoId)
            .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + productoId));

        // Vincular producto
        detalle.vincularProducto(nivel.toUpperCase(), producto);
        detalle = detalleRepository.save(detalle);

        // Recalcular totales
        recalcularTotales(detalle.getListaEscolar());
        listaRepository.save(detalle.getListaEscolar());

        log.info("Producto {} vinculado al detalle {} nivel {}", productoId, detalleId, nivel);
        return detalle;
    }

    // =========================================================
    //  REPORTE DE PRODUCTOS FALTANTES
    // =========================================================

    /**
     * Obtiene el reporte de productos faltantes (sin producto ni cotización manual).
     * Agrupa por texto similar para identificar demanda.
     */
    @Transactional(readOnly = true)
    public List<ProductoFaltanteDTO> obtenerProductosFaltantes() {
        List<Object[]> faltantesAgrupados = detalleRepository.countFaltantesAgrupados();

        List<ProductoFaltanteDTO> resultado = new ArrayList<>();

        for (Object[] row : faltantesAgrupados) {
            String textoOriginal = (String) row[0];
            Long veces = (Long) row[1];
            Long cantidad = (Long) row[2];

            ProductoFaltanteDTO dto = new ProductoFaltanteDTO();
            dto.setTextoOriginal(textoOriginal);
            dto.setTextoNormalizado(ProductoFaltanteDTO.normalizarTexto(textoOriginal));
            dto.setVecessolicitado(veces != null ? veces.intValue() : 0);
            dto.setCantidadTotal(cantidad != null ? cantidad.intValue() : 0);

            resultado.add(dto);
        }

        return resultado;
    }

    /**
     * Obtiene los detalles de un producto faltante específico (todas las listas que lo piden).
     */
    @Transactional(readOnly = true)
    public List<ProductoFaltanteDTO.DetalleFaltante> obtenerDetallesFaltante(String textoNormalizado) {
        // Buscar todos los items sin producto que coincidan con el texto
        List<DetalleListaEscolar> todosItems = detalleRepository.findAll();
        List<ProductoFaltanteDTO.DetalleFaltante> detalles = new ArrayList<>();

        for (DetalleListaEscolar item : todosItems) {
            if (item.esItemRegalo() || "VENDIDO".equals(item.getEstado()) || "CANCELADO".equals(item.getEstado())) {
                continue;
            }

            ListaEscolar lista = item.getListaEscolar();
            if (lista == null || "COMPLETADA".equals(lista.getEstado()) || "CANCELADA".equals(lista.getEstado())) {
                continue;
            }

            String textoItem = ProductoFaltanteDTO.normalizarTexto(item.getTextoOriginal());
            if (!textoItem.equals(textoNormalizado)) {
                continue;
            }

            // Verificar si falta en algún nivel
            boolean faltaEconomico = item.getProductoEconomico() == null && !Boolean.TRUE.equals(item.getCotizadoProveedorEconomico());
            boolean faltaMedio = item.getProductoMedio() == null && !Boolean.TRUE.equals(item.getCotizadoProveedorMedio());
            boolean faltaPremium = item.getProductoPremium() == null && !Boolean.TRUE.equals(item.getCotizadoProveedorPremium());

            if (faltaEconomico || faltaMedio || faltaPremium) {
                ProductoFaltanteDTO.DetalleFaltante detalle = new ProductoFaltanteDTO.DetalleFaltante();
                detalle.setDetalleId(item.getId());
                detalle.setListaId(lista.getId());
                detalle.setCodigoLista(lista.getCodigoCompleto());
                detalle.setNombreAlumno(lista.getNombreAlumno());
                detalle.setColegio(lista.getColegio());
                detalle.setGrado(lista.getGrado());
                detalle.setCantidad(item.getCantidadSolicitada());
                detalle.setFechaSolicitud(lista.getFechaCreacion());

                // Determinar niveles faltantes
                StringBuilder niveles = new StringBuilder();
                if (faltaEconomico) niveles.append("E");
                if (faltaMedio) niveles.append("M");
                if (faltaPremium) niveles.append("P");
                detalle.setNivel(niveles.toString());

                detalles.add(detalle);
            }
        }

        // Ordenar por fecha más reciente
        detalles.sort((a, b) -> {
            if (a.getFechaSolicitud() == null) return 1;
            if (b.getFechaSolicitud() == null) return -1;
            return b.getFechaSolicitud().compareTo(a.getFechaSolicitud());
        });

        return detalles;
    }

    /**
     * Obtiene todos los items con cotización de proveedor pendientes de compra.
     */
    @Transactional(readOnly = true)
    public List<DetalleListaEscolar> obtenerCotizadosProveedor() {
        return detalleRepository.findCotizadosProveedor();
    }

    /**
     * Marca un item como "producto comprado" (ya ingresado al inventario).
     */
    @Transactional
    public DetalleListaEscolar marcarProductoComprado(Long detalleId) {
        DetalleListaEscolar detalle = detalleRepository.findById(detalleId)
            .orElseThrow(() -> new RuntimeException("Detalle no encontrado: " + detalleId));

        detalle.setProductoComprado(true);
        return detalleRepository.save(detalle);
    }

    /**
     * Asigna un producto a múltiples items (cuando se compra producto para varias listas).
     */
    @Transactional
    public int asignarProductoMasivo(String textoNormalizado, String nivel, Long productoId) {
        Producto producto = productoRepository.findById(productoId)
            .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + productoId));

        List<ProductoFaltanteDTO.DetalleFaltante> detalles = obtenerDetallesFaltante(textoNormalizado);

        int actualizados = 0;
        for (ProductoFaltanteDTO.DetalleFaltante det : detalles) {
            if (det.getNivel().contains(nivel.substring(0, 1).toUpperCase())) {
                try {
                    actualizarProductoPorNivel(det.getDetalleId(), nivel, productoId);
                    actualizados++;
                } catch (Exception e) {
                    log.warn("No se pudo actualizar detalle {}: {}", det.getDetalleId(), e.getMessage());
                }
            }
        }

        log.info("Producto {} asignado masivamente a {} items para texto '{}'",
            productoId, actualizados, textoNormalizado);

        return actualizados;
    }

    /**
     * Estadísticas de productos faltantes.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerEstadisticasFaltantes() {
        List<ProductoFaltanteDTO> faltantes = obtenerProductosFaltantes();
        List<DetalleListaEscolar> cotizadosProveedor = obtenerCotizadosProveedor();

        int totalItemsFaltantes = faltantes.stream().mapToInt(ProductoFaltanteDTO::getCantidadTotal).sum();
        int totalProductosDiferentes = faltantes.size();
        int totalCotizadosProveedor = cotizadosProveedor.size();

        return Map.of(
            "totalItemsFaltantes", totalItemsFaltantes,
            "totalProductosDiferentes", totalProductosDiferentes,
            "totalCotizadosProveedor", totalCotizadosProveedor,
            "topFaltantes", faltantes.stream().limit(10).toList()
        );
    }
}
