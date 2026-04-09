package com.libreria.sistema.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libreria.sistema.model.AdicionalPersonalizado;
import com.libreria.sistema.model.CategoriaAdicionalPersonalizado;
import com.libreria.sistema.model.Compra;
import com.libreria.sistema.model.InsumoPersonalizado;
import com.libreria.sistema.model.PlantillaComponentePersonalizado;
import com.libreria.sistema.model.PlantillaPersonalizada;
import com.libreria.sistema.model.PlantillaRangoPrecio;
import com.libreria.sistema.model.PresentacionCompra;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.ZonaEntregaPersonalizado;
import com.libreria.sistema.model.dto.CompraDTO;
import com.libreria.sistema.model.dto.CompraPersonalizadaDTO;
import com.libreria.sistema.model.dto.InsumoPersonalizadoFormDTO;
import com.libreria.sistema.model.dto.PlantillaPersonalizadaFormDTO;
import com.libreria.sistema.repository.AdicionalPersonalizadoRepository;
import com.libreria.sistema.repository.CategoriaAdicionalPersonalizadoRepository;
import com.libreria.sistema.repository.InsumoPersonalizadoRepository;
import com.libreria.sistema.repository.PedidoPersonalizadoRepository;
import com.libreria.sistema.repository.PlantillaPersonalizadaRepository;
import com.libreria.sistema.repository.PresentacionCompraRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.ZonaEntregaPersonalizadoRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class PersonalizadoCatalogoService {

    private final InsumoPersonalizadoRepository insumoRepository;
    private final PresentacionCompraRepository presentacionRepository;
    private final CategoriaAdicionalPersonalizadoRepository categoriaRepository;
    private final AdicionalPersonalizadoRepository adicionalRepository;
    private final PlantillaPersonalizadaRepository plantillaRepository;
    private final ZonaEntregaPersonalizadoRepository zonaRepository;
    private final PedidoPersonalizadoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final CompraService compraService;
    private final UploadStorageService uploadStorageService;
    private final ObjectMapper objectMapper;

    public PersonalizadoCatalogoService(InsumoPersonalizadoRepository insumoRepository,
                                        PresentacionCompraRepository presentacionRepository,
                                        CategoriaAdicionalPersonalizadoRepository categoriaRepository,
                                        AdicionalPersonalizadoRepository adicionalRepository,
                                        PlantillaPersonalizadaRepository plantillaRepository,
                                        ZonaEntregaPersonalizadoRepository zonaRepository,
                                        PedidoPersonalizadoRepository pedidoRepository,
                                        ProductoRepository productoRepository,
                                        CompraService compraService,
                                        UploadStorageService uploadStorageService,
                                        ObjectMapper objectMapper) {
        this.insumoRepository = insumoRepository;
        this.presentacionRepository = presentacionRepository;
        this.categoriaRepository = categoriaRepository;
        this.adicionalRepository = adicionalRepository;
        this.plantillaRepository = plantillaRepository;
        this.zonaRepository = zonaRepository;
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.compraService = compraService;
        this.uploadStorageService = uploadStorageService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> obtenerResumen() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("insumosActivos", insumoRepository.countByActivoTrue());
        out.put("plantillasActivas", plantillaRepository.countActivas());
        out.put("pedidosPendientes", pedidoRepository.countPendientes());
        out.put("adicionalesActivos", adicionalRepository.findByActivoTrueOrderByNombreAsc().size());
        out.put("zonasActivas", zonaRepository.findByActivoTrueOrderByDepartamentoAscProvinciaAscDistritoAsc().size());
        return out;
    }

    public List<InsumoPersonalizado> listarInsumos() {
        return insumoRepository.findTodosOrdenados();
    }

    public Optional<InsumoPersonalizado> obtenerInsumoDetalle(Long id) {
        return insumoRepository.findDetalleById(id);
    }

    public InsumoPersonalizadoFormDTO construirFormularioInsumo(Long id) {
        if (id == null) {
            InsumoPersonalizadoFormDTO dto = new InsumoPersonalizadoFormDTO();
            dto.setActivo(true);
            dto.setControlaStock(true);
            dto.setUnidadBase("UNIDAD");
            dto.setPresentacionesJson("[]");
            return dto;
        }

        InsumoPersonalizado insumo = insumoRepository.findDetalleById(id)
                .orElseThrow(() -> new RuntimeException("Insumo personalizado no encontrado"));
        InsumoPersonalizadoFormDTO dto = new InsumoPersonalizadoFormDTO();
        dto.setId(insumo.getId());
        dto.setCodigo(insumo.getCodigo());
        dto.setNombre(insumo.getNombre());
        dto.setSlugBusqueda(insumo.getSlugBusqueda());
        dto.setCategoria(insumo.getCategoria());
        dto.setSubcategoria(insumo.getSubcategoria());
        dto.setUnidadBase(insumo.getUnidadBase());
        dto.setControlaStock(insumo.getControlaStock());
        dto.setActivo(insumo.getActivo());
        dto.setDescripcion(insumo.getDescripcion());
        dto.setTags(insumo.getTags());
        dto.setFotoActual(insumo.getFoto());
        dto.setPresentacionesJson(writeJson(obtenerPresentacionesEdicion(insumo.getId())));
        return dto;
    }

    @Transactional
    public InsumoPersonalizado guardarInsumo(InsumoPersonalizadoFormDTO dto, MultipartFile foto) throws Exception {
        validarCodigoUnicoInsumo(dto.getCodigo(), dto.getId());
        validarSlugUnicoInsumo(dto.getSlugBusqueda(), dto.getId());

        InsumoPersonalizado insumo = dto.getId() != null
                ? insumoRepository.findById(dto.getId()).orElseThrow(() -> new RuntimeException("Insumo no encontrado"))
                : new InsumoPersonalizado();

        Producto sombra = insumo.getProducto() != null
                ? productoRepository.findById(insumo.getProducto().getId()).orElse(new Producto())
                : new Producto();

        insumo.setCodigo(normalizarCodigo(dto.getCodigo()));
        insumo.setNombre(normalizarTitulo(dto.getNombre()));
        insumo.setSlugBusqueda(normalizarSlug(dto.getSlugBusqueda(), dto.getNombre()));
        insumo.setCategoria(normalizarTitulo(dto.getCategoria()));
        insumo.setSubcategoria(normalizarTitulo(dto.getSubcategoria()));
        insumo.setUnidadBase(valorO(dto.getUnidadBase(), "UNIDAD").toUpperCase(Locale.ROOT));
        insumo.setControlaStock(dto.getControlaStock() == null || dto.getControlaStock());
        insumo.setActivo(dto.getActivo() == null || dto.getActivo());
        insumo.setDescripcion(normalizarTexto(dto.getDescripcion()));
        insumo.setTags(normalizarTitulo(dto.getTags()));

        if (foto != null && !foto.isEmpty()) {
            insumo.setFoto(uploadStorageService.guardarImagen(foto));
        } else if (dto.getFotoActual() != null && !dto.getFotoActual().isBlank()) {
            insumo.setFoto(dto.getFotoActual());
        }

        configurarProductoSombra(sombra, insumo);
        productoRepository.save(sombra);
        insumo.setProducto(sombra);
        InsumoPersonalizado guardado = insumoRepository.save(insumo);
        guardarPresentacionesInsumo(guardado, dto.getPresentacionesJson());
        return guardado;
    }

    @Transactional
    public void alternarEstadoInsumo(Long id) {
        InsumoPersonalizado insumo = insumoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Insumo no encontrado"));
        insumo.setActivo(!Boolean.TRUE.equals(insumo.getActivo()));
        if (insumo.getProducto() != null) {
            productoRepository.findById(insumo.getProducto().getId()).ifPresent(producto -> {
                producto.setActivo(Boolean.TRUE.equals(insumo.getActivo()));
                productoRepository.save(producto);
            });
        }
        insumoRepository.save(insumo);
    }

    public List<Map<String, Object>> buscarInsumos(String termino) {
        String valor = normalizarTexto(termino);
        List<InsumoPersonalizado> insumos = valor.isBlank()
                ? insumoRepository.findActivos()
                : insumoRepository.buscarActivos(valor);

        return insumos.stream().map(insumo -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", insumo.getId());
            row.put("codigo", insumo.getCodigo());
            row.put("nombre", insumo.getNombre());
            row.put("categoria", insumo.getCategoria());
            row.put("foto", insumo.getFoto());
            row.put("unidadBase", insumo.getUnidadBase());
            row.put("stock", insumo.getProducto() != null && insumo.getProducto().getStockActual() != null ? insumo.getProducto().getStockActual() : 0);
            row.put("precioCompra", insumo.getProducto() != null && insumo.getProducto().getPrecioCompra() != null ? insumo.getProducto().getPrecioCompra() : BigDecimal.ZERO);
            row.put("presentaciones", mapearPresentaciones(insumo.getId()));
            return row;
        }).toList();
    }

    public List<Map<String, Object>> mapearPresentaciones(Long insumoId) {
        List<PresentacionCompra> presentaciones = new ArrayList<>();
        presentaciones.addAll(presentacionRepository.findGlobalesActivas("INSUMO_PERSONALIZADO"));
        if (insumoId != null) {
            presentaciones.addAll(presentacionRepository.findByInsumoPersonalizadoId(insumoId));
        }
        return presentaciones.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActiva()))
                .sorted(Comparator.comparing((PresentacionCompra p) -> !Boolean.TRUE.equals(p.getPredeterminada()))
                        .thenComparing(PresentacionCompra::getOrden)
                        .thenComparing(PresentacionCompra::getNombrePresentacion))
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", p.getId());
                    row.put("nombre", p.getNombrePresentacion());
                    row.put("unidadMedida", p.getUnidadMedidaPresentacion());
                    row.put("factorBase", p.getFactorBase());
                    row.put("permiteDecimal", p.getPermiteDecimal());
                    row.put("predeterminada", p.getPredeterminada());
                    return row;
                })
                .toList();
    }

    public List<PlantillaPersonalizada> listarPlantillas() {
        return plantillaRepository.findAllByOrderByActivoDescNombreComercialAsc();
    }

    public Optional<PlantillaPersonalizada> obtenerPlantillaDetalle(Long id) {
        return plantillaRepository.findDetalleById(id);
    }

    public PlantillaPersonalizadaFormDTO construirFormularioPlantilla(Long id) {
        if (id == null) {
            PlantillaPersonalizadaFormDTO dto = new PlantillaPersonalizadaFormDTO();
            dto.setActivo(true);
            dto.setVisibleWeb(true);
            dto.setVendibleDirecto(true);
            dto.setPermitePersonalizacion(true);
            dto.setMargenMinimoPct(BigDecimal.ZERO);
            dto.setMargenObjetivoPct(new BigDecimal("20.00"));
            dto.setComponentesJson("[]");
            dto.setRangosJson("[]");
            return dto;
        }

        PlantillaPersonalizada plantilla = plantillaRepository.findDetalleById(id)
                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada"));
        PlantillaPersonalizadaFormDTO dto = new PlantillaPersonalizadaFormDTO();
        dto.setId(plantilla.getId());
        dto.setCodigoModelo(plantilla.getCodigoModelo());
        dto.setNombreComercial(plantilla.getNombreComercial());
        dto.setSlug(plantilla.getSlug());
        dto.setCategoria(plantilla.getCategoria());
        dto.setColeccionOcasion(plantilla.getColeccionOcasion());
        dto.setDescripcionComercial(plantilla.getDescripcionComercial());
        dto.setActivo(plantilla.getActivo());
        dto.setVisibleWeb(plantilla.getVisibleWeb());
        dto.setVendibleDirecto(plantilla.getVendibleDirecto());
        dto.setPermitePersonalizacion(plantilla.getPermitePersonalizacion());
        dto.setMargenMinimoPct(plantilla.getMargenMinimoPct());
        dto.setMargenObjetivoPct(plantilla.getMargenObjetivoPct());
        dto.setObservacionesInternas(plantilla.getObservacionesInternas());
        dto.setFotoActual(plantilla.getFotoPrincipal());
        dto.setComponentesJson(writeJson(plantilla.getComponentes().stream()
                .sorted(Comparator.comparing(PlantillaComponentePersonalizado::getOrden))
                .map(this::mapearComponentePlantilla)
                .toList()));
        dto.setRangosJson(writeJson(plantilla.getRangos().stream()
                .sorted(Comparator.comparing(PlantillaRangoPrecio::getCantidadMin))
                .map(this::mapearRangoPlantilla)
                .toList()));
        return dto;
    }

    @Transactional
    public PlantillaPersonalizada guardarPlantilla(PlantillaPersonalizadaFormDTO dto, MultipartFile foto) throws Exception {
        validarCodigoUnicoPlantilla(dto.getCodigoModelo(), dto.getId());
        validarSlugUnicoPlantilla(dto.getSlug(), dto.getId());

        PlantillaPersonalizada plantilla = dto.getId() != null
                ? plantillaRepository.findDetalleById(dto.getId()).orElseThrow(() -> new RuntimeException("Plantilla no encontrada"))
                : new PlantillaPersonalizada();

        plantilla.setCodigoModelo(normalizarCodigo(dto.getCodigoModelo()));
        plantilla.setNombreComercial(normalizarTitulo(dto.getNombreComercial()));
        plantilla.setSlug(normalizarSlug(dto.getSlug(), dto.getNombreComercial()));
        plantilla.setCategoria(valorO(dto.getCategoria(), "PERSONALIZADO").toUpperCase(Locale.ROOT));
        plantilla.setColeccionOcasion(normalizarTexto(dto.getColeccionOcasion()).toUpperCase(Locale.ROOT));
        plantilla.setDescripcionComercial(normalizarTexto(dto.getDescripcionComercial()));
        plantilla.setActivo(dto.getActivo() == null || dto.getActivo());
        plantilla.setVisibleWeb(dto.getVisibleWeb() == null || dto.getVisibleWeb());
        plantilla.setVendibleDirecto(dto.getVendibleDirecto() == null || dto.getVendibleDirecto());
        plantilla.setPermitePersonalizacion(dto.getPermitePersonalizacion() == null || dto.getPermitePersonalizacion());
        plantilla.setMargenMinimoPct(nz(dto.getMargenMinimoPct()));
        plantilla.setMargenObjetivoPct(nz(dto.getMargenObjetivoPct()));
        plantilla.setObservacionesInternas(normalizarTexto(dto.getObservacionesInternas()));

        if (foto != null && !foto.isEmpty()) {
            plantilla.setFotoPrincipal(uploadStorageService.guardarImagen(foto));
        } else if (dto.getFotoActual() != null && !dto.getFotoActual().isBlank()) {
            plantilla.setFotoPrincipal(dto.getFotoActual());
        }

        plantilla.getComponentes().clear();
        for (ComponentePlantillaJson row : parseJson(dto.getComponentesJson(), new TypeReference<List<ComponentePlantillaJson>>() {})) {
            if (row == null || !row.esValido()) continue;
            PlantillaComponentePersonalizado componente = new PlantillaComponentePersonalizado();
            componente.setPlantilla(plantilla);
            componente.setTipoOrigen(valorO(row.getTipoOrigen(), "MANUAL"));
            componente.setDescripcionManual(normalizarTexto(row.getDescripcionManual()));
            componente.setCantidadBase(nzPositivo(row.getCantidadBase(), BigDecimal.ONE));
            componente.setTipoComponente(valorO(row.getTipoComponente(), "BASE"));
            componente.setIncluidoPorDefecto(row.getIncluidoPorDefecto() == null || row.getIncluidoPorDefecto());
            componente.setEditableCantidad(row.getEditableCantidad() == null || row.getEditableCantidad());
            componente.setPuedeEliminarse(Boolean.TRUE.equals(row.getPuedeEliminarse()));
            componente.setOrden(row.getOrden() != null ? row.getOrden() : 0);
            if (row.getInsumoPersonalizadoId() != null) {
                insumoRepository.findById(row.getInsumoPersonalizadoId()).ifPresent(componente::setInsumoPersonalizado);
            }
            if (row.getAdicionalPersonalizadoId() != null) {
                adicionalRepository.findById(row.getAdicionalPersonalizadoId()).ifPresent(componente::setAdicionalPersonalizado);
            }
            plantilla.getComponentes().add(componente);
        }

        plantilla.getRangos().clear();
        for (RangoPlantillaJson row : parseJson(dto.getRangosJson(), new TypeReference<List<RangoPlantillaJson>>() {})) {
            if (row == null || row.getCantidadMin() == null || row.getCantidadMin() <= 0) continue;
            PlantillaRangoPrecio rango = new PlantillaRangoPrecio();
            rango.setPlantilla(plantilla);
            rango.setCantidadMin(row.getCantidadMin());
            rango.setCantidadMax(row.getCantidadMax());
            rango.setMargenMinimoPct(nz(row.getMargenMinimoPct()));
            rango.setMargenObjetivoPct(nz(row.getMargenObjetivoPct()));
            rango.setCargoFijo(nz(row.getCargoFijo()));
            rango.setDescuentoMayorPct(row.getDescuentoMayorPct());
            rango.setActivo(row.getActivo() == null || row.getActivo());
            plantilla.getRangos().add(rango);
        }

        return plantillaRepository.save(plantilla);
    }

    @Transactional
    public PlantillaPersonalizada duplicarPlantilla(Long id) {
        PlantillaPersonalizada original = plantillaRepository.findDetalleById(id)
                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada"));
        PlantillaPersonalizada copia = new PlantillaPersonalizada();
        copia.setCodigoModelo(normalizarCodigo(original.getCodigoModelo() + "-COPIA-" + (int) (Math.random() * 1000)));
        copia.setNombreComercial(original.getNombreComercial() + " COPIA");
        copia.setSlug(normalizarSlug(original.getSlug() + "-copia-" + System.currentTimeMillis(), original.getSlug()));
        copia.setCategoria(original.getCategoria());
        copia.setColeccionOcasion(original.getColeccionOcasion());
        copia.setDescripcionComercial(original.getDescripcionComercial());
        copia.setFotoPrincipal(original.getFotoPrincipal());
        copia.setActivo(true);
        copia.setVisibleWeb(original.getVisibleWeb());
        copia.setVendibleDirecto(original.getVendibleDirecto());
        copia.setPermitePersonalizacion(original.getPermitePersonalizacion());
        copia.setMargenMinimoPct(original.getMargenMinimoPct());
        copia.setMargenObjetivoPct(original.getMargenObjetivoPct());
        copia.setObservacionesInternas(original.getObservacionesInternas());
        for (PlantillaComponentePersonalizado componenteOriginal : original.getComponentes()) {
            PlantillaComponentePersonalizado componente = new PlantillaComponentePersonalizado();
            componente.setPlantilla(copia);
            componente.setTipoOrigen(componenteOriginal.getTipoOrigen());
            componente.setInsumoPersonalizado(componenteOriginal.getInsumoPersonalizado());
            componente.setAdicionalPersonalizado(componenteOriginal.getAdicionalPersonalizado());
            componente.setDescripcionManual(componenteOriginal.getDescripcionManual());
            componente.setCantidadBase(componenteOriginal.getCantidadBase());
            componente.setTipoComponente(componenteOriginal.getTipoComponente());
            componente.setIncluidoPorDefecto(componenteOriginal.getIncluidoPorDefecto());
            componente.setEditableCantidad(componenteOriginal.getEditableCantidad());
            componente.setPuedeEliminarse(componenteOriginal.getPuedeEliminarse());
            componente.setOrden(componenteOriginal.getOrden());
            copia.getComponentes().add(componente);
        }
        for (PlantillaRangoPrecio rangoOriginal : original.getRangos()) {
            PlantillaRangoPrecio rango = new PlantillaRangoPrecio();
            rango.setPlantilla(copia);
            rango.setCantidadMin(rangoOriginal.getCantidadMin());
            rango.setCantidadMax(rangoOriginal.getCantidadMax());
            rango.setMargenMinimoPct(rangoOriginal.getMargenMinimoPct());
            rango.setMargenObjetivoPct(rangoOriginal.getMargenObjetivoPct());
            rango.setCargoFijo(rangoOriginal.getCargoFijo());
            rango.setDescuentoMayorPct(rangoOriginal.getDescuentoMayorPct());
            rango.setActivo(rangoOriginal.getActivo());
            copia.getRangos().add(rango);
        }
        return plantillaRepository.save(copia);
    }

    @Transactional
    public void alternarEstadoPlantilla(Long id) {
        PlantillaPersonalizada plantilla = plantillaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada"));
        plantilla.setActivo(!Boolean.TRUE.equals(plantilla.getActivo()));
        plantillaRepository.save(plantilla);
    }

    public List<Map<String, Object>> buscarPlantillas(String termino) {
        String valor = normalizarTexto(termino);
        List<PlantillaPersonalizada> plantillas = valor.isBlank()
                ? plantillaRepository.findAllByOrderByActivoDescNombreComercialAsc().stream().filter(p -> Boolean.TRUE.equals(p.getActivo())).toList()
                : plantillaRepository.buscarActivas(valor);
        return plantillas.stream().map(p -> Map.<String, Object>of(
                "id", p.getId(),
                "codigoModelo", p.getCodigoModelo(),
                "nombreComercial", p.getNombreComercial(),
                "categoria", p.getCategoria(),
                "fotoPrincipal", p.getFotoPrincipal() != null ? p.getFotoPrincipal() : "",
                "visibleWeb", Boolean.TRUE.equals(p.getVisibleWeb())
        )).toList();
    }

    public List<CategoriaAdicionalPersonalizado> listarCategoriasAdicionales() {
        return categoriaRepository.findAllByOrderByOrdenAscNombreAsc();
    }

    public List<AdicionalPersonalizado> listarAdicionales() {
        return adicionalRepository.findAllByOrderByActivoDescNombreAsc();
    }

    public List<ZonaEntregaPersonalizado> listarZonas() {
        return zonaRepository.findAllByOrderByActivoDescDepartamentoAscProvinciaAscDistritoAsc();
    }

    @Transactional
    public CategoriaAdicionalPersonalizado guardarCategoria(Long id, String codigo, String nombre, String descripcion, Integer orden, Boolean activo) {
        CategoriaAdicionalPersonalizado categoria = id != null
                ? categoriaRepository.findById(id).orElseThrow(() -> new RuntimeException("Categoría no encontrada"))
                : new CategoriaAdicionalPersonalizado();
        categoria.setCodigo(normalizarCodigo(codigo));
        categoria.setNombre(normalizarTitulo(nombre));
        categoria.setDescripcion(normalizarTexto(descripcion));
        categoria.setOrden(orden != null ? orden : 0);
        categoria.setActivo(activo == null || activo);
        return categoriaRepository.save(categoria);
    }

    @Transactional
    public AdicionalPersonalizado guardarAdicional(Long id, String codigo, String nombre, Long categoriaId, String tipoOrigen,
                                                   Long insumoId, BigDecimal costoManual, BigDecimal precioBase,
                                                   Boolean editablePrecio, Boolean editableCantidad, Boolean activo,
                                                   String descripcion) {
        AdicionalPersonalizado adicional = id != null
                ? adicionalRepository.findById(id).orElseThrow(() -> new RuntimeException("Adicional no encontrado"))
                : new AdicionalPersonalizado();
        adicional.setCodigo(normalizarCodigo(codigo));
        adicional.setNombre(normalizarTitulo(nombre));
        adicional.setCategoriaAdicional(categoriaId != null ? categoriaRepository.findById(categoriaId).orElse(null) : null);
        adicional.setTipoOrigen(valorO(tipoOrigen, "MANUAL"));
        adicional.setInsumoPersonalizado(insumoId != null ? insumoRepository.findById(insumoId).orElse(null) : null);
        adicional.setCostoManual(nz(costoManual));
        adicional.setPrecioBase(nz(precioBase));
        adicional.setEditablePrecio(editablePrecio == null || editablePrecio);
        adicional.setEditableCantidad(editableCantidad == null || editableCantidad);
        adicional.setActivo(activo == null || activo);
        adicional.setDescripcion(normalizarTexto(descripcion));
        return adicionalRepository.save(adicional);
    }

    @Transactional
    public ZonaEntregaPersonalizado guardarZona(Long id, String departamento, String provincia, String distrito,
                                                BigDecimal tarifaBase, Integer plazoEstimadoDias, Boolean activo) {
        ZonaEntregaPersonalizado zona = id != null
                ? zonaRepository.findById(id).orElseThrow(() -> new RuntimeException("Zona no encontrada"))
                : new ZonaEntregaPersonalizado();
        zona.setDepartamento(normalizarTitulo(departamento));
        zona.setProvincia(normalizarTitulo(provincia));
        zona.setDistrito(normalizarTitulo(distrito));
        zona.setTarifaBase(nz(tarifaBase));
        zona.setPlazoEstimadoDias(plazoEstimadoDias != null ? plazoEstimadoDias : 1);
        zona.setActivo(activo == null || activo);
        return zonaRepository.save(zona);
    }

    @Transactional
    public Compra registrarCompraPersonalizada(CompraPersonalizadaDTO dto) {
        CompraDTO compraDTO = new CompraDTO();
        compraDTO.setProveedorId(dto.getProveedorId());
        compraDTO.setTipoComprobante(valorO(dto.getTipoComprobante(), "FACTURA"));
        compraDTO.setNumeroComprobante(normalizarTexto(dto.getNumeroComprobante()));
        compraDTO.setObservaciones(normalizarTexto(dto.getObservaciones()));

        List<CompraDTO.DetalleDTO> items = new ArrayList<>();
        for (CompraPersonalizadaDTO.ItemDTO item : dto.getItems()) {
            if (item.getInsumoId() == null) continue;

            InsumoPersonalizado insumo = insumoRepository.findDetalleById(item.getInsumoId())
                    .orElseThrow(() -> new RuntimeException("Insumo personalizado no encontrado"));
            PresentacionCompra presentacion = item.getPresentacionId() != null
                    ? presentacionRepository.findById(item.getPresentacionId()).orElse(null)
                    : null;

            CompraDTO.DetalleDTO detalle = new CompraDTO.DetalleDTO();
            detalle.setProductoId(insumo.getProducto().getId());
            detalle.setTipoCatalogo("INSUMO_PERSONALIZADO");

            BigDecimal cantidadPresentacion = nzPositivo(item.getCantidadPresentacion(), BigDecimal.ONE);
            BigDecimal totalPagado = nzPositivo(item.getTotalPagado(), BigDecimal.ZERO);
            if (totalPagado.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Debe indicar el total pagado de cada línea.");
            }

            if (presentacion != null) {
                detalle.setPresentacionNombre(presentacion.getNombrePresentacion());
                detalle.setCantidadPresentacion(cantidadPresentacion);
                detalle.setFactorPresentacion(presentacion.getFactorBase());
                detalle.setPrecioPorPresentacion(totalPagado.divide(cantidadPresentacion, 2, RoundingMode.HALF_UP));
            } else {
                detalle.setCantidad(cantidadPresentacion.setScale(0, RoundingMode.HALF_UP).intValue());
                detalle.setCosto(totalPagado.divide(BigDecimal.valueOf(detalle.getCantidad()), 4, RoundingMode.HALF_UP));
            }

            items.add(detalle);
        }

        compraDTO.setItems(items);
        return compraService.guardarCompra(compraDTO).getCompra();
    }

    private void configurarProductoSombra(Producto producto, InsumoPersonalizado insumo) {
        producto.setNombre(insumo.getNombre());
        producto.setCodigoInterno(generarCodigoInternoSombra(insumo.getCodigo(), producto.getId()));
        if (producto.getCodigoBarra() == null || producto.getCodigoBarra().isBlank()) {
            producto.setCodigoBarra("PERS-" + System.currentTimeMillis());
        }
        producto.setCategoria(insumo.getCategoria());
        producto.setDescripcion(insumo.getDescripcion());
        producto.setTags(insumo.getTags());
        producto.setClasificacion(Producto.CLASIFICACION_INSUMO);
        producto.setOrigenCatalogo(Producto.ORIGEN_CATALOGO_PERSONALIZADO);
        producto.setTipo("ESTANDAR");
        producto.setUnidadMedida(insumo.getUnidadBase());
        producto.setTipoAfectacionIgv("GRAVADO");
        if (producto.getPrecioVenta() == null) producto.setPrecioVenta(BigDecimal.ZERO);
        if (producto.getPrecioMayorista() == null) producto.setPrecioMayorista(BigDecimal.ZERO);
        if (producto.getStockMinimo() == null) producto.setStockMinimo(0);
        if (producto.getStockActual() == null) producto.setStockActual(0);
        producto.setActivo(Boolean.TRUE.equals(insumo.getActivo()));
    }

    private void guardarPresentacionesInsumo(InsumoPersonalizado insumo, String json) {
        presentacionRepository.deleteByInsumoPersonalizadoId(insumo.getId());
        for (PresentacionJson row : parseJson(json, new TypeReference<List<PresentacionJson>>() {})) {
            if (row == null || !row.esValida()) continue;
            PresentacionCompra presentacion = new PresentacionCompra();
            presentacion.setTipoCatalogo("INSUMO_PERSONALIZADO");
            presentacion.setInsumoPersonalizado(insumo);
            presentacion.setNombrePresentacion(normalizarTitulo(row.getNombrePresentacion()));
            presentacion.setUnidadMedidaPresentacion(valorO(row.getUnidadMedidaPresentacion(), insumo.getUnidadBase()).toUpperCase(Locale.ROOT));
            presentacion.setFactorBase(nzPositivo(row.getFactorBase(), BigDecimal.ONE));
            presentacion.setPermiteDecimal(Boolean.TRUE.equals(row.getPermiteDecimal()));
            presentacion.setPredeterminada(Boolean.TRUE.equals(row.getPredeterminada()));
            presentacion.setActiva(row.getActiva() == null || row.getActiva());
            presentacion.setOrden(row.getOrden() != null ? row.getOrden() : 0);
            presentacionRepository.save(presentacion);
        }
    }

    private List<Map<String, Object>> obtenerPresentacionesEdicion(Long insumoId) {
        return presentacionRepository.findByInsumoPersonalizadoId(insumoId).stream()
                .sorted(Comparator.comparing(PresentacionCompra::getOrden))
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("nombrePresentacion", p.getNombrePresentacion());
                    row.put("unidadMedidaPresentacion", p.getUnidadMedidaPresentacion());
                    row.put("factorBase", p.getFactorBase());
                    row.put("permiteDecimal", p.getPermiteDecimal());
                    row.put("predeterminada", p.getPredeterminada());
                    row.put("activa", p.getActiva());
                    row.put("orden", p.getOrden());
                    return row;
                }).toList();
    }

    private Map<String, Object> mapearComponentePlantilla(PlantillaComponentePersonalizado componente) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tipoOrigen", componente.getTipoOrigen());
        row.put("insumoPersonalizadoId", componente.getInsumoPersonalizado() != null ? componente.getInsumoPersonalizado().getId() : null);
        row.put("adicionalPersonalizadoId", componente.getAdicionalPersonalizado() != null ? componente.getAdicionalPersonalizado().getId() : null);
        row.put("descripcionManual", componente.getDescripcionManual());
        row.put("cantidadBase", componente.getCantidadBase());
        row.put("tipoComponente", componente.getTipoComponente());
        row.put("incluidoPorDefecto", componente.getIncluidoPorDefecto());
        row.put("editableCantidad", componente.getEditableCantidad());
        row.put("puedeEliminarse", componente.getPuedeEliminarse());
        row.put("orden", componente.getOrden());
        return row;
    }

    private Map<String, Object> mapearRangoPlantilla(PlantillaRangoPrecio rango) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cantidadMin", rango.getCantidadMin());
        row.put("cantidadMax", rango.getCantidadMax());
        row.put("margenMinimoPct", rango.getMargenMinimoPct());
        row.put("margenObjetivoPct", rango.getMargenObjetivoPct());
        row.put("cargoFijo", rango.getCargoFijo());
        row.put("descuentoMayorPct", rango.getDescuentoMayorPct());
        row.put("activo", rango.getActivo());
        return row;
    }

    private void validarCodigoUnicoInsumo(String codigo, Long actualId) {
        insumoRepository.findByCodigo(normalizarCodigo(codigo))
                .filter(existing -> !existing.getId().equals(actualId))
                .ifPresent(existing -> { throw new RuntimeException("Ya existe un insumo con ese código."); });
    }

    private void validarSlugUnicoInsumo(String slug, Long actualId) {
        String normal = normalizarSlug(slug, slug);
        insumoRepository.findBySlugBusqueda(normal)
                .filter(existing -> !existing.getId().equals(actualId))
                .ifPresent(existing -> { throw new RuntimeException("Ya existe un insumo con ese slug."); });
    }

    private void validarCodigoUnicoPlantilla(String codigo, Long actualId) {
        plantillaRepository.findByCodigoModelo(normalizarCodigo(codigo))
                .filter(existing -> !existing.getId().equals(actualId))
                .ifPresent(existing -> { throw new RuntimeException("Ya existe una plantilla con ese código de modelo."); });
    }

    private void validarSlugUnicoPlantilla(String slug, Long actualId) {
        String normal = normalizarSlug(slug, slug);
        plantillaRepository.findBySlug(normal)
                .filter(existing -> !existing.getId().equals(actualId))
                .ifPresent(existing -> { throw new RuntimeException("Ya existe una plantilla con ese slug."); });
    }

    private String generarCodigoInternoSombra(String codigo, Long productoId) {
        String base = "PERS-" + normalizarCodigo(codigo);
        Optional<Producto> existente = productoRepository.findByCodigoInterno(base);
        if (existente.isEmpty() || existente.get().getId().equals(productoId)) {
            return base;
        }
        return base + "-" + (productoId != null ? productoId : System.currentTimeMillis());
    }

    private <T> List<T> parseJson(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo procesar la configuración del módulo Personalizado.");
        }
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("No se pudo serializar JSON de personalizado: {}", e.getMessage());
            return "[]";
        }
    }

    private String normalizarTexto(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizarTitulo(String value) {
        return normalizarTexto(value).toUpperCase(Locale.ROOT);
    }

    private String normalizarCodigo(String value) {
        String limpio = normalizarTexto(value).toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9\\-_/]", "-")
                .replaceAll("-{2,}", "-");
        if (limpio.isBlank()) throw new RuntimeException("Debe indicar un código válido.");
        return limpio;
    }

    private String normalizarSlug(String slug, String fallback) {
        String base = normalizarTexto(slug).isBlank() ? normalizarTexto(fallback) : normalizarTexto(slug);
        base = base.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (base.isBlank()) throw new RuntimeException("Debe indicar un slug válido.");
        return base;
    }

    private String valorO(String value, String defecto) {
        String limpio = normalizarTexto(value);
        return limpio.isBlank() ? defecto : limpio;
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal nzPositivo(BigDecimal value, BigDecimal defecto) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) return defecto;
        return value;
    }

    @Data
    private static class PresentacionJson {
        private String nombrePresentacion;
        private String unidadMedidaPresentacion;
        private BigDecimal factorBase;
        private Boolean permiteDecimal;
        private Boolean predeterminada;
        private Boolean activa;
        private Integer orden;

        public boolean esValida() {
            return nombrePresentacion != null && !nombrePresentacion.isBlank()
                    && factorBase != null && factorBase.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    @Data
    private static class ComponentePlantillaJson {
        private String tipoOrigen;
        private Long insumoPersonalizadoId;
        private Long adicionalPersonalizadoId;
        private String descripcionManual;
        private BigDecimal cantidadBase;
        private String tipoComponente;
        private Boolean incluidoPorDefecto;
        private Boolean editableCantidad;
        private Boolean puedeEliminarse;
        private Integer orden;

        public boolean esValido() {
            return (descripcionManual != null && !descripcionManual.isBlank())
                    || insumoPersonalizadoId != null
                    || adicionalPersonalizadoId != null;
        }
    }

    @Data
    private static class RangoPlantillaJson {
        private Integer cantidadMin;
        private Integer cantidadMax;
        private BigDecimal margenMinimoPct;
        private BigDecimal margenObjetivoPct;
        private BigDecimal cargoFijo;
        private BigDecimal descuentoMayorPct;
        private Boolean activo;
    }
}
