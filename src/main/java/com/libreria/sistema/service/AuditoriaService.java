package com.libreria.sistema.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.libreria.sistema.model.AuditoriaLog;
import com.libreria.sistema.repository.AuditoriaLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAuditoria(String modulo, String accion, String entidad, Long entidadId, String detalles) {
        registrarAuditoria(modulo, accion, entidad, entidadId, null, null, detalles);
    }

    /**
     * Obtiene el historial usando Specifications (Soluciona error PostgreSQL con nulos)
     */
    public Page<AuditoriaLog> obtenerHistorial(String usuario, String modulo, String accion,
                                               LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                               Pageable pageable) {
        
        Specification<AuditoriaLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (usuario != null && !usuario.isEmpty()) {
                predicates.add(cb.equal(root.get("usuario"), usuario));
            }
            if (modulo != null && !modulo.isEmpty()) {
                predicates.add(cb.equal(root.get("modulo"), modulo));
            }
            if (accion != null && !accion.isEmpty()) {
                predicates.add(cb.equal(root.get("accion"), accion));
            }
            if (fechaInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), fechaInicio));
            }
            if (fechaFin != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fechaHora"), fechaFin));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditoriaLogRepository.findAll(spec, pageable);
    }

    public List<AuditoriaLog> obtenerHistorialEntidad(String entidad, Long entidadId) {
        return auditoriaLogRepository.findByEntidadAndEntidadIdOrderByFechaHoraDesc(entidad, entidadId);
    }

    public String convertirAJson(Object objeto) {
        if (objeto == null) return null;
        try {
            return objectMapper.writeValueAsString(objeto);
        } catch (JsonProcessingException e) {
            log.error("Error al convertir objeto a JSON: {}", e.getMessage());
            return objeto.toString();
        }
    }

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
}