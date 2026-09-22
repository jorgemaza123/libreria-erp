package com.libreria.sistema.repository;

import com.libreria.sistema.model.OrdenServicio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrdenServicioRepositoryTest {

    @Autowired
    private OrdenServicioRepository ordenServicioRepository;

    @Test
    void tableroPriorizaTrabajosActivosPorPrioridadYEntrega() {
        OrdenServicio pendienteNormal = orden("Maqueta escolar", "MAQUETA", "PENDIENTE", "NORMAL", LocalDate.now());
        OrdenServicio urgente = orden("Video ceremonia", "EDICION", "EN_PROCESO", "URGENTE", LocalDate.now().plusDays(2));
        OrdenServicio listo = orden("Taza personalizada", "SUBLIMACION", "LISTO", "ALTA", LocalDate.now().plusDays(1));
        OrdenServicio entregado = orden("Curriculum", "IMPRESION", "ENTREGADO", "URGENTE", LocalDate.now().minusDays(1));

        ordenServicioRepository.save(pendienteNormal);
        ordenServicioRepository.save(urgente);
        ordenServicioRepository.save(listo);
        ordenServicioRepository.save(entregado);

        assertThat(ordenServicioRepository.findAllOrdenadasParaTablero())
                .extracting(OrdenServicio::getTituloTrabajo)
                .containsExactly("Video ceremonia", "Taza personalizada", "Maqueta escolar", "Curriculum");
    }

    @Test
    void tableroSubeRecordatoriosPendientesAntesDeLaPrioridad() {
        OrdenServicio urgente = orden("Video urgente", "EDICION", "EN_PROCESO", "URGENTE", LocalDate.now().plusDays(1));
        OrdenServicio conRecordatorio = orden("Llamar por maqueta", "MAQUETA", "PENDIENTE", "NORMAL", LocalDate.now().plusDays(3));
        conRecordatorio.setRecordatorioActivo(true);
        conRecordatorio.setFechaRecordatorio(LocalDate.now().minusDays(1));

        ordenServicioRepository.save(urgente);
        ordenServicioRepository.save(conRecordatorio);

        assertThat(ordenServicioRepository.findAllOrdenadasParaTablero())
                .extracting(OrdenServicio::getTituloTrabajo)
                .containsExactly("Llamar por maqueta", "Video urgente");
    }

    @Test
    void tiposDeTrabajoSeObtienenDesdeLoRegistrado() {
        ordenServicioRepository.save(orden("Pedido uno", "SUBLIMACION", "PENDIENTE", "NORMAL", null));
        ordenServicioRepository.save(orden("Pedido dos", "MAQUETA", "PENDIENTE", "NORMAL", null));
        ordenServicioRepository.save(orden("Pedido tres", "SUBLIMACION", "PENDIENTE", "NORMAL", null));

        assertThat(ordenServicioRepository.findTiposServicio())
                .containsExactly("MAQUETA", "SUBLIMACION");
    }

    private OrdenServicio orden(String titulo, String tipo, String estado, String prioridad, LocalDate entrega) {
        OrdenServicio orden = new OrdenServicio();
        orden.setTituloTrabajo(titulo);
        orden.setTipoServicio(tipo);
        orden.setEstado(estado);
        orden.setPrioridad(prioridad);
        orden.setFechaEntregaEstimada(entrega);
        orden.setClienteNombre("Cliente prueba");
        orden.setTotal(new BigDecimal("10.00"));
        orden.setACuenta(BigDecimal.ZERO);
        orden.setSaldo(new BigDecimal("10.00"));
        return orden;
    }
}
