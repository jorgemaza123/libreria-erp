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
import java.util.stream.Collectors;

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
    // FASE 1: CREACIÓN DE LISTA
    // =========================================================

    /**
     * Crea una nueva lista escolar a partir del texto OCR.
     * Parsea el texto y crea los items sin cotizar.
     */
    @Transactional
    public ListaEscolar crearLista(ListaEscolarDTO dto) {
        log.info("Creando nueva lista escolar para alumno: {}", dto.getNombreAlumno());

        // FIX ERROR-7: usar correlativo con lock pesimista para evitar duplicados en
        // concurrencia.
        // El código "LISTA_ESCOLAR" con serie "LE001" se crea automáticamente si no
        // existe.
        Correlativo correlativoLista = correlativoRepository.findByCodigoAndSerieWithLock("LISTA_ESCOLAR", SERIE_LISTA)
                .orElseGet(() -> correlativoRepository.save(new Correlativo("LISTA_ESCOLAR", SERIE_LISTA, 0)));

        int ultimoCorrelativo = correlativoLista.getUltimoNumero() != null ? correlativoLista.getUltimoNumero() : 0;
        int maxEnDb = listaRepository.findMaxNumeroBySerie(SERIE_LISTA);

        int nuevoNumero = Math.max(ultimoCorrelativo, maxEnDb) + 1;

        correlativoLista.setUltimoNumero(nuevoNumero);
        correlativoRepository.save(correlativoLista);

        // Crear lista
        ListaEscolar lista = new ListaEscolar();
        lista.setSerie(SERIE_LISTA);
        lista.setNumero(nuevoNumero);
        lista.setNombreAlumno(dto.getNombreAlumno());
        lista.setGrado(dto.getGrado());
        lista.setSeccion(dto.getSeccion());
        lista.setColegio(dto.getColegio());
        lista.setAnioEscolar(dto.getAnioEscolar() != null ? dto.getAnioEscolar() : LocalDate.now().getYear());
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
        for (int i = 0; i < itemsParseados.size(); i++) {
            TextoListaParser.ItemParseado item = itemsParseados.get(i);
            DetalleListaEscolar detalle = new DetalleListaEscolar();
            detalle.setListaEscolar(lista);

            // Usar texto editado por el usuario en preview si existe
            String textoItem = item.getTextoOriginal();
            if (dto.getTextosEditados() != null && dto.getTextosEditados().containsKey(i)) {
                String editado = dto.getTextosEditados().get(i);
                if (editado != null && !editado.isBlank())
                    textoItem = editado.trim();
            }
            detalle.setTextoOriginal(textoItem);

            // Usar cantidad validada por el usuario (preview) si existe, si no la del
            // parser
            int cantidad = item.getCantidad();
            if (dto.getCantidadesValidadas() != null &&
                    dto.getCantidadesValidadas().containsKey(i)) {
                cantidad = Math.max(1, dto.getCantidadesValidadas().get(i));
            }
            detalle.setCantidadSolicitada(cantidad);
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
    // FASE 2: GENERACIÓN DE COTIZACIONES
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
            // Saltar regalos y servicios (no aplica cotizacion automatica)
            if (detalle.esItemRegalo() || detalle.esItemServicio()) {
                continue;
            }
            // Buscar match para este item
            MatcherProductosService.ResultadoMatch match = matcherService.buscarMatch(detalle.getTextoOriginal(),
                    detalle.getCantidadSolicitada());

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

            if (d.getPrecioEconomico() != null && !d.esOmitidoPorNivel("ECONOMICO")) {
                totalEconomico = totalEconomico.add(d.getPrecioEconomico().multiply(cantidad));
            }
            if (d.getPrecioMedio() != null && !d.esOmitidoPorNivel("MEDIO")) {
                totalMedio = totalMedio.add(d.getPrecioMedio().multiply(cantidad));
            }
            if (d.getPrecioPremium() != null && !d.esOmitidoPorNivel("PREMIUM")) {
                totalPremium = totalPremium.add(d.getPrecioPremium().multiply(cantidad));
            }
        }

        lista.setTotalEconomico(totalEconomico);
        lista.setTotalMedio(totalMedio);
        lista.setTotalPremium(totalPremium);
    }

    // =========================================================
    // FASE 3: EDICIÓN DE COTIZACIÓN
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
    // FASE 4: VENTA PARCIAL/TOTAL
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
        // FIX: cachear productos bloqueados para evitar doble findByIdWithLock
        // que causa OptimisticLockException cuando el mismo producto aparece múltiples veces
        Map<Long, Producto> productosCacheados = new HashMap<>();

        for (ItemSeleccionadoDTO item : dto.getItemsSeleccionados()) {
            DetalleListaEscolar detalle = null;

            if (item.getDetalleListaId() != null) {
                // Item de la lista escolar
                detalle = detalleRepository.findById(item.getDetalleListaId())
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
            }
            // else: producto adicional sin entrada en la lista — solo validar stock

            // Validar producto asignado (usar caché para evitar doble lock del mismo producto)
            Producto producto = productosCacheados.computeIfAbsent(item.getProductoId(),
                    pid -> productoRepository.findByIdWithLock(pid)
                            .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + pid)));

            // Validar stock (si no es servicio)
            if (!"SERVICIO".equalsIgnoreCase(producto.getTipo())) {
                if (producto.getStockActual() == null || producto.getStockActual() < item.getCantidad()) {
                    throw new RuntimeException("Stock insuficiente para: " + producto.getNombre());
                }
            }

            // Actualizar detalle con selección final (solo si es item de la lista)
            if (detalle != null) {
                detalle.setNivelSeleccionado(item.getNivelSeleccionado());
                detalle.setPrecioFinal(item.getPrecioUnitario());
            }

            itemsAVender.add(detalle); // null for additional products
            totalVenta = totalVenta.add(item.getSubtotal());
        }

        // 5. Crear venta
        Venta venta = crearVentaDesdeListaEscolar(dto, lista, totalVenta);
        venta = ventaRepository.save(venta);

        // 6. Procesar cada item
        BigDecimal totalGravada = BigDecimal.ZERO;
        BigDecimal totalIgv = BigDecimal.ZERO;
        BigDecimal igvFactor = configuracionService.getIgvFactor();

        for (int i = 0; i < itemsAVender.size(); i++) {
            DetalleListaEscolar detalleLista = itemsAVender.get(i);
            ItemSeleccionadoDTO itemDTO = dto.getItemsSeleccionados().get(i);

            // FIX: reusar el producto ya cacheado desde la fase de validación (evita OptimisticLockException)
            Producto producto = productosCacheados.get(itemDTO.getProductoId());

            // Crear detalle venta
            DetalleVenta detalleVenta = new DetalleVenta();
            detalleVenta.setVenta(venta);
            detalleVenta.setProducto(producto);
            detalleVenta.setCantidad(BigDecimal.valueOf(itemDTO.getCantidad()));
            detalleVenta.setDescripcion(producto.getNombre());
            detalleVenta.setUnidadMedida(producto.getUnidadMedida() != null ? producto.getUnidadMedida() : "NIU");
            detalleVenta.setPrecioUnitario(itemDTO.getPrecioUnitario());
            detalleVenta.setSubtotal(itemDTO.getSubtotal());

            // Congelar costo al momento de la venta
            BigDecimal costoUnit = producto.getPrecioCompra() != null ? producto.getPrecioCompra() : BigDecimal.ZERO;
            detalleVenta.setCostoUnitario(costoUnit);
            detalleVenta.setUtilidadUnitaria(itemDTO.getPrecioUnitario().subtract(costoUnit));
            detalleVenta.setUtilidadTotal(itemDTO.getPrecioUnitario().subtract(costoUnit)
                    .multiply(BigDecimal.valueOf(itemDTO.getCantidad())));

            // Cálculos IGV
            BigDecimal valorUnitario = itemDTO.getPrecioUnitario().divide(igvFactor, 6, RoundingMode.HALF_UP);
            BigDecimal valorVenta = valorUnitario.multiply(BigDecimal.valueOf(itemDTO.getCantidad()));
            BigDecimal igvItem = itemDTO.getSubtotal().subtract(valorVenta);

            detalleVenta.setValorUnitario(valorUnitario);
            detalleVenta.setPorcentajeIgv(configuracionService.getIgvPorcentaje());

            // FIX ERROR-8: asignar codigoTipoAfectacionIgv para cumplimiento SUNAT
            String codigoAfectacion = com.libreria.sistema.util.Constants.AFECTACION_GRAVADO;
            if (producto.getTipoAfectacionIgv() != null) {
                codigoAfectacion = switch (producto.getTipoAfectacionIgv().toUpperCase()) {
                    case "EXONERADO" -> com.libreria.sistema.util.Constants.AFECTACION_EXONERADO;
                    case "INAFECTO" -> com.libreria.sistema.util.Constants.AFECTACION_INAFECTO;
                    default -> com.libreria.sistema.util.Constants.AFECTACION_GRAVADO;
                };
            }
            detalleVenta.setCodigoTipoAfectacionIgv(codigoAfectacion);

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

            // Marcar item como vendido (solo items de la lista, no productos adicionales)
            if (detalleLista != null) {
                detalleLista.marcarVendido(venta, null); // El ID se asigna después de guardar
                detalleRepository.save(detalleLista);
            }
        }

        // 7. Actualizar totales de venta
        venta.setTotal(totalVenta);
        venta.setTotalGravada(totalGravada);
        venta.setTotalIgv(totalIgv);

        // 8. Procesar forma de pago
        procesarFormaPago(venta, dto, totalVenta);

        // 9. Guardar venta
        venta = ventaRepository.save(venta);

        // Actualizar IDs de detalle venta en items lista (omitir productos adicionales)
        for (int i = 0; i < venta.getItems().size(); i++) {
            DetalleListaEscolar detalleLista = itemsAVender.get(i);
            if (detalleLista != null) {
                detalleLista.setDetalleVentaId(venta.getItems().get(i).getId());
                detalleRepository.save(detalleLista);
            }
        }

        // 10. Registrar pago y caja
        BigDecimal montoAbonado = "CREDITO".equals(dto.getFormaPago())
                ? (dto.getMontoInicial() != null ? dto.getMontoInicial() : BigDecimal.ZERO)
                : totalVenta;

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
                SecurityContextHolder.getContext().getAuthentication().getName()).ifPresent(ventaLista::setUsuario);
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
                "estadoLista", lista.getEstado());
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
        venta.setClienteDenominacion(
                dto.getClienteNombre() != null ? dto.getClienteNombre() : lista.getContactoNombre());
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
                monto, CategoriaMovimiento.VENTA);
    }

    // =========================================================
    // CONSULTAS Y LISTADOS
    // =========================================================

    /**
     * Busca listas con filtros y paginación.
     * La búsqueda es global: alumno, colegio, contacto, teléfono, grado, código.
     * Se normaliza el texto removiendo tildes para búsqueda flexible.
     */
    @Transactional(readOnly = true)
    public Page<ListaEscolar> buscar(String estado, String busqueda,
            Integer anio, Pageable pageable) {
        // Normalizar: remover tildes/acentos para búsqueda flexible
        String busquedaNormalizada = normalizarBusqueda(busqueda);
        String estadoLimpio = (estado != null && estado.trim().isEmpty()) ? null : estado;
        return listaRepository.buscarConFiltros(estadoLimpio, busquedaNormalizada, anio, pageable);
    }

    /**
     * Normaliza texto de búsqueda: remueve acentos/tildes y caracteres especiales.
     */
    private String normalizarBusqueda(String texto) {
        if (texto == null || texto.trim().isEmpty())
            return null;
        String normalizado = java.text.Normalizer.normalize(texto.trim(), java.text.Normalizer.Form.NFD);
        normalizado = normalizado.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return normalizado;
    }

    /**
     * Omite o des-omite un item para un nivel específico.
     * No elimina el producto/precio asignado — solo lo oculta de la cotización.
     */
    @Transactional
    public DetalleListaEscolar omitirPorNivel(Long detalleId, String nivel, boolean omitir) {
        DetalleListaEscolar detalle = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new RuntimeException("Detalle no encontrado: " + detalleId));

        if ("VENDIDO".equals(detalle.getEstado())) {
            throw new RuntimeException("No se puede omitir un item ya vendido");
        }

        detalle.setOmitidoPorNivel(nivel, omitir);
        detalle = detalleRepository.save(detalle);

        // Recalcular totales de la lista
        recalcularTotales(detalle.getListaEscolar());
        listaRepository.save(detalle.getListaEscolar());

        log.info("Detalle {} {} en nivel {}", detalleId, omitir ? "omitido" : "restaurado", nivel);
        return detalle;
    }

    /**
     * Actualiza las cantidades solicitadas de varios detalles en lote.
     * Usado para persistir los cambios de cantidad antes de generar el PDF.
     */
    @Transactional
    public void actualizarCantidades(Long listaId, List<Map<String, Object>> cantidades) {
        for (Map<String, Object> entry : cantidades) {
            Long detalleId = Long.valueOf(entry.get("id").toString());
            int cantidad = Integer.parseInt(entry.get("cantidad").toString());
            if (cantidad < 1)
                cantidad = 1;
            final int cantidadFinal = cantidad;
            detalleRepository.findById(detalleId).ifPresent(detalle -> {
                if (detalle.getListaEscolar() != null
                        && detalle.getListaEscolar().getId().equals(listaId)) {
                    detalle.setCantidadSolicitada(cantidadFinal);
                    detalleRepository.save(detalle);
                }
            });
        }
    }

    /**
     * Guarda cambios en una lista existente.
     */
    @Transactional
    public ListaEscolar guardarLista(ListaEscolar lista) {
        return listaRepository.save(lista);
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
     * Obtiene todas las transacciones de venta de una lista con sus detalles
     * completos.
     * Carga los DetalleVenta de cada Venta dentro de la transacción para evitar
     * LazyInit.
     * Usado para el PDF combinado y el POS modal de devoluciones.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerTransaccionesLista(Long listaId) {
        List<VentaListaEscolar> ventasLista = ventaListaRepository.findByListaIdConDatos(listaId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (VentaListaEscolar vl : ventasLista) {
            Venta venta = vl.getVenta();
            if (venta == null || "ANULADO".equals(venta.getEstado()))
                continue;
            // Acceder a items (LAZY) dentro de la transacción activa
            List<com.libreria.sistema.model.DetalleVenta> detalles = venta.getItems();
            Map<String, Object> ventaMap = new LinkedHashMap<>();
            ventaMap.put("ventaId", venta.getId());
            ventaMap.put("serie", venta.getSerie());
            ventaMap.put("numero", venta.getNumero());
            ventaMap.put("comprobante", venta.getSerie() + "-" + String.format("%08d", venta.getNumero()));
            ventaMap.put("fecha", venta.getFechaEmision() != null ? venta.getFechaEmision().toString() : "");
            ventaMap.put("total", venta.getTotal());
            ventaMap.put("estado", venta.getEstado());
            List<Map<String, Object>> items = new ArrayList<>();
            if (detalles != null) {
                for (com.libreria.sistema.model.DetalleVenta d : detalles) {
                    if (d.getProducto() == null)
                        continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productoId", d.getProducto().getId());
                    item.put("descripcion",
                            d.getDescripcion() != null ? d.getDescripcion() : d.getProducto().getNombre());
                    item.put("cantidad", d.getCantidad() != null ? d.getCantidad().intValue() : 0);
                    item.put("precioUnitario", d.getPrecioUnitario());
                    item.put("subtotal", d.getSubtotal());
                    items.add(item);
                }
            }
            ventaMap.put("items", items);
            result.add(ventaMap);
        }
        return result;
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
        if (anio == null)
            anio = LocalDate.now().getYear();

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
    // EDICIÓN POR NIVELES
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

        // FIX: Si nivelSeleccionado es null (item venía de NO_COTIZADO sin match
        // automático),
        // establecerlo al nivel asignado manualmente.
        // Sin esto, getProductoFinal() retorna el campo genérico 'producto' (que
        // también es null)
        // ignorando productoMedio/productoEconomico/productoPremium recién asignados,
        // y el POS muestra el item como "Sin asignar" aunque el producto sí esté
        // persistido.
        if (detalle.getNivelSeleccionado() == null) {
            detalle.setNivelSeleccionado(nivel.toUpperCase());
            detalle.setPrecioFinal(precio);
        } else if (nivel.equalsIgnoreCase(detalle.getNivelSeleccionado())) {
            // Si el nivel activo es el mismo que se actualiza, refrescar precioFinal
            detalle.setPrecioFinal(precio);
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

    // =========================================================
    // SERVICIOS ADICIONALES
    // =========================================================

    /**
     * Agrega un servicio adicional a una lista (ej: forrado de cuadernos).
     * A diferencia del regalo, el servicio tiene un precio que se suma al total.
     */
    @Transactional
    public DetalleListaEscolar agregarServicio(Long listaId, String texto, BigDecimal precio, String nivel) {
        ListaEscolar lista = listaRepository.findByIdConDetalles(listaId)
                .orElseThrow(() -> new RuntimeException("Lista no encontrada: " + listaId));

        if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El precio del servicio debe ser mayor a cero");
        }

        // Determinar el orden
        int maxOrden = lista.getDetalles().stream()
                .mapToInt(DetalleListaEscolar::getOrden)
                .max()
                .orElse(0);

        DetalleListaEscolar servicio = DetalleListaEscolar.crearServicio(lista, texto, nivel, precio, maxOrden + 1);
        servicio = detalleRepository.save(servicio);

        lista.getDetalles().add(servicio);
        lista.setItemsTotal(lista.getDetalles().size());
        lista.recalcularPendientes();
        recalcularTotales(lista);
        listaRepository.save(lista);

        log.info("Servicio '{}' (S/ {}) agregado a lista {} para nivel {}", texto, precio, lista.getCodigoCompleto(),
                nivel);
        return servicio;
    }

    /**
     * Elimina un servicio de una lista.
     */
    @Transactional
    public void eliminarServicio(Long detalleId) {
        DetalleListaEscolar detalle = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new RuntimeException("Detalle no encontrado: " + detalleId));

        if (!detalle.esItemServicio()) {
            throw new RuntimeException("Solo se pueden eliminar items de tipo servicio");
        }

        if ("VENDIDO".equals(detalle.getEstado())) {
            throw new RuntimeException("No se puede eliminar un servicio ya vendido");
        }

        ListaEscolar lista = detalle.getListaEscolar();
        lista.getDetalles().remove(detalle);
        detalleRepository.delete(detalle);

        lista.setItemsTotal(lista.getDetalles().size());
        lista.recalcularPendientes();
        recalcularTotales(lista);
        listaRepository.save(lista);

        log.info("Servicio {} eliminado de lista {}", detalleId, lista.getCodigoCompleto());
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

        int copiados = 0;
        for (DetalleListaEscolar detalle : lista.getDetalles()) {
            if ("VENDIDO".equals(detalle.getEstado()) || detalle.esItemRegalo() || detalle.esItemServicio()) {
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

            // Si el origen no tiene nada que copiar, saltar (no sobreescribir destino con
            // null)
            if (productoOrigen == null && (precioOrigen == null || precioOrigen.compareTo(BigDecimal.ZERO) == 0)) {
                continue;
            }

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
            copiados++;
        }

        recalcularTotales(lista);
        listaRepository.save(lista);

        log.info("Nivel {} copiado a {} en lista {}", nivelOrigen, nivelDestino, lista.getCodigoCompleto());
    }

    // =========================================================
    // COTIZACIÓN MANUAL (PROVEEDOR EXTERNO)
    // =========================================================

    /**
     * Establece una cotización manual de proveedor para un item sin producto en
     * inventario.
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
     * Vincula un producto real del inventario a un item que tenía cotización
     * manual.
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
    // REPORTE DE PRODUCTOS FALTANTES
    // =========================================================

    /**
     * Obtiene el reporte de productos faltantes (sin producto ni cotización
     * manual).
     * Agrupa por texto similar para identificar demanda.
     */
    @Transactional(readOnly = true)
    public List<ProductoFaltanteDTO> obtenerProductosFaltantes() {
        List<Object[]> faltantesAgrupados = detalleRepository.countFaltantesAgrupados();

        List<ProductoFaltanteDTO> resultado = new ArrayList<>();

        for (Object[] row : faltantesAgrupados) {
            String textoOriginal = (String) row[0];
            if (textoOriginal == null || textoOriginal.isBlank())
                continue;

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
     * Obtiene los detalles de un producto faltante específico (todas las listas que
     * lo piden).
     */
    @Transactional(readOnly = true)
    public List<ProductoFaltanteDTO.DetalleFaltante> obtenerDetallesFaltante(String textoNormalizado) {
        if (textoNormalizado == null || textoNormalizado.isBlank()) {
            return new ArrayList<>();
        }

        // Buscar solo los items que coinciden con el texto (query optimizada, no
        // findAll)
        List<DetalleListaEscolar> items = detalleRepository.findFaltantesPorTexto(textoNormalizado);
        List<ProductoFaltanteDTO.DetalleFaltante> detalles = new ArrayList<>();

        for (DetalleListaEscolar item : items) {
            ListaEscolar lista = item.getListaEscolar();
            if (lista == null)
                continue;

            // Verificar niveles faltantes
            boolean faltaEconomico = item.getProductoEconomico() == null
                    && !Boolean.TRUE.equals(item.getCotizadoProveedorEconomico());
            boolean faltaMedio = item.getProductoMedio() == null
                    && !Boolean.TRUE.equals(item.getCotizadoProveedorMedio());
            boolean faltaPremium = item.getProductoPremium() == null
                    && !Boolean.TRUE.equals(item.getCotizadoProveedorPremium());

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

                StringBuilder niveles = new StringBuilder();
                if (faltaEconomico)
                    niveles.append("E");
                if (faltaMedio)
                    niveles.append("M");
                if (faltaPremium)
                    niveles.append("P");
                detalle.setNivel(niveles.toString());

                detalles.add(detalle);
            }
        }

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
     * Asigna un producto a múltiples items (cuando se compra producto para varias
     * listas).
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
     * Elimina items faltantes (sin producto asignado) agrupados por texto.
     */
    @Transactional
    public int eliminarItemsFaltantes(List<String> textos) {
        int eliminados = 0;
        for (String texto : textos) {
            String textoNormalizado = texto.toLowerCase().trim();
            List<DetalleListaEscolar> items = detalleRepository.findFaltantesPorTexto(textoNormalizado);
            for (DetalleListaEscolar item : items) {
                // Solo eliminar items que realmente no tienen producto en ningún nivel
                if (item.getProductoEconomico() == null && item.getProductoMedio() == null
                        && item.getProductoPremium() == null
                        && !Boolean.TRUE.equals(item.getCotizadoProveedorEconomico())
                        && !Boolean.TRUE.equals(item.getCotizadoProveedorMedio())
                        && !Boolean.TRUE.equals(item.getCotizadoProveedorPremium())) {
                    ListaEscolar lista = item.getListaEscolar();
                    lista.getDetalles().remove(item);
                    detalleRepository.delete(item);
                    lista.setItemsTotal(lista.getDetalles().size());
                    lista.recalcularPendientes();
                    listaRepository.save(lista);
                    eliminados++;
                }
            }
        }
        log.info("Eliminados {} items faltantes", eliminados);
        return eliminados;
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
                "topFaltantes", faltantes.stream().limit(10).toList());
    }

    // =========================================================
    // RESUMEN FINANCIERO - Cálculo en memoria sin queries
    // =========================================================

    /**
     * Calcula el resumen financiero de una lista escolar.
     * Recalcula totalVenta desde detalles (no confía en valor almacenado).
     * Si precioCompra es null, lo trata como 0 y cuenta como item sin costo.
     * Solo afecta módulo de listas escolares.
     */
    @Transactional(readOnly = true)
    public ResumenFinancieroDTO calcularResumenFinanciero(Long listaId) {
        ListaEscolar lista = listaRepository.findByIdConDetalles(listaId)
                .orElseThrow(() -> new RuntimeException("Lista no encontrada"));

        ResumenFinancieroDTO resumen = new ResumenFinancieroDTO();
        Set<Long> productosSinCosto = new HashSet<>();

        for (DetalleListaEscolar detalle : lista.getDetalles()) {
            if (Boolean.TRUE.equals(detalle.getEsRegalo()))
                continue;
            if (Boolean.TRUE.equals(detalle.getEsServicio()))
                continue;

            int cantidad = detalle.getCantidadSolicitada() != null ? detalle.getCantidadSolicitada() : 1;
            resumen.setItemsTotal(resumen.getItemsTotal() + 1);

            // ECONOMICO
            acumularNivel(resumen.getEconomico(), detalle.getProductoEconomico(),
                    detalle.getPrecioEconomico(), cantidad, productosSinCosto);

            // MEDIO
            acumularNivel(resumen.getMedio(), detalle.getProductoMedio(),
                    detalle.getPrecioMedio(), cantidad, productosSinCosto);

            // PREMIUM
            acumularNivel(resumen.getPremium(), detalle.getProductoPremium(),
                    detalle.getPrecioPremium(), cantidad, productosSinCosto);
        }

        resumen.setItemsSinCosto(productosSinCosto.size());

        resumen.getEconomico().calcular();
        resumen.getMedio().calcular();
        resumen.getPremium().calcular();

        return resumen;
    }

    // =========================================================
    // EDITAR TEXTO ORIGINAL Y REPROCESAR
    // =========================================================

    /**
     * Actualiza el texto original de la lista y sincroniza los detalles:
     * - Líneas que permanecen en el texto → se mantienen sin cambios (respeta
     * ediciones manuales)
     * - Líneas eliminadas del texto → se borran si no están VENDIDO
     * - Líneas nuevas en el texto → se crean y se cotizan automáticamente
     * Los ítems con estado VENDIDO nunca son afectados (validación dentro de la
     * transacción).
     *
     * @return Map con: success, mensaje, itemsTotal, totales y listas de cambios
     *         para UI dinámica
     */
    @Transactional
    public Map<String, Object> actualizarTextoYReprocesar(Long listaId, String nuevoTexto) {
        ListaEscolar lista = listaRepository.findByIdConDetalles(listaId)
                .orElseThrow(() -> new RuntimeException("Lista no encontrada: " + listaId));

        // Validar estado (no modificar listas cerradas)
        if ("COMPLETADA".equals(lista.getEstado()) || "CANCELADA".equals(lista.getEstado())
                || "ELIMINADA".equals(lista.getEstado())) {
            throw new RuntimeException("No se puede modificar una lista en estado " + lista.getEstado());
        }

        // Actualizar texto almacenado
        lista.setTextoOriginal(nuevoTexto);

        // Parsear nuevo texto
        List<TextoListaParser.ItemParseado> nuevosItemsParseados = parser.parsear(nuevoTexto);

        // Set de textos normalizados del nuevo texto (para comparar)
        Set<String> nuevosTextosNorm = nuevosItemsParseados.stream()
                .map(i -> normalizarParaComparar(i.getTextoOriginal()))
                .collect(Collectors.toSet());

        // Separar detalles vendidos (intocables) y no-vendidos
        // IMPORTANTE: Se re-lee el estado de cada detalle desde la colección ya cargada
        // (dentro de la transacción, refleja el estado actual en BD gracias al SELECT
        // FOR UPDATE implícito)
        List<DetalleListaEscolar> detallesNoVendidos = lista.getDetalles().stream()
                .filter(d -> !"VENDIDO".equals(d.getEstado()))
                .collect(Collectors.toList());

        // Set de textos normalizados de los existentes no-vendidos
        Set<String> existentesTextosNorm = detallesNoVendidos.stream()
                .map(d -> normalizarParaComparar(d.getTextoOriginal()))
                .collect(Collectors.toSet());

        // 1. Identificar detalles a eliminar (están en BD pero ya no en el nuevo texto)
        List<Long> idsEliminados = new ArrayList<>();
        Iterator<DetalleListaEscolar> iter = lista.getDetalles().iterator();
        while (iter.hasNext()) {
            DetalleListaEscolar d = iter.next();
            // Nunca eliminar vendidos
            if ("VENDIDO".equals(d.getEstado()))
                continue;
            // Si ya no está en el nuevo texto, eliminar
            if (!nuevosTextosNorm.contains(normalizarParaComparar(d.getTextoOriginal()))) {
                idsEliminados.add(d.getId());
                iter.remove(); // orphanRemoval lo borrará de BD al hacer save
            }
        }

        // 2. Identificar e insertar ítems nuevos (están en el nuevo texto pero no en
        // BD)
        List<TextoListaParser.ItemParseado> itemsNuevos = nuevosItemsParseados.stream()
                .filter(i -> !existentesTextosNorm.contains(normalizarParaComparar(i.getTextoOriginal())))
                .collect(Collectors.toList());

        int maxOrden = lista.getDetalles().stream()
                .mapToInt(DetalleListaEscolar::getOrden)
                .max().orElse(0);

        List<DetalleListaEscolar> nuevosDetalles = new ArrayList<>();

        for (TextoListaParser.ItemParseado item : itemsNuevos) {
            DetalleListaEscolar detalle = new DetalleListaEscolar();
            detalle.setListaEscolar(lista);
            detalle.setTextoOriginal(item.getTextoOriginal());
            detalle.setCantidadSolicitada(item.getCantidad());
            detalle.setEstado("NO_COTIZADO");
            detalle.setOrden(++maxOrden);

            // Buscar match automático para el nuevo ítem
            MatcherProductosService.ResultadoMatch match = matcherService.buscarMatch(item.getTextoOriginal(),
                    item.getCantidad());

            if (match.isTieneMatch()) {
                detalle.setProducto(match.getProductoPrincipal());
                detalle.setProductoNombreSnapshot(match.getProductoPrincipal().getNombre());
                detalle.setConfianzaMatch(BigDecimal.valueOf(match.getConfianza()));
                detalle.setMatchAutomatico(true);
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
                detalle.setNivelSeleccionado("MEDIO");
                detalle.setPrecioFinal(match.getPrecioMedio());
                detalle.setEstado("COTIZADO");
            }

            lista.getDetalles().add(detalle);
            nuevosDetalles.add(detalle);
        }

        // Actualizar conteos y totales
        lista.setItemsTotal(lista.getDetalles().size());
        lista.recalcularPendientes();
        recalcularTotales(lista);

        // Guardar: cascade persiste nuevos detalles y orphanRemoval borra los
        // eliminados
        lista = listaRepository.save(lista);

        // Construir datos de nuevos ítems para actualización dinámica del frontend
        List<Map<String, Object>> nuevosItemsInfo = new ArrayList<>();
        for (DetalleListaEscolar det : nuevosDetalles) {
            Map<String, Object> info = new HashMap<>();
            info.put("id", det.getId());
            info.put("textoOriginal", det.getTextoOriginal());
            info.put("cantidad", det.getCantidadSolicitada());
            info.put("estado", det.getEstado());
            info.put("orden", det.getOrden());
            info.put("nivelSeleccionado", det.getNivelSeleccionado());
            info.put("precioEconomico", det.getPrecioEconomico());
            info.put("precioMedio", det.getPrecioMedio());
            info.put("precioPremium", det.getPrecioPremium());
            info.put("productoEconomicoId",
                    det.getProductoEconomico() != null ? det.getProductoEconomico().getId() : null);
            info.put("productoEconomicoNombre",
                    det.getProductoEconomico() != null ? det.getProductoEconomico().getNombre() : null);
            info.put("productoMedioId", det.getProductoMedio() != null ? det.getProductoMedio().getId() : null);
            info.put("productoMedioNombre", det.getProductoMedio() != null ? det.getProductoMedio().getNombre() : null);
            info.put("productoPremiumId", det.getProductoPremium() != null ? det.getProductoPremium().getId() : null);
            info.put("productoPremiumNombre",
                    det.getProductoPremium() != null ? det.getProductoPremium().getNombre() : null);
            info.put("productoNombre", det.getProductoFinal() != null ? det.getProductoFinal().getNombre() : null);
            info.put("tieneMatch", !"NO_COTIZADO".equals(det.getEstado()));
            nuevosItemsInfo.add(info);
        }

        log.info("Lista {} actualizada: {} items eliminados, {} items nuevos agregados y cotizados",
                lista.getCodigoCompleto(), idsEliminados.size(), nuevosDetalles.size());

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("success", true);
        resultado.put("mensaje", String.format(
                "Lista actualizada. %d item(s) eliminado(s), %d item(s) nuevo(s) agregado(s).",
                idsEliminados.size(), nuevosDetalles.size()));
        resultado.put("itemsTotal", lista.getItemsTotal());
        resultado.put("totalEconomico", lista.getTotalEconomico());
        resultado.put("totalMedio", lista.getTotalMedio());
        resultado.put("totalPremium", lista.getTotalPremium());
        resultado.put("itemsEliminados", idsEliminados);
        resultado.put("nuevosItems", nuevosItemsInfo);
        return resultado;
    }

    /**
     * Agrega nuevos ítems a una lista existente sin eliminar los ya existentes.
     * Parsea el texto, crea detalles nuevos con orden continuado y cotiza
     * automáticamente.
     */
    @Transactional
    public Map<String, Object> agregarItemsTexto(Long listaId, String texto) {
        ListaEscolar lista = listaRepository.findByIdConDetalles(listaId)
                .orElseThrow(() -> new RuntimeException("Lista no encontrada: " + listaId));

        if ("CANCELADA".equals(lista.getEstado()) || "ELIMINADA".equals(lista.getEstado())) {
            throw new RuntimeException("No se puede modificar una lista en estado " + lista.getEstado());
        }

        // Si estaba COMPLETADA, reabrirla para permitir más ventas
        boolean eraCompletada = "COMPLETADA".equals(lista.getEstado());

        List<TextoListaParser.ItemParseado> nuevosParseados = parser.parsear(texto);
        if (nuevosParseados.isEmpty()) {
            throw new RuntimeException("No se detectaron items en el texto ingresado");
        }

        int maxOrden = lista.getDetalles().stream()
                .mapToInt(d -> d.getOrden() != null ? d.getOrden() : 0)
                .max().orElse(0);

        List<DetalleListaEscolar> nuevosDetalles = new ArrayList<>();
        for (TextoListaParser.ItemParseado item : nuevosParseados) {
            DetalleListaEscolar detalle = new DetalleListaEscolar();
            detalle.setListaEscolar(lista);
            detalle.setTextoOriginal(item.getTextoOriginal());
            detalle.setCantidadSolicitada(item.getCantidad());
            detalle.setEstado("NO_COTIZADO");
            detalle.setOrden(++maxOrden);

            MatcherProductosService.ResultadoMatch match = matcherService.buscarMatch(item.getTextoOriginal(),
                    item.getCantidad());

            if (match.isTieneMatch()) {
                detalle.setProducto(match.getProductoPrincipal());
                detalle.setProductoNombreSnapshot(match.getProductoPrincipal().getNombre());
                detalle.setConfianzaMatch(BigDecimal.valueOf(match.getConfianza()));
                detalle.setMatchAutomatico(true);
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
                detalle.setNivelSeleccionado("MEDIO");
                detalle.setPrecioFinal(match.getPrecioMedio());
                detalle.setEstado("COTIZADO");
            }

            lista.getDetalles().add(detalle);
            nuevosDetalles.add(detalle);
        }

        lista.setItemsTotal(lista.getDetalles().size());
        lista.recalcularPendientes();
        recalcularTotales(lista);
        if (eraCompletada) {
            lista.setEstado("EN_PROCESO");
        }
        listaRepository.save(lista);

        log.info("Lista {}: {} items nuevos agregados{}", lista.getCodigoCompleto(), nuevosDetalles.size(),
                eraCompletada ? " (lista reabierta desde COMPLETADA)" : "");

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("success", true);
        resultado.put("agregados", nuevosDetalles.size());
        resultado.put("itemsTotal", lista.getItemsTotal());
        resultado.put("mensaje", nuevosDetalles.size() + " item(s) agregado(s) correctamente");
        return resultado;
    }

    /**
     * Agrega un producto específico directamente a la lista como ítem COTIZADO
     * listo para vender.
     * Útil cuando el cliente quiere agregar más unidades o productos nuevos sin
     * pasar por el parser OCR.
     * Si la lista estaba COMPLETADA la reactiva a EN_PROCESO.
     */
    @Transactional
    public DetalleListaEscolar agregarProductoDirecto(Long listaId, Long productoId, int cantidad, BigDecimal precio) {
        ListaEscolar lista = listaRepository.findByIdConDetalles(listaId)
                .orElseThrow(() -> new RuntimeException("Lista no encontrada: " + listaId));

        if ("CANCELADA".equals(lista.getEstado()) || "ELIMINADA".equals(lista.getEstado())) {
            throw new RuntimeException("No se puede agregar items a una lista en estado " + lista.getEstado());
        }

        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + productoId));

        if (cantidad < 1)
            cantidad = 1;
        if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
            precio = producto.getPrecioVenta() != null ? producto.getPrecioVenta() : BigDecimal.ZERO;
        }

        int maxOrden = lista.getDetalles().stream()
                .mapToInt(d -> d.getOrden() != null ? d.getOrden() : 0)
                .max().orElse(0);

        DetalleListaEscolar detalle = new DetalleListaEscolar();
        detalle.setListaEscolar(lista);
        detalle.setTextoOriginal((cantidad > 1 ? cantidad + " " : "") + producto.getNombre());
        detalle.setCantidadSolicitada(cantidad);
        detalle.setEstado("COTIZADO");
        detalle.setOrden(maxOrden + 1);
        detalle.setObservaciones("ITEM_ADICIONAL");

        // Asignar el producto a los tres niveles con el mismo precio elegido
        detalle.setProducto(producto);
        detalle.setProductoNombreSnapshot(producto.getNombre());
        detalle.setProductoEconomico(producto);
        detalle.setPrecioEconomico(precio);
        detalle.setProductoMedio(producto);
        detalle.setPrecioMedio(precio);
        detalle.setProductoPremium(producto);
        detalle.setPrecioPremium(precio);
        detalle.setNivelSeleccionado("MEDIO");
        detalle.setPrecioFinal(precio);

        detalle = detalleRepository.save(detalle);

        lista.getDetalles().add(detalle);
        lista.setItemsTotal(lista.getDetalles().size());
        lista.recalcularPendientes();
        recalcularTotales(lista);

        // Si estaba COMPLETADA, reactivar para que el POS permita vender los nuevos
        // items
        if ("COMPLETADA".equals(lista.getEstado())) {
            lista.setEstado("EN_PROCESO");
            log.info("Lista {} reactivada a EN_PROCESO por nuevo producto adicional", lista.getCodigoCompleto());
        }

        listaRepository.save(lista);

        log.info("Producto '{}' (x{} @ S/{}) agregado directamente a lista {}",
                producto.getNombre(), cantidad, precio, lista.getCodigoCompleto());
        return detalle;
    }

    /**
     * Normaliza texto para comparación de ítems (trim + minúsculas + espacios
     * normalizados).
     */
    private String normalizarParaComparar(String texto) {
        if (texto == null)
            return "";
        return texto.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    // =========================================================
    // ELIMINACIÓN LÓGICA DE LISTA
    // =========================================================

    /**
     * Elimina lógicamente una lista marcándola como ELIMINADA.
     * Solo se permite si la lista no tiene ventas realizadas.
     * Los datos permanecen en BD (soft delete) para auditoría.
     */
    @Transactional
    public void eliminarLista(Long listaId) {
        ListaEscolar lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista no encontrada: " + listaId));

        if (lista.getItemsVendidos() > 0) {
            throw new RuntimeException(
                    "No se puede eliminar una lista con ventas realizadas. " +
                            "La lista tiene " + lista.getItemsVendidos() + " item(s) vendido(s). " +
                            "Use 'Cancelar' en su lugar.");
        }

        lista.setEstado("ELIMINADA");
        listaRepository.save(lista);
        log.info("Lista {} marcada como ELIMINADA (soft delete)", lista.getCodigoCompleto());
    }

    /**
     * Acumula venta y costo de un detalle para un nivel específico.
     */
    private void acumularNivel(ResumenFinancieroDTO.NivelFinanciero nivel,
            Producto producto, BigDecimal precioVenta,
            int cantidad, Set<Long> productosSinCosto) {
        if (producto == null || precioVenta == null || precioVenta.compareTo(BigDecimal.ZERO) <= 0) {
            return; // Item no cotizado en este nivel
        }

        BigDecimal ventaItem = precioVenta.multiply(BigDecimal.valueOf(cantidad));
        nivel.setTotalVenta(nivel.getTotalVenta().add(ventaItem));
        nivel.setItemsCotizados(nivel.getItemsCotizados() + 1);

        // Costo: si precioCompra es null o 0, se trata como 0 pero se marca advertencia
        BigDecimal costoUnitario = producto.getPrecioCompra();
        if (costoUnitario == null || costoUnitario.compareTo(BigDecimal.ZERO) <= 0) {
            costoUnitario = BigDecimal.ZERO;
            productosSinCosto.add(producto.getId());
        }
        BigDecimal costoItem = costoUnitario.multiply(BigDecimal.valueOf(cantidad));
        nivel.setTotalCosto(nivel.getTotalCosto().add(costoItem));
    }
}
