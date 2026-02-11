package com.libreria.sistema.service;

import com.libreria.sistema.model.ActividadCRM;
import com.libreria.sistema.model.Cliente;
import com.libreria.sistema.model.Notificacion;
import com.libreria.sistema.model.OportunidadCRM;
import com.libreria.sistema.repository.ActividadCRMRepository;
import com.libreria.sistema.repository.ClienteRepository;
import com.libreria.sistema.repository.NotificacionRepository;
import com.libreria.sistema.repository.OportunidadCRMRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class CrmScheduledTasks {

    private final ActividadCRMRepository actividadRepository;
    private final ClienteRepository clienteRepository;
    private final OportunidadCRMRepository oportunidadRepository;
    private final NotificacionRepository notificacionRepository;

    public CrmScheduledTasks(ActividadCRMRepository actividadRepository,
                             ClienteRepository clienteRepository,
                             OportunidadCRMRepository oportunidadRepository,
                             NotificacionRepository notificacionRepository) {
        this.actividadRepository = actividadRepository;
        this.clienteRepository = clienteRepository;
        this.oportunidadRepository = oportunidadRepository;
        this.notificacionRepository = notificacionRepository;
    }

    /**
     * Verifica tareas CRM vencidas cada hora
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void verificarTareasVencidas() {
        try {
            List<ActividadCRM> vencidas = actividadRepository.findVencidas(LocalDateTime.now());
            for (ActividadCRM actividad : vencidas) {
                String entidadRef = "CRM_TAREA:" + actividad.getId();
                if (!notificacionRepository.existsByEntidadRelacionadaAndResueltaFalse(entidadRef)) {
                    Notificacion notif = Notificacion.builder()
                            .tipo("CRM_TAREA")
                            .titulo("Tarea CRM vencida: " + actividad.getAsunto())
                            .mensaje("La tarea para " + actividad.getCliente().getNombreRazonSocial() + " esta vencida")
                            .icono("fa-clock")
                            .color("text-danger")
                            .url("/crm/actividades")
                            .entidadRelacionada(entidadRef)
                            .prioridad("ALTA")
                            .build();

                    if (actividad.getAsignado() != null) {
                        notif.setUsuario(actividad.getAsignado());
                    }
                    notificacionRepository.save(notif);
                }
            }
            if (!vencidas.isEmpty()) {
                log.info("CRM: {} tareas vencidas notificadas", vencidas.size());
            }
        } catch (Exception e) {
            log.error("Error al verificar tareas CRM vencidas: {}", e.getMessage());
        }
    }

    /**
     * Verifica seguimientos programados para hoy (diario a las 8am)
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void verificarSeguimientosHoy() {
        try {
            LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
            LocalDateTime finHoy = LocalDate.now().atTime(23, 59, 59);

            List<ActividadCRM> programadasHoy = actividadRepository.findProgramadasEntre(inicioHoy, finHoy);
            if (!programadasHoy.isEmpty()) {
                Notificacion notif = Notificacion.builder()
                        .tipo("CRM_SEGUIMIENTO")
                        .titulo("Tienes " + programadasHoy.size() + " seguimiento(s) CRM para hoy")
                        .mensaje("Revisa tus tareas pendientes en el modulo CRM")
                        .icono("fa-calendar-check")
                        .color("text-info")
                        .url("/crm/actividades")
                        .prioridad("NORMAL")
                        .build();
                notificacionRepository.save(notif);
                log.info("CRM: {} seguimientos programados para hoy", programadasHoy.size());
            }
        } catch (Exception e) {
            log.error("Error al verificar seguimientos CRM: {}", e.getMessage());
        }
    }

    /**
     * Verifica clientes inactivos (lunes a las 9am)
     */
    @Scheduled(cron = "0 0 9 * * MON")
    @Transactional
    public void verificarClientesInactivos() {
        try {
            LocalDateTime hace30Dias = LocalDateTime.now().minusDays(30);
            List<Cliente> inactivos = clienteRepository.findLeadsInactivos(hace30Dias);
            if (!inactivos.isEmpty()) {
                Notificacion notif = Notificacion.builder()
                        .tipo("CRM_INACTIVO")
                        .titulo(inactivos.size() + " cliente(s) sin actividad en 30 dias")
                        .mensaje("Hay clientes que no han tenido interaccion reciente. Revisa en CRM > Segmentacion")
                        .icono("fa-user-clock")
                        .color("text-warning")
                        .url("/crm/segmentacion")
                        .prioridad("NORMAL")
                        .build();
                notificacionRepository.save(notif);
                log.info("CRM: {} clientes inactivos detectados", inactivos.size());
            }
        } catch (Exception e) {
            log.error("Error al verificar clientes inactivos: {}", e.getMessage());
        }
    }

    /**
     * Verifica leads sin convertir >15 dias (lunes a las 9am)
     */
    @Scheduled(cron = "0 5 9 * * MON")
    @Transactional
    public void verificarLeadsSinConvertir() {
        try {
            LocalDateTime hace15Dias = LocalDateTime.now().minusDays(15);
            List<Cliente> leadsSinConvertir = clienteRepository.findLeadsInactivos(hace15Dias);
            if (!leadsSinConvertir.isEmpty()) {
                Notificacion notif = Notificacion.builder()
                        .tipo("CRM_LEAD")
                        .titulo(leadsSinConvertir.size() + " lead(s) sin convertir en mas de 15 dias")
                        .mensaje("Estos leads necesitan seguimiento o descarte")
                        .icono("fa-user-plus")
                        .color("text-warning")
                        .url("/crm/leads")
                        .prioridad("ALTA")
                        .build();
                notificacionRepository.save(notif);
                log.info("CRM: {} leads sin convertir >15 dias", leadsSinConvertir.size());
            }
        } catch (Exception e) {
            log.error("Error al verificar leads sin convertir: {}", e.getMessage());
        }
    }
}
