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

    public VentaService(ProductoRepository productoRepository,
                        VentaRepository ventaRepository,
                        KardexRepository kardexRepository,
                        CorrelativoRepository correlativoRepository,
                        ClienteRepository clienteRepository,
                        AmortizacionRepository amortizacionRepository,
                        UsuarioRepository usuarioRepository,
                        CajaService cajaService,
                        FacturacionElectronicaService facturacionService,
                        ConfiguracionService configuracionService) {
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
    }

    /**
     * Crea una nueva venta con soporte DUAL-MODE:
     * - MODO INTERNO (facturaElectronicaActiva = false): Series I001/IF001, no envía a SUNAT
     * - MODO ELECTRÓNICO (facturaElectronicaActiva = true): Series B001/F001, envía automáticamente a SUNAT
     *
     * MEJORAS DE SEGURIDAD:
     * - Usa LOCK PESIMISTA para validación de stock (evita race conditions)
     * - Soporta múltiples métodos de pago (EFECTIVO, YAPE, PLIN, TARJETA, TRANSFERENCIA)
     * - Maneja clientes duplicados gracefully
     *
     * @param dto Datos de la venta
     * @return Map con id de la venta y estado SUNAT (si aplica)
     * @throws RuntimeException si hay error de stock o al procesar
     * @throws OptimisticLockingFailureException si hay conflicto de concurrencia en stock
     */
    @Transactional
    @Auditable(modulo = "VENTAS", accion = "CREAR", descripcion = "Registrar nueva venta")
    public Map<String, Object> crearVenta(VentaDTO dto) throws OptimisticLockingFailureException {

        // 1. VERIFICAR MODO DE FACTURACIÓN
        boolean facturaElectronicaActiva = facturacionService.isFacturacionElectronicaActiva();

        // 2. CLIENTE (con manejo de duplicados)
        Cliente cliente = obtenerOCrearCliente(dto);

        // 3. DETERMINAR TIPO Y SERIE SEGÚN MODO
        String tipo = dto.getTipoComprobante() != null ? dto.getTipoComprobante() : "NOTA_VENTA";
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
        venta.setClienteDenominacion(dto.getClienteNombre() != null ? dto.getClienteNombre() : cliente.getNombreRazonSocial());
        venta.setClienteNumeroDocumento(cliente.getNumeroDocumento());
        venta.setClienteTipoDocumento(cliente.getTipoDocumento());
        // Usar direccion del DTO (snapshot de lo que el usuario vio en pantalla, ej: desde SUNAT)
        // Si el DTO no tiene direccion, usar la del cliente guardado
        String direccionVenta = dto.getClienteDireccion() != null && !dto.getClienteDireccion().isBlank() ?
                dto.getClienteDireccion() : cliente.getDireccion();
        venta.setClienteDireccion(direccionVenta);
        venta.setTipoComprobante(tipo);
        venta.setSerie(serie);
        venta.setNumero(nuevoNumero);
        venta.setFechaEmision(LocalDate.now());
        venta.setEstado("EMITIDO");

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
        BigDecimal montoAbonado = procesarFormaPago(venta, dto, totalVenta);

        // 8. GUARDAR VENTA
        Venta ventaGuardada = ventaRepository.save(venta);

        // 9. REGISTRAR PAGO Y MOVIMIENTO DE CAJA
        if (montoAbonado.compareTo(BigDecimal.ZERO) > 0) {
            registrarPagoYCaja(ventaGuardada, montoAbonado, dto.getMetodoPago());
        }

        // 10. ENVÍO A SUNAT (SOLO EN MODO ELECTRÓNICO)
        String estadoSunat = "NO_APLICA";
        if (facturaElectronicaActiva && !tipo.equals("NOTA_VENTA")) {
            try {
                SunatResponseDTO respuestaSunat = facturacionService.enviarComprobanteSunat(ventaGuardada.getId());
                estadoSunat = respuestaSunat.getPayload() != null ?
                        respuestaSunat.getPayload().getEstado() : "ERROR";
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
                "estadoSunat", estadoSunat,
                "facturaElectronica", facturaElectronicaActiva
        );
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
                    if ((clienteExistente.getNombreRazonSocial() == null || clienteExistente.getNombreRazonSocial().isBlank())
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
                        c.setTipoDocumento(dto.getClienteDocumento().length() == Constants.RUC_LENGTH ?
                            Constants.TIPO_DOC_RUC : Constants.TIPO_DOC_DNI);
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
     * Procesa los detalles de venta: validación de stock, creación de detalles y kardex
     *
     * MEJORADO: Usa LOCK PESIMISTA para evitar race conditions en stock
     * Esto previene que dos ventas simultáneas vendan el mismo último ítem
     *
     * @return Array [totalVenta, totalGravada, totalIgv]
     */
    private BigDecimal[] procesarDetalles(Venta venta, VentaDTO dto) {
        BigDecimal totalVenta = BigDecimal.ZERO;
        BigDecimal totalGravada = BigDecimal.ZERO;
        BigDecimal totalIgv = BigDecimal.ZERO;

        // Obtener IGV configurable
        BigDecimal igvFactor = configuracionService.getIgvFactor();
        BigDecimal igvPorcentaje = configuracionService.getIgvPorcentaje();

        for (VentaDTO.DetalleDTO item : dto.getItems()) {
            // CRÍTICO: Usar findByIdWithLock para bloqueo pesimista
            // Esto evita que dos ventas simultáneas vendan el mismo último ítem
            Producto prod = productoRepository.findByIdWithLock(item.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: ID " + item.getProductoId()));

            // CONTROL DE PRECIOS: Validar que el precio sea autorizado
            validarPrecioVenta(prod, item.getPrecioVenta());

            // Validación estricta de Stock
            int cantidadRequerida = item.getCantidad().intValue();
            int stockDisponible = prod.getStockActual() != null ? prod.getStockActual() : 0;

            if (stockDisponible < cantidadRequerida) {
                throw new RuntimeException(String.format(
                        "Stock insuficiente para '%s'. Disponible: %d, Requerido: %d",
                        prod.getNombre(), stockDisponible, cantidadRequerida));
            }

            // Cálculos con IGV configurable
            BigDecimal precioFinal = item.getPrecioVenta();
            BigDecimal cantidad = item.getCantidad();
            BigDecimal subtotalItem = precioFinal.multiply(cantidad);
            BigDecimal valorUnitario = precioFinal.divide(igvFactor, 6, RoundingMode.HALF_UP);
            BigDecimal valorVenta = valorUnitario.multiply(cantidad);
            BigDecimal igvItem = subtotalItem.subtract(valorVenta);

            // Crear detalle
            DetalleVenta det = new DetalleVenta();
            det.setVenta(venta);
            det.setProducto(prod);
            det.setCantidad(cantidad);
            det.setDescripcion(prod.getNombre());
            det.setUnidadMedida(prod.getUnidadMedida() != null ? prod.getUnidadMedida() : "NIU");
            det.setPrecioUnitario(precioFinal);
            det.setValorUnitario(valorUnitario);
            det.setSubtotal(subtotalItem);
            det.setPorcentajeIgv(igvPorcentaje);
            det.setCodigoTipoAfectacionIgv(prod.getTipoAfectacionIgv() != null ?
                mapearTipoAfectacion(prod.getTipoAfectacionIgv()) : Constants.AFECTACION_GRAVADO);

            venta.getItems().add(det);

            // Acumular totales
            totalVenta = totalVenta.add(subtotalItem);
            totalGravada = totalGravada.add(valorVenta);
            totalIgv = totalIgv.add(igvItem);

            // Kardex (registrar ANTES de actualizar stock para tener stockAnterior correcto)
            registrarKardex(prod, cantidadRequerida, venta);

            // Actualizar Stock
            prod.setStockActual(stockDisponible - cantidadRequerida);
            productoRepository.save(prod);
        }

        return new BigDecimal[]{totalVenta, totalGravada, totalIgv};
    }

    /**
     * Registra movimiento en Kardex
     */
    private void registrarKardex(Producto prod, int cantidad, Venta venta) {
        Kardex k = new Kardex();
        k.setProducto(prod);
        k.setTipo("SALIDA");
        k.setMotivo("VENTA " + venta.getSerie() + "-" + venta.getNumero());
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
    private BigDecimal procesarFormaPago(Venta venta, VentaDTO dto, BigDecimal totalVenta) {
        BigDecimal montoAbonado;

        if ("CREDITO".equals(dto.getFormaPago())) {
            venta.setFormaPago("CREDITO");
            BigDecimal inicial = dto.getMontoInicial() != null ? dto.getMontoInicial() : BigDecimal.ZERO;
            montoAbonado = inicial;
            venta.setMontoPagado(inicial);
            venta.setSaldoPendiente(totalVenta.subtract(inicial));
            int dias = dto.getDiasCredito() != null ? dto.getDiasCredito() : Constants.DEFAULT_CREDIT_DAYS;
            venta.setFechaVencimiento(LocalDate.now().plusDays(dias));
        } else {
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
        amo.setObservacion("PAGO INICIAL / CONTADO - " + metodoPago);
        amortizacionRepository.save(amo);

        // Movimiento de caja - OBLIGATORIO: Si falla, debe abortar la transacción
        cajaService.registrarMovimiento("INGRESO",
                "VENTA " + venta.getSerie() + "-" + venta.getNumero() + " (" + metodoPago + ")",
                monto);
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

    /**
     * CONTROL DE PRECIOS: Verifica si el usuario actual tiene rol ADMIN.
     * Solo los administradores pueden modificar precios en el POS.
     *
     * @return true si el usuario tiene rol ADMIN, false en caso contrario
     */
    private boolean isUsuarioAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;

        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));
    }

    /**
     * CONTROL DE PRECIOS: Valida que el precio enviado sea válido.
     *
     * REGLAS DE SEGURIDAD:
     * 1. SOBREPRECIO: Si el precio enviado es MAYOR al precio real (con tolerancia de 0.01),
     *    se rechaza SIEMPRE, incluso para ADMIN. Esto previene fraude por sobrecobro.
     * 2. MODIFICACIÓN: Solo ADMIN puede modificar precios (hacia abajo, para descuentos)
     * 3. MÍNIMO: El precio no puede ser menor al precio mínimo configurado
     *
     * @param producto El producto a validar
     * @param precioEnviado El precio enviado desde el frontend
     * @throws RuntimeException si hay manipulación de precios no autorizada
     */
    private void validarPrecioVenta(Producto producto, BigDecimal precioEnviado) {
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

        // Si el precio está dentro del rango permitido (igual o menor con tolerancia), es válido
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
}
