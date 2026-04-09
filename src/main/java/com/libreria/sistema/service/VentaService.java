package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.SunatResponseDTO;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.*;
import com.libreria.sistema.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Servicio de Ventas OPTIMIZADO con:
 * - Lock Pesimista para evitar race conditions en stock
 * - Soporte para múltiples métodos de pago
 * - Manejo de clientes duplicados
 */
@Service
@Slf4j
public class VentaService {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final KardexRepository kardexRepository;
    private final CorrelativoRepository correlativoRepository;
    private final ClienteRepository clienteRepository;
    private final AmortizacionRepository amortizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CajaService cajaService;
    private final FacturacionElectronicaService facturacionService;
    private final ConfiguracionService configuracionService;
    private final ClienteService clienteService;

    public VentaService(ProductoRepository productoRepository,
            VentaRepository ventaRepository,
            KardexRepository kardexRepository,
            CorrelativoRepository correlativoRepository,
            ClienteRepository clienteRepository,
            AmortizacionRepository amortizacionRepository,
            UsuarioRepository usuarioRepository,
            CajaService cajaService,
            FacturacionElectronicaService facturacionService,
            ConfiguracionService configuracionService,
            ClienteService clienteService) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.kardexRepository = kardexRepository;
        this.correlativoRepository = correlativoRepository;
        this.clienteRepository = clienteRepository;
        this.amortizacionRepository = amortizacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.cajaService = cajaService;
        this.facturacionService = facturacionService;
        this.configuracionService = configuracionService;
        this.clienteService = clienteService;
    }

    /**
     * Crea una nueva venta con soporte DUAL-MODE:
     * - MODO INTERNO (facturaElectronicaActiva = false): Series I001/IF001, no
     * envía a SUNAT
     * - MODO ELECTRÓNICO (facturaElectronicaActiva = true): Series B001/F001, envía
     * automáticamente a SUNAT
     *
     * MEJORAS DE SEGURIDAD:
     * - Usa LOCK PESIMISTA para validación de stock (evita race conditions)
     * - Soporta múltiples métodos de pago (EFECTIVO, YAPE, PLIN, TARJETA,
     * TRANSFERENCIA)
     * - Maneja clientes duplicados gracefully
     *
     * @param dto Datos de la venta
     * @return Map con id de la venta y estado SUNAT (si aplica)
     * @throws RuntimeException                  si hay error de stock o al procesar
     * @throws OptimisticLockingFailureException si hay conflicto de concurrencia en
     *                                           stock
     */
    @Transactional
    @Auditable(modulo = "VENTAS", accion = "CREAR", descripcion = "Registrar nueva venta")
    public Map<String, Object> crearVenta(VentaDTO dto) throws OptimisticLockingFailureException {

        // 1. VERIFICAR MODO DE FACTURACIÓN
        boolean facturaElectronicaActiva = facturacionService.isFacturacionElectronicaActiva();

        // 2. CLIENTE (con manejo de duplicados)
        Cliente cliente = obtenerOCrearCliente(dto);
        boolean esApartado = dto.isEntregaAlFinal();

        // 3. DETERMINAR TIPO Y SERIE SEGÚN MODO
        String tipo = dto.getTipoComprobante() != null ? dto.getTipoComprobante() : "NOTA_VENTA";
        if (esApartado && !"NOTA_VENTA".equals(tipo)) {
            throw new RuntimeException("Los apartados deben registrarse como Nota de Venta hasta la entrega final.");
        }
        String serie = facturacionService.obtenerSerie(tipo, facturaElectronicaActiva);

        // 4. OBTENER CORRELATIVO CON LOCK PESIMISTA para evitar race conditions
        // Si la serie no existe, se crea automáticamente con ultimoNumero = 0
        Correlativo correlativo = correlativoRepository.findByCodigoAndSerieWithLock(tipo, serie)
                .orElseGet(() -> {
                    Correlativo nuevo = new Correlativo(tipo, serie, 0);
                    return correlativoRepository.save(nuevo);
                });

        // SAFE UNBOXING DEFENSIVO: Triple protección contra NPE
        // 1. El getter de Correlativo ya maneja null
        // 2. Verificación explícita aquí por seguridad adicional
        // 3. El campo está inicializado en la entidad
        Integer ultimoActual = correlativo.getUltimoNumero();
        int nuevoNumero = (ultimoActual != null ? ultimoActual : 0) + 1;
        correlativo.setUltimoNumero(nuevoNumero);
        correlativoRepository.save(correlativo);

        // 5. CREAR CABECERA VENTA
        Venta venta = new Venta();
        venta.setClienteEntity(cliente);
        venta.setClienteDenominacion(
                dto.getClienteNombre() != null ? dto.getClienteNombre() : cliente.getNombreRazonSocial());
        venta.setClienteNumeroDocumento(cliente.getNumeroDocumento());
        venta.setClienteTipoDocumento(cliente.getTipoDocumento());
        // Usar direccion del DTO (snapshot de lo que el usuario vio en pantalla, ej:
        // desde SUNAT)
        // Si el DTO no tiene direccion, usar la del cliente guardado
        String direccionVenta = dto.getClienteDireccion() != null && !dto.getClienteDireccion().isBlank()
                ? dto.getClienteDireccion()
                : cliente.getDireccion();
        venta.setClienteDireccion(direccionVenta);
        venta.setTipoComprobante(tipo);
        venta.setSerie(serie);
        venta.setNumero(nuevoNumero);
        venta.setFechaEmision(LocalDate.now());
        venta.setEstado(esApartado ? "APARTADO" : "EMITIDO");
        venta.setEntregaPendiente(esApartado);

        // NUEVO: Método de pago
        venta.setMetodoPago(dto.getMetodoPago());

        // Usuario actual
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);
        venta.setUsuario(usuario);

        // 6. PROCESAR DETALLES (con LOCK PESIMISTA en productos)
        BigDecimal[] totales = procesarDetalles(venta, dto);
        BigDecimal totalVenta = totales[0];
        BigDecimal totalGravada = totales[1];
        BigDecimal totalIgv = totales[2];

        venta.setTotal(totalVenta);
        venta.setTotalGravada(totalGravada);
        venta.setTotalIgv(totalIgv);

        // 7. FORMA DE PAGO
        BigDecimal montoAbonado = procesarFormaPago(venta, dto, totalVenta, cliente);

        // 8. GUARDAR VENTA
        Venta ventaGuardada = ventaRepository.save(venta);

        // 9. REGISTRAR PAGO Y MOVIMIENTO DE CAJA
        if (montoAbonado.compareTo(BigDecimal.ZERO) > 0) {
            registrarPagoYCaja(ventaGuardada, montoAbonado, dto.getMetodoPago());
        }

        if (cliente != null && cliente.getId() != null) {
            clienteService.recalcularSaldoDeudor(cliente.getId());
        }

        // 10. ENVÍO A SUNAT (SOLO EN MODO ELECTRÓNICO)
        String estadoSunat = "NO_APLICA";
        if (!esApartado && facturaElectronicaActiva && !tipo.equals("NOTA_VENTA")) {
            try {
                SunatResponseDTO respuestaSunat = facturacionService.enviarComprobanteSunat(ventaGuardada.getId());
                estadoSunat = respuestaSunat.getPayload() != null ? respuestaSunat.getPayload().getEstado() : "ERROR";
            } catch (Exception e) {
                log.error("Error al enviar comprobante a SUNAT. Venta ID: {}", ventaGuardada.getId(), e);
                estadoSunat = "ERROR_ENVIO";
            }
        }

        return Map.of(
                "id", ventaGuardada.getId(),
                "serie", ventaGuardada.getSerie(),
                "numero", ventaGuardada.getNumero(),
                "metodoPago", ventaGuardada.getMetodoPago(),
                "estadoVenta", ventaGuardada.getEstado(),
                "entregaPendiente", Boolean.TRUE.equals(ventaGuardada.getEntregaPendiente()),
                "estadoSunat", estadoSunat,
                "facturaElectronica", facturaElectronicaActiva);
    }

    /**
     * Obtiene un cliente existente o crea uno nuevo.
     * MEJORADO: Maneja duplicados gracefully (constraint violation)
     * MEJORADO: Actualiza datos del cliente si vienen de SUNAT (direccion, nombre)
     */
    private Cliente obtenerOCrearCliente(VentaDTO dto) {
        // Primero intentar buscar
        return clienteRepository.findByNumeroDocumento(dto.getClienteDocumento())
                .map(clienteExistente -> {
                    // Si el cliente existe pero faltan datos y el DTO los tiene, actualizar
                    boolean actualizado = false;

                    // Actualizar direccion si no tiene y el DTO si la tiene
                    if ((clienteExistente.getDireccion() == null || clienteExistente.getDireccion().isBlank())
                            && dto.getClienteDireccion() != null && !dto.getClienteDireccion().isBlank()) {
                        clienteExistente.setDireccion(dto.getClienteDireccion());
                        actualizado = true;
                    }

                    // Actualizar nombre/razon social si esta vacio y el DTO lo tiene
                    if ((clienteExistente.getNombreRazonSocial() == null
                            || clienteExistente.getNombreRazonSocial().isBlank())
                            && dto.getClienteNombre() != null && !dto.getClienteNombre().isBlank()) {
                        clienteExistente.setNombreRazonSocial(dto.getClienteNombre());
                        actualizado = true;
                    }

                    if (actualizado) {
                        log.info("Actualizando datos del cliente {} desde consulta SUNAT", dto.getClienteDocumento());
                        return clienteRepository.save(clienteExistente);
                    }
                    return clienteExistente;
                })
                .orElseGet(() -> {
                    try {
                        // Intentar crear nuevo cliente
                        Cliente c = new Cliente();
                        c.setNumeroDocumento(dto.getClienteDocumento());
                        c.setNombreRazonSocial(dto.getClienteNombre());
                        c.setDireccion(dto.getClienteDireccion());
                        c.setTelefono(dto.getClienteTelefono());
                        c.setTipoDocumento(
                                dto.getClienteDocumento().length() == Constants.RUC_LENGTH ? Constants.TIPO_DOC_RUC
                                        : Constants.TIPO_DOC_DNI);
                        return clienteRepository.save(c);
                    } catch (DataIntegrityViolationException e) {
                        // Race condition: otro proceso creó el cliente
                        // Buscar nuevamente y retornar
                        log.warn("Cliente duplicado detectado, recuperando existente: {}", dto.getClienteDocumento());
                        return clienteRepository.findByNumeroDocumento(dto.getClienteDocumento())
                                .orElseThrow(() -> new RuntimeException("Error al obtener/crear cliente"));
                    }
                });
    }

    /**
     * Procesa los detalles de venta: validación de stock, creación de detalles y
     * kardex
     *
     * MEJORADO: Usa LOCK PESIMISTA para evitar race conditions en stock
     * Esto previene que dos ventas simultáneas vendan el mismo último ítem
     *
     * @return Array [totalVenta, totalGravada, totalIgv]
     */
    // =========================================================
    // ACTUALIZAR SOLO ESTE MÉTODO PRIVADO EN VentaService.java
    // =========================================================
    private BigDecimal[] procesarDetalles(Venta venta, VentaDTO dto) {
        BigDecimal totalVenta = BigDecimal.ZERO;
        BigDecimal totalGravada = BigDecimal.ZERO;
        BigDecimal totalIgv = BigDecimal.ZERO;

        BigDecimal igvFactor = configuracionService.getIgvFactor();
        BigDecimal igvPorcentaje = configuracionService.getIgvPorcentaje();

        for (VentaDTO.DetalleDTO item : dto.getItems()) {
            // Mantenemos tu Lock Pesimista (¡Muy bien implementado!)
            Producto prod = productoRepository.findByIdWithLock(item.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: ID " + item.getProductoId()));

            if (prod.esInsumo()) {
                throw new RuntimeException("El producto '" + prod.getNombre() + "' está marcado como insumo y no puede venderse desde el POS.");
            }

            // Mantenemos tu validación de seguridad de precios
            validarPrecioVenta(prod, item.getPrecioVenta());

            // --- NUEVA LÓGICA AGREGADA AQUÍ ---
            // Verificamos si es un servicio basándonos en tu modelo Producto.java (campo
            // 'tipo')
            // Asumimos que en BD guardas "SERVICIO" o "PRODUCTO" en ese campo.
            boolean esServicio = prod.getTipo() != null && "SERVICIO".equalsIgnoreCase(prod.getTipo());

            int cantidadRequerida = item.getCantidad().intValue();
            int stockDisponible = prod.getStockActual() != null ? prod.getStockActual() : 0;

            // SOLO validamos stock si NO es un servicio
            if (!esServicio) {
                if (stockDisponible < cantidadRequerida) {
                    throw new RuntimeException(String.format(
                            "Stock insuficiente para '%s'. Disponible: %d, Requerido: %d",
                            prod.getNombre(), stockDisponible, cantidadRequerida));
                }
            }
            // ----------------------------------

            // Cálculos matemáticos
            // CRÍTICO-2 FIX: IGV calculado directamente (no por diferencia) para evitar
            // acumulación de errores de redondeo que generan descuadres en declaraciones
            // SUNAT.
            BigDecimal precioFinal = item.getPrecioVenta();
            BigDecimal cantidad = item.getCantidad();
            BigDecimal subtotalItem = precioFinal.multiply(cantidad);
            // IGV = subtotal × (18/118) — nunca por diferencia
            BigDecimal igvItem = subtotalItem.multiply(new BigDecimal("18")).divide(new BigDecimal("118"), 2,
                    RoundingMode.HALF_UP);
            BigDecimal valorVenta = subtotalItem.subtract(igvItem);
            BigDecimal valorUnitario = valorVenta.divide(cantidad, 6, RoundingMode.HALF_UP);

            // Creación del detalle (Mantenemos tu lógica original)
            DetalleVenta det = new DetalleVenta();
            det.setVenta(venta);
            det.setProducto(prod);
            det.setCantidad(cantidad);
            det.setDescripcion(item.getDescripcion() != null && !item.getDescripcion().isBlank()
                    ? item.getDescripcion().trim()
                    : prod.getNombre());
            det.setUnidadMedida(prod.getUnidadMedida() != null ? prod.getUnidadMedida() : "NIU");
            det.setPrecioUnitario(precioFinal);
            det.setValorUnitario(valorUnitario);
            det.setSubtotal(subtotalItem);

            // Congelar costo al momento de la venta
            BigDecimal costoUnit = prod.getPrecioCompra() != null ? prod.getPrecioCompra() : BigDecimal.ZERO;
            det.setCostoUnitario(costoUnit);
            det.setUtilidadUnitaria(precioFinal.subtract(costoUnit));
            det.setUtilidadTotal(precioFinal.subtract(costoUnit).multiply(cantidad));

            det.setPorcentajeIgv(igvPorcentaje);

            // Corrección segura para mapear la afectación sin perder lógica
            String codigoAfectacion = Constants.AFECTACION_GRAVADO; // Valor por defecto
            if (prod.getTipoAfectacionIgv() != null) {
                codigoAfectacion = mapearTipoAfectacion(prod.getTipoAfectacionIgv());
            }
            det.setCodigoTipoAfectacionIgv(codigoAfectacion);

            venta.getItems().add(det);

            // Acumuladores
            totalVenta = totalVenta.add(subtotalItem);
            totalGravada = totalGravada.add(valorVenta);
            totalIgv = totalIgv.add(igvItem);

            // --- LÓGICA KARDEX Y STOCK ---
            // Si es servicio, NO descontamos stock, pero opcionalmente registramos Kardex
            // informativo
            // o simplemente no hacemos nada. Aquí asumo que quieres registrar la venta pero
            // no mover stock.
            if (!esServicio) {
                registrarKardex(prod, cantidadRequerida, venta);
                prod.setStockActual(stockDisponible - cantidadRequerida);
                productoRepository.save(prod);
            }
        }

        return new BigDecimal[] { totalVenta, totalGravada, totalIgv };
    }

    /**
     * Registra movimiento en Kardex
     */
    private void registrarKardex(Producto prod, int cantidad, Venta venta) {
        Kardex k = new Kardex();
        k.setProducto(prod);
        k.setTipo("SALIDA");
        String motivo = Boolean.TRUE.equals(venta.getEntregaPendiente())
                ? "APARTADO " + venta.getSerie() + "-" + venta.getNumero()
                : "VENTA " + venta.getSerie() + "-" + venta.getNumero();
        k.setMotivo(motivo);
        k.setCantidad(cantidad);
        k.setStockAnterior(prod.getStockActual());
        k.setStockActual(prod.getStockActual() - cantidad);
        kardexRepository.save(k);
    }

    /**
     * Procesa la forma de pago (Contado/Crédito)
     *
     * @return Monto abonado
     */
    private BigDecimal procesarFormaPago(Venta venta, VentaDTO dto, BigDecimal totalVenta, Cliente cliente) {
        BigDecimal montoAbonado;

        if ("CREDITO".equals(dto.getFormaPago())) {
            venta.setFormaPago("CREDITO");
            BigDecimal inicial = dto.getMontoInicial() != null ? dto.getMontoInicial() : BigDecimal.ZERO;
            if (inicial.compareTo(totalVenta) > 0) {
                throw new RuntimeException("El monto inicial no puede exceder el total de la operación.");
            }
            montoAbonado = inicial;
            venta.setMontoPagado(inicial);
            venta.setSaldoPendiente(totalVenta.subtract(inicial));
            int dias = resolverDiasCredito(dto, cliente);
            venta.setFechaVencimiento(LocalDate.now().plusDays(dias));

            if (Boolean.TRUE.equals(venta.getEntregaPendiente())) {
                venta.setEstado(venta.getSaldoPendiente().compareTo(BigDecimal.ZERO) == 0
                        ? "LISTO_ENTREGA"
                        : "APARTADO");
            } else {
                if (venta.getSaldoPendiente().compareTo(BigDecimal.ZERO) > 0) {
                    validarCreditoInmediato(cliente, venta.getSaldoPendiente());
                    venta.setEstado("EMITIDO");
                } else {
                    venta.setEstado("PAGADO_TOTAL");
                }
            }
        } else {
            if (dto.isEntregaAlFinal()) {
                throw new RuntimeException("El apartado debe registrarse como crédito para permitir pagos parciales.");
            }
            venta.setFormaPago("CONTADO");
            venta.setMontoPagado(totalVenta);
            venta.setSaldoPendiente(BigDecimal.ZERO);
            venta.setFechaVencimiento(LocalDate.now());
            montoAbonado = totalVenta;
        }

        return montoAbonado;
    }

    /**
     * Registra el pago inicial y el movimiento de caja
     * MEJORADO: Ahora incluye el método de pago en la amortización
     */
    private void registrarPagoYCaja(Venta venta, BigDecimal monto, String metodoPago) {
        // Amortización con método de pago
        Amortizacion amo = new Amortizacion();
        amo.setVenta(venta);
        amo.setMonto(monto);
        amo.setMetodoPago(metodoPago != null ? metodoPago : "EFECTIVO");
        amo.setObservacion(Boolean.TRUE.equals(venta.getEntregaPendiente())
                ? "ADELANTO APARTADO - " + metodoPago
                : "PAGO INICIAL / CONTADO - " + metodoPago);
        amortizacionRepository.save(amo);

        // Movimiento de caja - OBLIGATORIO: Si falla, debe abortar la transacción
        String concepto = Boolean.TRUE.equals(venta.getEntregaPendiente())
                ? "ADELANTO APARTADO " + venta.getSerie() + "-" + venta.getNumero() + " (" + metodoPago + ")"
                : "VENTA " + venta.getSerie() + "-" + venta.getNumero() + " (" + metodoPago + ")";
        String categoria = Boolean.TRUE.equals(venta.getEntregaPendiente())
                ? CategoriaMovimiento.COBRANZA
                : CategoriaMovimiento.VENTA;
        cajaService.registrarMovimiento("INGRESO", concepto, monto, categoria);
    }

    /**
     * Mapea tipo de afectación de texto a código SUNAT
     */
    private String mapearTipoAfectacion(String tipoAfectacion) {
        return switch (tipoAfectacion.toUpperCase()) {
            case "GRAVADO" -> Constants.AFECTACION_GRAVADO;
            case "EXONERADO" -> Constants.AFECTACION_EXONERADO;
            case "INAFECTO" -> Constants.AFECTACION_INAFECTO;
            default -> Constants.AFECTACION_GRAVADO;
        };
    }

    /**
     * Verifica si la facturación electrónica está activa
     */
    public boolean isFacturacionElectronicaActiva() {
        return facturacionService.isFacturacionElectronicaActiva();
    }

    private int resolverDiasCredito(VentaDTO dto, Cliente cliente) {
        if (dto.getDiasCredito() != null && dto.getDiasCredito() > 0) {
            return dto.getDiasCredito();
        }
        if (cliente != null && cliente.getDiasCredito() != null && cliente.getDiasCredito() > 0) {
            return cliente.getDiasCredito();
        }
        return Constants.DEFAULT_CREDIT_DAYS;
    }

    private void validarCreditoInmediato(Cliente cliente, BigDecimal saldoPendiente) {
        if (cliente == null || cliente.getId() == null) {
            throw new RuntimeException("Debe seleccionar un cliente registrado para ventas a crédito con entrega inmediata.");
        }
        if (!cliente.isTieneCredito()) {
            throw new RuntimeException("El cliente no tiene crédito habilitado para entregar mercadería antes del pago total.");
        }
        if (!cliente.puedeRecibirCredito(saldoPendiente)) {
            BigDecimal disponible = cliente.getCreditoDisponible();
            throw new RuntimeException("El cliente excede su límite de crédito. Disponible: S/ " +
                    disponible.setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * CONTROL DE PRECIOS: Verifica si el usuario actual tiene rol ADMIN.
     * Solo los administradores pueden modificar precios en el POS.
     *
     * @return true si el usuario tiene rol ADMIN, false en caso contrario
     */
    private boolean isUsuarioAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            return false;

        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));
    }

    /**
     * CONTROL DE PRECIOS: Valida que el precio enviado sea válido.
     *
     * REGLAS DE SEGURIDAD:
     * 1. SOBREPRECIO: Si el precio enviado es MAYOR al precio real (con tolerancia
     * de 0.01),
     * se rechaza SIEMPRE, incluso para ADMIN. Esto previene fraude por sobrecobro.
     * 2. MODIFICACIÓN: Solo ADMIN puede modificar precios (hacia abajo, para
     * descuentos)
     * 3. MÍNIMO: El precio no puede ser menor al precio mínimo configurado
     *
     * @param producto      El producto a validar
     * @param precioEnviado El precio enviado desde el frontend
     * @throws RuntimeException si hay manipulación de precios no autorizada
     */
    private void validarPrecioVenta(Producto producto, BigDecimal precioEnviado) {
        boolean esServicioGenericoVariable =
                producto.getTipo() != null
                        && "SERVICIO".equalsIgnoreCase(producto.getTipo())
                        && ("SERV-001".equalsIgnoreCase(producto.getCodigoInterno())
                        || "SERV-PERS-001".equalsIgnoreCase(producto.getCodigoInterno()));

        // SERV-001 y SERV-PERS-001 son servicios con precio variable.
        // Su precio es variable por diseño y no debe quedar sujeto al control
        // de sobreprecio de productos con tarifa fija.
        if (esServicioGenericoVariable) {
            return;
        }

        BigDecimal precioVenta = producto.getPrecioVenta();
        BigDecimal precioMinimo = precioVenta.multiply(Constants.DESCUENTO_MINIMO_VENTA);

        // Tolerancia técnica para errores de redondeo (0.01)
        BigDecimal tolerancia = new BigDecimal("0.01");
        BigDecimal precioMaximoPermitido = precioVenta.add(tolerancia);

        // ============================================================
        // VALIDACIÓN 1: DETECCIÓN DE SOBREPRECIO (CRÍTICA - SIEMPRE)
        // Si el precio enviado es MAYOR al precio real + tolerancia,
        // es un intento de sobrecobro/fraude. RECHAZAR SIEMPRE.
        // ============================================================
        if (precioEnviado.compareTo(precioMaximoPermitido) > 0) {
            String mensaje = String.format(
                    "ALERTA DE SEGURIDAD: Intento de sobreprecio detectado en el producto '%s'. " +
                            "Precio real: S/ %.2f, Precio enviado: S/ %.2f. Operación bloqueada.",
                    producto.getNombre(), precioVenta, precioEnviado);

            log.error("!!! {} - Usuario: {}", mensaje,
                    SecurityContextHolder.getContext().getAuthentication().getName());

            throw new RuntimeException(mensaje);
        }

        // Si el precio está dentro del rango permitido (igual o menor con tolerancia),
        // es válido
        if (precioEnviado.subtract(precioVenta).abs().compareTo(tolerancia) <= 0) {
            return; // Precio es igual (con tolerancia de redondeo)
        }

        // ============================================================
        // VALIDACIÓN 2: MODIFICACIÓN DE PRECIO (SOLO ADMIN)
        // Si el precio fue modificado hacia abajo (descuento), solo ADMIN puede hacerlo
        // ============================================================
        if (!isUsuarioAdmin()) {
            log.warn("ALERTA SEGURIDAD: Usuario no-admin intentó modificar precio. " +
                    "Producto: {}, Precio Original: {}, Precio Enviado: {}, Usuario: {}",
                    producto.getNombre(), precioVenta, precioEnviado,
                    SecurityContextHolder.getContext().getAuthentication().getName());

            throw new RuntimeException(String.format(
                    "No autorizado: Solo administradores pueden modificar precios. " +
                            "Producto '%s', precio correcto: S/ %.2f",
                    producto.getNombre(), precioVenta));
        }

        // ============================================================
        // VALIDACIÓN 3: PRECIO MÍNIMO (PARA ADMIN)
        // ADMIN puede dar descuentos, pero no por debajo del mínimo
        // ============================================================
        if (precioEnviado.compareTo(precioMinimo) < 0) {
            log.warn("Precio por debajo del mínimo permitido. Producto: {}, Mínimo: {}, Enviado: {}",
                    producto.getNombre(), precioMinimo, precioEnviado);
            throw new RuntimeException(String.format(
                    "Precio inválido para '%s'. Mínimo permitido: S/ %.2f",
                    producto.getNombre(), precioMinimo));
        }

        log.info("Precio modificado por ADMIN. Producto: {}, Original: {}, Nuevo: {}, Usuario: {}",
                producto.getNombre(), precioVenta, precioEnviado,
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    // =====================================================
    // ANULACIÓN DE VENTAS CON COMUNICACIÓN DE BAJA SUNAT
    // =====================================================

    /**
     * Anula una venta y envía Comunicación de Baja a SUNAT si aplica.
     *
     * POLÍTICA "NO DAÑAR":
     * - Si SUNAT falla, la anulación LOCAL sigue adelante
     * - El error de SUNAT se registra en la venta para reintento posterior
     * - No se bloquea la operación por problemas de red/SUNAT
     *
     * LÓGICA CONDICIONAL (según auditoría):
     * - SI (venta.tipo == FACTURA o BOLETA)
     * - Y (configuracion.isFacturacionElectronicaActiva())
     * - Y (venta.getSunatEstado() == ACEPTADO)
     * - ENTONCES: Ejecuta llamada a API de baja
     *
     * @param ventaId ID de la venta a anular
     * @param motivo  Motivo de la anulación
     * @return Map con resultado de la operación
     */
    @Transactional
    @Auditable(modulo = "VENTAS", accion = "ANULAR", descripcion = "Anular venta")
    public Map<String, Object> anularVenta(Long ventaId, String motivo) {
        Map<String, Object> resultado = new HashMap<>();

        // 1. Obtener la venta
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada: " + ventaId));

        // 2. Validar que no esté ya anulada
        if ("ANULADO".equals(venta.getEstado())) {
            throw new RuntimeException("La venta ya está anulada");
        }

        // 3. Validar que no tenga devoluciones
        if ("DEVUELTO_TOTAL".equals(venta.getEstado()) || "DEVUELTO_PARCIAL".equals(venta.getEstado())) {
            throw new RuntimeException("No se puede anular una venta con devoluciones. Use el módulo de devoluciones.");
        }

        // 4. INTENTAR COMUNICACIÓN DE BAJA A SUNAT (si aplica)
        String estadoSunatBaja = "NO_APLICA";
        String mensajeSunat = "";
        boolean errorSunat = false;

        try {
            // LÓGICA CONDICIONAL según auditoría:
            // Solo si es BOLETA/FACTURA, facturación activa, y fue ACEPTADO
            boolean esComprobanteElectronico = "BOLETA".equals(venta.getTipoComprobante())
                    || "FACTURA".equals(venta.getTipoComprobante());
            boolean facturaActiva = facturacionService.isFacturacionElectronicaActiva();
            boolean fueAceptado = "ACEPTADO".equals(venta.getSunatEstado());

            if (esComprobanteElectronico && facturaActiva && fueAceptado) {
                log.info("Enviando comunicación de baja a SUNAT para venta {}-{}", venta.getSerie(), venta.getNumero());

                Map<String, Object> respuestaBaja = facturacionService.enviarComunicacionBaja(venta, motivo);
                estadoSunatBaja = (String) respuestaBaja.get("estado");
                mensajeSunat = (String) respuestaBaja.get("mensaje");
                errorSunat = !Boolean.TRUE.equals(respuestaBaja.get("success"));

                if (errorSunat) {
                    log.warn("Error en comunicación de baja SUNAT: {}. La anulación local continuará.", mensajeSunat);
                    // Marcar para reintento posterior
                    venta.setSunatMensajeError("ERROR_BAJA: " + mensajeSunat);
                }
            }
        } catch (Exception e) {
            // POLÍTICA "NO DAÑAR": El error de SUNAT NO bloquea la anulación local
            log.error("Excepción en comunicación de baja SUNAT (anulación local continuará): {}", e.getMessage(), e);
            errorSunat = true;
            mensajeSunat = "Excepción: " + e.getMessage();
            venta.setSunatMensajeError("ERROR_BAJA_EXCEPTION: " + e.getMessage());
        }

        // 5. ANULACIÓN LOCAL (siempre se ejecuta, independiente de SUNAT)
        venta.setEstado("ANULADO");

        // 6. Devolver stock al inventario (si tiene items)
        if (venta.getItems() != null) {
            for (DetalleVenta detalle : venta.getItems()) {
                Producto producto = detalle.getProducto();
                if (producto != null) {
                    // Solo si no es servicio
                    boolean esServicio = producto.getTipo() != null && "SERVICIO".equalsIgnoreCase(producto.getTipo());
                    if (!esServicio) {
                        int cantidadVendida = detalle.getCantidad().intValue();

                        // Registrar en Kardex
                        Kardex kardex = new Kardex();
                        kardex.setProducto(producto);
                        kardex.setTipo("INGRESO");
                        kardex.setMotivo("ANULACIÓN VENTA " + venta.getSerie() + "-" + venta.getNumero());
                        kardex.setCantidad(cantidadVendida);
                        kardex.setStockAnterior(producto.getStockActual());
                        kardex.setStockActual(producto.getStockActual() + cantidadVendida);
                        kardexRepository.save(kardex);

                        // Actualizar stock
                        producto.setStockActual(producto.getStockActual() + cantidadVendida);
                        productoRepository.save(producto);
                    }
                }
            }
        }

        // 7. Registrar egreso en caja si fue pagada (revertir el ingreso)
        // FIX ERROR-10: usar CategoriaMovimiento.DEVOLUCION en lugar de OTRO_EGRESO
        // para
        // que los reportes financieros distingan correctamente anulaciones de otros
        // egresos.
        if (venta.getMontoPagado() != null && venta.getMontoPagado().compareTo(BigDecimal.ZERO) > 0) {
            try {
                String concepto = "ANULACIÓN VENTA " + venta.getSerie() + "-" + venta.getNumero();
                cajaService.registrarMovimiento("EGRESO", concepto, venta.getMontoPagado(),
                        CategoriaMovimiento.DEVOLUCION);
            } catch (Exception e) {
                // Si la caja está cerrada, no podemos registrar pero no bloqueamos la anulación
                log.warn("No se pudo registrar egreso por anulación (¿caja cerrada?): {}", e.getMessage());
                resultado.put("alertaCaja", "No se pudo registrar egreso en caja: " + e.getMessage());
            }
        }

        // 8. Guardar cambios
        ventaRepository.save(venta);

        if (venta.getClienteEntity() != null && venta.getClienteEntity().getId() != null) {
            clienteService.recalcularSaldoDeudor(venta.getClienteEntity().getId());
        }

        // 9. Preparar resultado
        resultado.put("success", true);
        resultado.put("ventaId", venta.getId());
        resultado.put("estadoVenta", venta.getEstado());
        resultado.put("estadoSunatBaja", estadoSunatBaja);
        resultado.put("mensajeSunat", mensajeSunat);
        resultado.put("errorSunat", errorSunat);

        if (errorSunat) {
            resultado.put("advertencia", "La venta fue anulada localmente pero hubo un error con SUNAT. " +
                    "Reintente la comunicación de baja más tarde.");
        }

        log.info("Venta {}-{} anulada. Estado SUNAT baja: {}", venta.getSerie(), venta.getNumero(), estadoSunatBaja);

        return resultado;
    }

    @Transactional
    @Auditable(modulo = "VENTAS", accion = "MODIFICAR", descripcion = "Entregar apartado pagado")
    public Map<String, Object> entregarApartado(Long ventaId) {
        Venta venta = ventaRepository.findByIdWithDetalles(ventaId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada: " + ventaId));

        if (!Boolean.TRUE.equals(venta.getEntregaPendiente())) {
            throw new RuntimeException("Esta operación no tiene entrega pendiente.");
        }
        if ("ANULADO".equals(venta.getEstado())) {
            throw new RuntimeException("No se puede entregar una venta anulada.");
        }
        if (venta.getSaldoPendiente() != null && venta.getSaldoPendiente().compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("No se puede entregar mientras exista saldo pendiente.");
        }

        venta.setEntregaPendiente(false);
        venta.setFechaEntregaReal(java.time.LocalDateTime.now());
        venta.setFechaEmision(LocalDate.now());
        venta.setEstado("PAGADO_TOTAL");
        ventaRepository.save(venta);

        if (venta.getClienteEntity() != null && venta.getClienteEntity().getId() != null) {
            clienteService.recalcularSaldoDeudor(venta.getClienteEntity().getId());
        }

        return Map.of(
                "id", venta.getId(),
                "serie", venta.getSerie(),
                "numero", venta.getNumero(),
                "estado", venta.getEstado());
    }
}
