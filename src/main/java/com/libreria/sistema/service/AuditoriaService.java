package com.libreria.sistema.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.libreria.sistema.model.AuditoriaLog;
import com.libreria.sistema.repository.AuditoriaLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AuditoriaService {

    @Autowired
    private AuditoriaLogRepository auditoriaLogRepository;

    private final ObjectMapper objectMapper;

    public AuditoriaService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Registra una acción de auditoría de forma asíncrona
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAuditoria(String modulo, String accion, String entidad, Long entidadId,
                                   Object valorAnterior, Object valorNuevo, String detalles) {
        try {
            AuditoriaLog log = new AuditoriaLog();
            log.setUsuario(obtenerUsuarioActual());
            log.setModulo(modulo);
            log.setAccion(accion);
            log.setEntidad(entidad);
            log.setEntidadId(entidadId);
            log.setValorAnterior(convertirAJson(valorAnterior));
            log.setValorNuevo(convertirAJson(valorNuevo));
            log.setIpAddress(obtenerIpCliente());
            log.setDetalles(detalles);
            log.setFechaHora(LocalDateTime.now());

            auditoriaLogRepository.save(log);
        } catch (Exception e) {
            log.error("Error al registrar auditoría: {}", e.getMessage(), e);
        }
    }

    /**
     * Registra una auditoría con información simplificada
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAuditoria(String modulo, String accion, String entidad, Long entidadId, String detalles) {
        registrarAuditoria(modulo, accion, entidad, entidadId, null, null, detalles);
    }

    /**
     * Obtiene el historial de auditoría con filtros
     */
    public Page<AuditoriaLog> obtenerHistorial(String usuario, String modulo, String accion,
                                               LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                               Pageable pageable) {
        return auditoriaLogRepository.buscarConFiltros(usuario, modulo, accion, fechaInicio, fechaFin, pageable);
    }

    /**
     * Obtiene todas las auditorías paginadas
     */
    public Page<AuditoriaLog> obtenerTodas(Pageable pageable) {
        return auditoriaLogRepository.findAll(pageable);
    }

    /**
     * Obtiene el historial de un registro específico
     */
    public List<AuditoriaLog> obtenerHistorialEntidad(String entidad, Long entidadId) {
        return auditoriaLogRepository.findByEntidadAndEntidadIdOrderByFechaHoraDesc(entidad, entidadId);
    }

    /**
     * Obtiene las últimas 50 auditorías
     */
    public List<AuditoriaLog> obtenerUltimas() {
        return auditoriaLogRepository.findTop50ByOrderByFechaHoraDesc();
    }

    /**
     * Convierte un objeto a JSON
     */
    public String convertirAJson(Object objeto) {
        if (objeto == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(objeto);
        } catch (JsonProcessingException e) {
            log.error("Error al convertir objeto a JSON: {}", e.getMessage());
            return objeto.toString();
        }
    }

    /**
     * Crea un mapa con los cambios entre dos objetos
     */
    public Map<String, Object> crearMapaCambios(Object objetoAnterior, Object objetoNuevo) {
        Map<String, Object> cambios = new HashMap<>();
        cambios.put("anterior", objetoAnterior);
        cambios.put("nuevo", objetoNuevo);
        return cambios;
    }

    /**
     * Obtiene el nombre del usuario actual
     */
    private String obtenerUsuarioActual() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener el usuario actual: {}", e.getMessage());
        }
        return "SYSTEM";
    }

    /**
     * Obtiene la dirección IP del cliente
     */
    private String obtenerIpCliente() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener la IP del cliente: {}", e.getMessage());
        }
        return "UNKNOWN";
    }

    /**
     * Cuenta auditorías por usuario
     */
    public long contarPorUsuario(String usuario) {
        return auditoriaLogRepository.countByUsuario(usuario);
    }

    /**
     * Cuenta auditorías por módulo
     */
    public long contarPorModulo(String modulo) {
        return auditoriaLogRepository.countByModulo(modulo);
    }
}
