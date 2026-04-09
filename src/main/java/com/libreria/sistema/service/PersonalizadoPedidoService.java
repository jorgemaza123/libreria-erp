package com.libreria.sistema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libreria.sistema.model.AdicionalPersonalizado;
import com.libreria.sistema.model.Cliente;
import com.libreria.sistema.model.InsumoPersonalizado;
import com.libreria.sistema.model.PedidoPersonalizado;
import com.libreria.sistema.model.PedidoPersonalizadoComponente;
import com.libreria.sistema.model.PedidoPersonalizadoItem;
import com.libreria.sistema.model.PlantillaComponentePersonalizado;
import com.libreria.sistema.model.PlantillaPersonalizada;
import com.libreria.sistema.model.PlantillaRangoPrecio;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.model.ZonaEntregaPersonalizado;
import com.libreria.sistema.model.dto.PedidoPersonalizadoDTO;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.AdicionalPersonalizadoRepository;
import com.libreria.sistema.repository.ClienteRepository;
import com.libreria.sistema.repository.InsumoPersonalizadoRepository;
import com.libreria.sistema.repository.PedidoPersonalizadoRepository;
import com.libreria.sistema.repository.PlantillaPersonalizadaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.VentaRepository;
import com.libreria.sistema.repository.ZonaEntregaPersonalizadoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class PersonalizadoPedidoService {

    private static final String SERVICIO_PERSONALIZADO_CODE = "SERV-PERS-001";
    private static final DateTimeFormatter PEDIDO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PedidoPersonalizadoRepository pedidoRepository;
    private final PlantillaPersonalizadaRepository plantillaRepository;
    private final InsumoPersonalizadoRepository insumoRepository;
    private final AdicionalPersonalizadoRepository adicionalRepository;
    private final ZonaEntregaPersonalizadoRepository zonaRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final VentaService ventaService;
    private final ConfiguracionService configuracionService;
    private final ObjectMapper objectMapper;

    public PersonalizadoPedidoService(PedidoPersonalizadoRepository pedidoRepository,
                                      PlantillaPersonalizadaRepository plantillaRepository,
                                      InsumoPersonalizadoRepository insumoRepository,
                                      AdicionalPersonalizadoRepository adicionalRepository,
                                      ZonaEntregaPersonalizadoRepository zonaRepository,
                                      ClienteRepository clienteRepository,
                                      ProductoRepository productoRepository,
                                      VentaRepository ventaRepository,
                                      VentaService ventaService,
                                      ConfiguracionService configuracionService,
                                      ObjectMapper objectMapper) {
        this.pedidoRepository = pedidoRepository;
        this.plantillaRepository = plantillaRepository;
        this.insumoRepository = insumoRepository;
        this.adicionalRepository = adicionalRepository;
        this.zonaRepository = zonaRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.ventaService = ventaService;
        this.configuracionService = configuracionService;
        this.objectMapper = objectMapper;
    }

    public List<PedidoPersonalizado> listarPedidos(String termino, String estado) {
        return pedidoRepository.buscar(normalizarTexto(termino), normalizarTexto(estado));
    }

    public Optional<PedidoPersonalizado> obtenerDetalle(Long id) {
        return pedidoRepository.findDetalleById(id);
    }

    public PedidoPersonalizadoDTO construirDto(Long id) {
        if (id == null) {
            PedidoPersonalizadoDTO dto = new PedidoPersonalizadoDTO();
            dto.setCanalOrigen("TIENDA");
            dto.setEstado("BORRADOR");
            dto.setModoEntrega("RECOJO_TIENDA");
            dto.setEnvioGratis(false);
            dto.setCostoEnvio(BigDecimal.ZERO);
            dto.setDescuento(BigDecimal.ZERO);
            dto.setAdelanto(BigDecimal.ZERO);
            dto.setFormaPago("CONTADO");
            dto.setMetodoPago("EFECTIVO");
            dto.setTipoComprobante("NOTA_VENTA");
            return dto;
        }

        PedidoPersonalizado pedido = pedidoRepository.findDetalleById(id)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
        PedidoPersonalizadoDTO dto = new PedidoPersonalizadoDTO();
        dto.setId(pedido.getId());
        dto.setCanalOrigen(pedido.getCanalOrigen());
        dto.setEstado(pedido.getEstado());
        dto.setClienteId(pedido.getCliente() != null ? pedido.getCliente().getId() : null);
        dto.setClienteNombre(pedido.getClienteNombre());
        dto.setClienteTipoDocumento(pedido.getClienteTipoDocumento());
        dto.setClienteNumeroDocumento(pedido.getClienteNumeroDocumento());
        dto.setClienteWhatsapp(pedido.getClienteWhatsapp());
        dto.setClienteTelefono(pedido.getClienteTelefono());
        dto.setClienteEmail(pedido.getClienteEmail());
        dto.setNombreDestinatario(pedido.getNombreDestinatario());
        dto.setTelefonoDestinatario(pedido.getTelefonoDestinatario());
        dto.setDedicatoria(pedido.getDedicatoria());
        dto.setNombreEtiqueta(pedido.getNombreEtiqueta());
        dto.setNotasDiseno(pedido.getNotasDiseno());
        dto.setObservacionesCliente(pedido.getObservacionesCliente());
        dto.setModoEntrega(pedido.getModoEntrega());
        dto.setEnvioGratis(pedido.getEnvioGratis());
        dto.setCostoEnvio(pedido.getCostoEnvio());
        dto.setDepartamento(pedido.getDepartamento());
        dto.setProvincia(pedido.getProvincia());
        dto.setDistrito(pedido.getDistrito());
        dto.setDireccionEntrega(pedido.getDireccionEntrega());
        dto.setReferenciaEntrega(pedido.getReferenciaEntrega());
        dto.setFechaEntregaSolicitada(pedido.getFechaEntregaSolicitada() != null ? pedido.getFechaEntregaSolicitada().toString() : null);
        dto.setFranjaEntrega(pedido.getFranjaEntrega());
        dto.setDescuento(pedido.getDescuento());
        dto.setAdelanto(pedido.getAdelanto());
        dto.setObservacionesInternas(pedido.getObservacionesInternas());
        dto.setFormaPago(pedido.getSaldo() != null && pedido.getSaldo().compareTo(BigDecimal.ZERO) > 0 ? "CREDITO" : "CONTADO");
        dto.setMetodoPago("EFECTIVO");
        dto.setTipoComprobante("NOTA_VENTA");

        List<PedidoPersonalizadoDTO.ItemDTO> items = new ArrayList<>();
        for (PedidoPersonalizadoItem item : pedido.getItems()) {
            PedidoPersonalizadoDTO.ItemDTO itemDto = new PedidoPersonalizadoDTO.ItemDTO();
            itemDto.setPlantillaId(item.getPlantilla() != null ? item.getPlantilla().getId() : null);
            itemDto.setCodigoModeloSnapshot(item.getCodigoModeloSnapshot());
            itemDto.setNombreComercialSnapshot(item.getNombreComercialSnapshot());
            itemDto.setFotoSnapshot(item.getFotoSnapshot());
            itemDto.setCategoriaSnapshot(item.getCategoriaSnapshot());
            itemDto.setCantidad(item.getCantidad());
            itemDto.setCostoSnapshot(item.getCostoSnapshot());
            itemDto.setPrecioMinimoSnapshot(item.getPrecioMinimoSnapshot());
            itemDto.setPrecioSugeridoSnapshot(item.getPrecioSugeridoSnapshot());
            itemDto.setPrecioFinal(item.getPrecioFinal());
            itemDto.setConfiguracionJson(item.getConfiguracionJson());
            itemDto.setComponentes(item.getComponentes().stream()
                    .sorted(Comparator.comparing(PedidoPersonalizadoComponente::getOrden))
                    .map(this::mapearComponente)
                    .toList());
            items.add(itemDto);
        }
        dto.setItems(items);
        return dto;
    }

    @Transactional
    public PedidoPersonalizado guardar(PedidoPersonalizadoDTO dto) {
        PedidoPersonalizado pedido = dto.getId() != null
                ? pedidoRepository.findDetalleById(dto.getId()).orElseThrow(() -> new RuntimeException("Pedido no encontrado"))
                : new PedidoPersonalizado();

        if (pedido.getCodigoPedido() == null || pedido.getCodigoPedido().isBlank()) {
            pedido.setCodigoPedido(generarCodigoPedido());
        }

        pedido.setCanalOrigen(valorO(dto.getCanalOrigen(), "TIENDA"));
        pedido.setEstado(valorO(dto.getEstado(), "BORRADOR"));
        pedido.setCliente(dto.getClienteId() != null ? clienteRepository.findById(dto.getClienteId()).orElse(null) : null);
        pedido.setClienteNombre(normalizarTitulo(dto.getClienteNombre()));
        pedido.setClienteTipoDocumento(normalizarTexto(dto.getClienteTipoDocumento()));
        pedido.setClienteNumeroDocumento(normalizarTexto(dto.getClienteNumeroDocumento()));
        pedido.setClienteWhatsapp(normalizarTelefono(dto.getClienteWhatsapp()));
        pedido.setClienteTelefono(normalizarTelefono(dto.getClienteTelefono()));
        pedido.setClienteEmail(normalizarTexto(dto.getClienteEmail()));
        pedido.setNombreDestinatario(normalizarTitulo(dto.getNombreDestinatario()));
        pedido.setTelefonoDestinatario(normalizarTelefono(dto.getTelefonoDestinatario()));
        pedido.setDedicatoria(normalizarTexto(dto.getDedicatoria()));
        pedido.setNombreEtiqueta(normalizarTexto(dto.getNombreEtiqueta()));
        pedido.setNotasDiseno(normalizarTexto(dto.getNotasDiseno()));
        pedido.setObservacionesCliente(normalizarTexto(dto.getObservacionesCliente()));
        pedido.setModoEntrega(valorO(dto.getModoEntrega(), "RECOJO_TIENDA"));
        pedido.setEnvioGratis(Boolean.TRUE.equals(dto.getEnvioGratis()));
        pedido.setDepartamento(normalizarTitulo(dto.getDepartamento()));
        pedido.setProvincia(normalizarTitulo(dto.getProvincia()));
        pedido.setDistrito(normalizarTitulo(dto.getDistrito()));
        pedido.setDireccionEntrega(normalizarTexto(dto.getDireccionEntrega()));
        pedido.setReferenciaEntrega(normalizarTexto(dto.getReferenciaEntrega()));
        pedido.setFechaEntregaSolicitada(parseDate(dto.getFechaEntregaSolicitada()));
        pedido.setFranjaEntrega(normalizarTexto(dto.getFranjaEntrega()));
        pedido.setDescuento(nz(dto.getDescuento()));
        pedido.setAdelanto(nz(dto.getAdelanto()));
        pedido.setObservacionesInternas(normalizarTexto(dto.getObservacionesInternas()));

        pedido.getItems().clear();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (PedidoPersonalizadoDTO.ItemDTO itemDto : dto.getItems()) {
            PedidoCalculado calculado = calcularItem(itemDto);
            PedidoPersonalizadoItem item = new PedidoPersonalizadoItem();
            item.setPedido(pedido);
            item.setPlantilla(calculado.plantilla());
            item.setCodigoModeloSnapshot(calculado.codigoModelo());
            item.setNombreComercialSnapshot(calculado.nombre());
            item.setFotoSnapshot(calculado.foto());
            item.setCategoriaSnapshot(calculado.categoria());
            item.setCantidad(calculado.cantidad());
            item.setCostoSnapshot(calculado.costo());
            item.setPrecioMinimoSnapshot(calculado.precioMinimo());
            item.setPrecioSugeridoSnapshot(calculado.precioSugerido());
            item.setPrecioFinal(calculado.precioFinal());
            item.setConfiguracionJson(calculado.configuracionJson());
            for (PedidoPersonalizadoDTO.ComponenteDTO componenteDto : calculado.componentes()) {
                PedidoPersonalizadoComponente componente = new PedidoPersonalizadoComponente();
                componente.setPedidoItem(item);
                componente.setCategoria(componenteDto.getCategoria());
                componente.setNombre(componenteDto.getNombre());
                componente.setTipoOrigen(valorO(componenteDto.getTipoOrigen(), "MANUAL"));
                componente.setInsumoPersonalizadoId(componenteDto.getInsumoPersonalizadoId());
                componente.setAdicionalPersonalizadoId(componenteDto.getAdicionalPersonalizadoId());
                componente.setCantidad(nzPositivo(componenteDto.getCantidad(), BigDecimal.ONE));
                componente.setCostoUnitario(nz(componenteDto.getCostoUnitario()));
                componente.setCostoTotal(nz(componenteDto.getCostoTotal()));
                componente.setIncluido(componenteDto.getIncluido() == null || componenteDto.getIncluido());
                componente.setEliminado(Boolean.TRUE.equals(componenteDto.getEliminado()));
                componente.setOrden(componenteDto.getOrden() != null ? componenteDto.getOrden() : 0);
                item.getComponentes().add(componente);
            }
            pedido.getItems().add(item);
            subtotal = subtotal.add(calculado.precioFinal());
        }

        BigDecimal costoEnvio = resolverCostoEnvio(dto, pedido);
        pedido.setCostoEnvio(costoEnvio);
        pedido.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        BigDecimal total = subtotal.subtract(pedido.getDescuento()).add(costoEnvio).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("El total del pedido no puede ser negativo.");
        }
        if (pedido.getAdelanto().compareTo(total) > 0) {
            throw new RuntimeException("El adelanto no puede exceder el total del pedido.");
        }
        pedido.setTotal(total);
        pedido.setSaldo(total.subtract(pedido.getAdelanto()).setScale(2, RoundingMode.HALF_UP));
        return pedidoRepository.save(pedido);
    }

    public Map<String, Object> calcular(PedidoPersonalizadoDTO dto) {
        PedidoPersonalizado pedido = construirPedidoTemporal(dto);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subtotal", pedido.getSubtotal());
        out.put("descuento", pedido.getDescuento());
        out.put("costoEnvio", pedido.getCostoEnvio());
        out.put("total", pedido.getTotal());
        out.put("adelanto", pedido.getAdelanto());
        out.put("saldo", pedido.getSaldo());
        out.put("items", pedido.getItems().stream().map(item -> Map.of(
                "codigoModelo", item.getCodigoModeloSnapshot(),
                "nombre", item.getNombreComercialSnapshot(),
                "cantidad", item.getCantidad(),
                "costo", item.getCostoSnapshot(),
                "precioMinimo", item.getPrecioMinimoSnapshot(),
                "precioSugerido", item.getPrecioSugeridoSnapshot(),
                "precioFinal", item.getPrecioFinal()
        )).toList());
        return out;
    }

    @Transactional
    public Map<String, Object> cerrarVenta(Long pedidoId, String tipoComprobante, String formaPago, String metodoPago) {
        PedidoPersonalizado pedido = pedidoRepository.findDetalleById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
        if (pedido.getVentaId() != null) {
            throw new RuntimeException("El pedido ya fue convertido a venta.");
        }
        if (pedido.getItems().isEmpty()) {
            throw new RuntimeException("El pedido no tiene modelos cargados.");
        }

        Producto servicio = productoRepository.findByCodigoInterno(SERVICIO_PERSONALIZADO_CODE)
                .orElseThrow(() -> new RuntimeException("No se encontró el servicio base " + SERVICIO_PERSONALIZADO_CODE));

        VentaDTO ventaDTO = new VentaDTO();
        ventaDTO.setClienteNombre(valorO(pedido.getClienteNombre(), "CLIENTE PERSONALIZADO"));
        ventaDTO.setClienteDocumento(documentoValido(pedido.getClienteNumeroDocumento()));
        ventaDTO.setClienteDireccion(valorO(pedido.getDireccionEntrega(), ""));
        ventaDTO.setClienteTelefono(valorO(pedido.getClienteTelefono(), pedido.getClienteWhatsapp()));
        ventaDTO.setTipoComprobante(valorO(tipoComprobante, "NOTA_VENTA"));
        ventaDTO.setFormaPago(valorO(formaPago, pedido.getSaldo().compareTo(BigDecimal.ZERO) > 0 ? "CREDITO" : "CONTADO"));
        ventaDTO.setMetodoPago(valorO(metodoPago, "EFECTIVO"));
        if ("CREDITO".equalsIgnoreCase(ventaDTO.getFormaPago())) {
            ventaDTO.setMontoInicial(nz(pedido.getAdelanto()));
            ventaDTO.setDiasCredito(30);
        }

        List<VentaDTO.DetalleDTO> items = new ArrayList<>();
        for (PedidoPersonalizadoItem item : pedido.getItems()) {
            VentaDTO.DetalleDTO detalle = new VentaDTO.DetalleDTO();
            detalle.setProductoId(servicio.getId());
            detalle.setCantidad(BigDecimal.ONE);
            detalle.setPrecioVenta(item.getPrecioFinal());
            detalle.setDescripcion(construirDescripcionVenta(item, pedido));
            items.add(detalle);
        }
        ventaDTO.setItems(items);

        Map<String, Object> ventaCreada = ventaService.crearVenta(ventaDTO);
        Long ventaId = Long.valueOf(ventaCreada.get("id").toString());
        pedido.setVentaId(ventaId);
        pedido.setEstado("VENDIDO");
        pedidoRepository.save(pedido);

        Venta venta = ventaRepository.findById(ventaId).orElse(null);
        String telefono = normalizarTelefono(valorO(pedido.getClienteWhatsapp(), pedido.getClienteTelefono()));
        return Map.of(
                "pedidoId", pedido.getId(),
                "ventaId", ventaId,
                "serie", venta != null ? venta.getSerie() : "",
                "numero", venta != null ? venta.getNumero() : "",
                "imprimirUrl", "/ventas/imprimir/" + ventaId,
                "pdfUrl", "/ventas/pdf/" + ventaId,
                "ticketUrl", "/ventas/pdf-ticket/" + ventaId,
                "a4Url", "/ventas/pdf-a4/" + ventaId,
                "whatsappUrl", telefono.isBlank() ? "" : "https://wa.me/51" + telefono + "?text=" + urlEncode(construirMensajeWhatsapp(pedido))
        );
    }

    public String construirWhatsappUrl(Long pedidoId) {
        PedidoPersonalizado pedido = pedidoRepository.findDetalleById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
        String telefono = normalizarTelefono(valorO(pedido.getClienteWhatsapp(), pedido.getClienteTelefono()));
        if (telefono.isBlank()) return "";
        return "https://wa.me/51" + telefono + "?text=" + urlEncode(construirMensajeWhatsapp(pedido));
    }

    private PedidoPersonalizado construirPedidoTemporal(PedidoPersonalizadoDTO dto) {
        PedidoPersonalizado pedido = new PedidoPersonalizado();
        pedido.setModoEntrega(valorO(dto.getModoEntrega(), "RECOJO_TIENDA"));
        pedido.setEnvioGratis(Boolean.TRUE.equals(dto.getEnvioGratis()));
        pedido.setDepartamento(normalizarTitulo(dto.getDepartamento()));
        pedido.setProvincia(normalizarTitulo(dto.getProvincia()));
        pedido.setDistrito(normalizarTitulo(dto.getDistrito()));
        pedido.setDescuento(nz(dto.getDescuento()));
        pedido.setAdelanto(nz(dto.getAdelanto()));

        BigDecimal subtotal = BigDecimal.ZERO;
        for (PedidoPersonalizadoDTO.ItemDTO itemDto : dto.getItems()) {
            PedidoCalculado calculado = calcularItem(itemDto);
            PedidoPersonalizadoItem item = new PedidoPersonalizadoItem();
            item.setCodigoModeloSnapshot(calculado.codigoModelo());
            item.setNombreComercialSnapshot(calculado.nombre());
            item.setCantidad(calculado.cantidad());
            item.setCostoSnapshot(calculado.costo());
            item.setPrecioMinimoSnapshot(calculado.precioMinimo());
            item.setPrecioSugeridoSnapshot(calculado.precioSugerido());
            item.setPrecioFinal(calculado.precioFinal());
            pedido.getItems().add(item);
            subtotal = subtotal.add(calculado.precioFinal());
        }

        pedido.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        pedido.setCostoEnvio(resolverCostoEnvio(dto, pedido));
        pedido.setTotal(subtotal.subtract(pedido.getDescuento()).add(pedido.getCostoEnvio()).setScale(2, RoundingMode.HALF_UP));
        pedido.setSaldo(pedido.getTotal().subtract(pedido.getAdelanto()).setScale(2, RoundingMode.HALF_UP));
        return pedido;
    }

    private PedidoCalculado calcularItem(PedidoPersonalizadoDTO.ItemDTO itemDto) {
        PlantillaPersonalizada plantilla = itemDto.getPlantillaId() != null
                ? plantillaRepository.findDetalleById(itemDto.getPlantillaId()).orElse(null)
                : null;
        BigDecimal cantidad = nzPositivo(itemDto.getCantidad(), BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);

        List<PedidoPersonalizadoDTO.ComponenteDTO> componentes = itemDto.getComponentes() == null || itemDto.getComponentes().isEmpty()
                ? construirComponentesPorDefecto(plantilla)
                : normalizarComponentes(itemDto.getComponentes());

        BigDecimal costoMaterialUnitario = BigDecimal.ZERO;
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (PedidoPersonalizadoDTO.ComponenteDTO componente : componentes) {
            BigDecimal cantidadComp = nzPositivo(componente.getCantidad(), BigDecimal.ONE);
            BigDecimal costoUnit = resolverCostoComponente(componente);
            BigDecimal costoTotal = costoUnit.multiply(cantidadComp).setScale(2, RoundingMode.HALF_UP);
            componente.setCantidad(cantidadComp);
            componente.setCostoUnitario(costoUnit);
            componente.setCostoTotal(costoTotal);
            if (!Boolean.TRUE.equals(componente.getEliminado()) && Boolean.TRUE.equals(componente.getIncluido())) {
                costoMaterialUnitario = costoMaterialUnitario.add(costoTotal);
            }
            snapshot.add(Map.of(
                    "categoria", valorO(componente.getCategoria(), "GENERAL"),
                    "nombre", valorO(componente.getNombre(), "COMPONENTE"),
                    "cantidad", cantidadComp,
                    "costoUnitario", costoUnit,
                    "costoTotal", costoTotal,
                    "incluido", componente.getIncluido() == null || componente.getIncluido(),
                    "eliminado", Boolean.TRUE.equals(componente.getEliminado())
            ));
        }

        PlantillaRangoPrecio rango = resolverRango(plantilla, cantidad);
        BigDecimal margenMin = rango != null ? nz(rango.getMargenMinimoPct()) : plantilla != null ? nz(plantilla.getMargenMinimoPct()) : BigDecimal.ZERO;
        BigDecimal margenObj = rango != null ? nz(rango.getMargenObjetivoPct()) : plantilla != null ? nz(plantilla.getMargenObjetivoPct()) : margenMin;
        BigDecimal cargoFijo = rango != null ? nz(rango.getCargoFijo()) : BigDecimal.ZERO;
        BigDecimal descuentoMayor = rango != null && rango.getDescuentoMayorPct() != null ? rango.getDescuentoMayorPct() : BigDecimal.ZERO;

        BigDecimal costoLinea = costoMaterialUnitario.multiply(cantidad).add(cargoFijo).setScale(2, RoundingMode.HALF_UP);
        BigDecimal precioMinimo = aplicarMargen(costoLinea, margenMin);
        BigDecimal precioSugerido = aplicarMargen(costoLinea, margenObj);
        if (descuentoMayor.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal factor = BigDecimal.ONE.subtract(descuentoMayor.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            precioSugerido = precioSugerido.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            if (precioSugerido.compareTo(precioMinimo) < 0) {
                precioSugerido = precioMinimo;
            }
        }

        BigDecimal precioFinal = nzPositivo(itemDto.getPrecioFinal(), precioSugerido).setScale(2, RoundingMode.HALF_UP);
        if (precioFinal.compareTo(precioMinimo) < 0) {
            throw new RuntimeException("El precio final no puede ser menor al mínimo negociable.");
        }

        String configuracionJson = writeJson(Map.of(
                "cantidad", cantidad,
                "margenMinimoPct", margenMin,
                "margenObjetivoPct", margenObj,
                "cargoFijo", cargoFijo,
                "descuentoMayorPct", descuentoMayor,
                "componentes", snapshot
        ));

        return new PedidoCalculado(
                plantilla,
                valorO(itemDto.getCodigoModeloSnapshot(), plantilla != null ? plantilla.getCodigoModelo() : "MODELO-LIBRE"),
                valorO(itemDto.getNombreComercialSnapshot(), plantilla != null ? plantilla.getNombreComercial() : "Personalizado"),
                valorO(itemDto.getFotoSnapshot(), plantilla != null ? plantilla.getFotoPrincipal() : ""),
                valorO(itemDto.getCategoriaSnapshot(), plantilla != null ? plantilla.getCategoria() : "PERSONALIZADO"),
                cantidad,
                costoLinea,
                precioMinimo,
                precioSugerido,
                precioFinal,
                configuracionJson,
                componentes
        );
    }

    private List<PedidoPersonalizadoDTO.ComponenteDTO> construirComponentesPorDefecto(PlantillaPersonalizada plantilla) {
        List<PedidoPersonalizadoDTO.ComponenteDTO> componentes = new ArrayList<>();
        if (plantilla == null) return componentes;
        plantilla.getComponentes().stream()
                .sorted(Comparator.comparing(PlantillaComponentePersonalizado::getOrden))
                .forEach(c -> {
                    boolean incluido = "BASE".equalsIgnoreCase(c.getTipoComponente())
                            || "INCLUIDO".equalsIgnoreCase(c.getTipoComponente())
                            || Boolean.TRUE.equals(c.getIncluidoPorDefecto());
                    PedidoPersonalizadoDTO.ComponenteDTO dto = new PedidoPersonalizadoDTO.ComponenteDTO();
                    dto.setCategoria(c.getAdicionalPersonalizado() != null && c.getAdicionalPersonalizado().getCategoriaAdicional() != null
                            ? c.getAdicionalPersonalizado().getCategoriaAdicional().getNombre()
                            : c.getInsumoPersonalizado() != null ? valorO(c.getInsumoPersonalizado().getCategoria(), "INSUMO") : "GENERAL");
                    dto.setNombre(c.getInsumoPersonalizado() != null ? c.getInsumoPersonalizado().getNombre()
                            : c.getAdicionalPersonalizado() != null ? c.getAdicionalPersonalizado().getNombre()
                            : valorO(c.getDescripcionManual(), "COMPONENTE"));
                    dto.setTipoOrigen(valorO(c.getTipoOrigen(), "MANUAL"));
                    dto.setInsumoPersonalizadoId(c.getInsumoPersonalizado() != null ? c.getInsumoPersonalizado().getId() : null);
                    dto.setAdicionalPersonalizadoId(c.getAdicionalPersonalizado() != null ? c.getAdicionalPersonalizado().getId() : null);
                    dto.setCantidad(nzPositivo(c.getCantidadBase(), BigDecimal.ONE));
                    dto.setIncluido(incluido);
                    dto.setEliminado(false);
                    dto.setOrden(c.getOrden());
                    componentes.add(dto);
                });
        return componentes;
    }

    private List<PedidoPersonalizadoDTO.ComponenteDTO> normalizarComponentes(List<PedidoPersonalizadoDTO.ComponenteDTO> componentes) {
        return componentes.stream().map(c -> {
            PedidoPersonalizadoDTO.ComponenteDTO dto = new PedidoPersonalizadoDTO.ComponenteDTO();
            dto.setCategoria(normalizarTexto(c.getCategoria()));
            dto.setNombre(normalizarTexto(c.getNombre()));
            dto.setTipoOrigen(valorO(c.getTipoOrigen(), "MANUAL"));
            dto.setInsumoPersonalizadoId(c.getInsumoPersonalizadoId());
            dto.setAdicionalPersonalizadoId(c.getAdicionalPersonalizadoId());
            dto.setCantidad(nzPositivo(c.getCantidad(), BigDecimal.ONE));
            dto.setCostoUnitario(nz(c.getCostoUnitario()));
            dto.setCostoTotal(nz(c.getCostoTotal()));
            dto.setIncluido(c.getIncluido() == null || c.getIncluido());
            dto.setEliminado(Boolean.TRUE.equals(c.getEliminado()));
            dto.setOrden(c.getOrden() != null ? c.getOrden() : 0);
            return dto;
        }).toList();
    }

    private PlantillaRangoPrecio resolverRango(PlantillaPersonalizada plantilla, BigDecimal cantidad) {
        if (plantilla == null) return null;
        int qty = cantidad.setScale(0, RoundingMode.HALF_UP).intValue();
        return plantilla.getRangos().stream()
                .filter(r -> Boolean.TRUE.equals(r.getActivo()))
                .sorted(Comparator.comparing(PlantillaRangoPrecio::getCantidadMin))
                .filter(r -> qty >= r.getCantidadMin() && (r.getCantidadMax() == null || qty <= r.getCantidadMax()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal resolverCostoComponente(PedidoPersonalizadoDTO.ComponenteDTO componente) {
        if (componente.getCostoUnitario() != null && componente.getCostoUnitario().compareTo(BigDecimal.ZERO) > 0) {
            return componente.getCostoUnitario().setScale(2, RoundingMode.HALF_UP);
        }
        if (componente.getInsumoPersonalizadoId() != null) {
            return insumoRepository.findById(componente.getInsumoPersonalizadoId())
                    .map(i -> i.getProducto() != null ? nz(i.getProducto().getPrecioCompra()) : BigDecimal.ZERO)
                    .orElse(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        if (componente.getAdicionalPersonalizadoId() != null) {
            return adicionalRepository.findById(componente.getAdicionalPersonalizadoId())
                    .map(this::resolverCostoAdicional)
                    .orElse(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolverCostoAdicional(AdicionalPersonalizado adicional) {
        if (adicional.getCostoManual() != null && adicional.getCostoManual().compareTo(BigDecimal.ZERO) > 0) {
            return adicional.getCostoManual();
        }
        if (adicional.getInsumoPersonalizado() != null && adicional.getInsumoPersonalizado().getProducto() != null) {
            return nz(adicional.getInsumoPersonalizado().getProducto().getPrecioCompra());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolverCostoEnvio(PedidoPersonalizadoDTO dto, PedidoPersonalizado pedido) {
        if (Boolean.TRUE.equals(dto.getEnvioGratis())) {
            return BigDecimal.ZERO;
        }
        BigDecimal manual = nz(dto.getCostoEnvio());
        if (manual.compareTo(BigDecimal.ZERO) > 0) {
            return manual;
        }
        String dep = valorO(dto.getDepartamento(), pedido.getDepartamento());
        String prov = valorO(dto.getProvincia(), pedido.getProvincia());
        String dist = valorO(dto.getDistrito(), pedido.getDistrito());
        if (!normalizarTexto(dep).isBlank() && !normalizarTexto(prov).isBlank() && !normalizarTexto(dist).isBlank()) {
            return zonaRepository.findZonaExacta(dep, prov, dist)
                    .map(ZonaEntregaPersonalizado::getTarifaBase)
                    .map(this::nz)
                    .orElse(BigDecimal.ZERO);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal aplicarMargen(BigDecimal base, BigDecimal margenPct) {
        BigDecimal factor = BigDecimal.ONE.add(nz(margenPct).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        return base.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private PedidoPersonalizadoDTO.ComponenteDTO mapearComponente(PedidoPersonalizadoComponente componente) {
        PedidoPersonalizadoDTO.ComponenteDTO dto = new PedidoPersonalizadoDTO.ComponenteDTO();
        dto.setCategoria(componente.getCategoria());
        dto.setNombre(componente.getNombre());
        dto.setTipoOrigen(componente.getTipoOrigen());
        dto.setInsumoPersonalizadoId(componente.getInsumoPersonalizadoId());
        dto.setAdicionalPersonalizadoId(componente.getAdicionalPersonalizadoId());
        dto.setCantidad(componente.getCantidad());
        dto.setCostoUnitario(componente.getCostoUnitario());
        dto.setCostoTotal(componente.getCostoTotal());
        dto.setIncluido(componente.getIncluido());
        dto.setEliminado(componente.getEliminado());
        dto.setOrden(componente.getOrden());
        return dto;
    }

    private String construirDescripcionVenta(PedidoPersonalizadoItem item, PedidoPersonalizado pedido) {
        List<String> partes = new ArrayList<>();
        partes.add(item.getCodigoModeloSnapshot() + " - " + item.getNombreComercialSnapshot());
        if (pedido.getNombreEtiqueta() != null && !pedido.getNombreEtiqueta().isBlank()) {
            partes.add("Etiqueta: " + pedido.getNombreEtiqueta());
        }
        if (pedido.getDedicatoria() != null && !pedido.getDedicatoria().isBlank()) {
            partes.add("Dedicatoria: " + recortar(pedido.getDedicatoria(), 80));
        }
        if (pedido.getModoEntrega() != null && !pedido.getModoEntrega().isBlank()) {
            partes.add("Entrega: " + pedido.getModoEntrega());
        }
        return String.join(" | ", partes);
    }

    private String construirMensajeWhatsapp(PedidoPersonalizado pedido) {
        String empresa = configuracionService.obtenerConfiguracion().getNombreEmpresa();
        StringBuilder sb = new StringBuilder();
        sb.append("Hola ").append(valorO(pedido.getClienteNombre(), "cliente")).append(",\n");
        sb.append("Te compartimos el resumen de tu pedido ").append(pedido.getCodigoPedido()).append(".\n\n");
        for (PedidoPersonalizadoItem item : pedido.getItems()) {
            sb.append("• ").append(item.getCodigoModeloSnapshot()).append(" - ").append(item.getNombreComercialSnapshot())
                    .append(": S/ ").append(item.getPrecioFinal().setScale(2, RoundingMode.HALF_UP)).append("\n");
        }
        if (pedido.getCostoEnvio() != null && pedido.getCostoEnvio().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("Envío: S/ ").append(pedido.getCostoEnvio().setScale(2, RoundingMode.HALF_UP)).append("\n");
        } else if (Boolean.TRUE.equals(pedido.getEnvioGratis())) {
            sb.append("Envío: GRATIS\n");
        }
        sb.append("Total: S/ ").append(pedido.getTotal().setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("Gracias,\n").append(valorO(empresa, "Personalizado"));
        return sb.toString();
    }

    private String generarCodigoPedido() {
        int intentos = 0;
        while (intentos++ < 20) {
            String codigo = "PP-" + LocalDate.now().format(PEDIDO_DATE) + "-" + String.format("%04d", (int) (Math.random() * 10000));
            if (pedidoRepository.findByCodigoPedido(codigo).isEmpty()) {
                return codigo;
            }
        }
        return "PP-" + System.currentTimeMillis();
    }

    private String documentoValido(String documento) {
        String limpio = normalizarTexto(documento).replaceAll("\\D", "");
        return (limpio.length() == 8 || limpio.length() == 11) ? limpio : "00000000";
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("No se pudo serializar snapshot del pedido: {}", e.getMessage());
            return "{}";
        }
    }

    private String normalizarTexto(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizarTitulo(String value) {
        return normalizarTexto(value).toUpperCase(Locale.ROOT);
    }

    private String valorO(String value, String defecto) {
        String limpio = normalizarTexto(value);
        return limpio.isBlank() ? defecto : limpio;
    }

    private String normalizarTelefono(String value) {
        return normalizarTexto(value).replaceAll("\\D", "");
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal nzPositivo(BigDecimal value, BigDecimal defecto) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) return defecto;
        return value;
    }

    private String recortar(String value, int max) {
        String limpio = normalizarTexto(value);
        if (limpio.length() <= max) return limpio;
        return limpio.substring(0, max - 3) + "...";
    }

    private record PedidoCalculado(PlantillaPersonalizada plantilla,
                                   String codigoModelo,
                                   String nombre,
                                   String foto,
                                   String categoria,
                                   BigDecimal cantidad,
                                   BigDecimal costo,
                                   BigDecimal precioMinimo,
                                   BigDecimal precioSugerido,
                                   BigDecimal precioFinal,
                                   String configuracionJson,
                                   List<PedidoPersonalizadoDTO.ComponenteDTO> componentes) {
    }
}
