package com.libreria.sistema.service;

import com.libreria.sistema.model.ServicioCategoria;
import com.libreria.sistema.model.dto.ServicioCategoriaDTO;
import com.libreria.sistema.repository.ServicioCategoriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicioCategoriaServiceTest {

    @Mock
    private ServicioCategoriaRepository servicioCategoriaRepository;

    @InjectMocks
    private ServicioCategoriaService servicioCategoriaService;

    @Test
    void creaTipoNuevoDesdeTextoLibreParaOrdenes() {
        when(servicioCategoriaRepository.findByCodigo("ARREGLO_FLORAL")).thenReturn(Optional.empty());
        when(servicioCategoriaRepository.findByNombreIgnoreCase("Arreglo Floral")).thenReturn(Optional.empty());
        when(servicioCategoriaRepository.findMaxOrden()).thenReturn(90);
        when(servicioCategoriaRepository.save(any(ServicioCategoria.class))).thenAnswer(invocation -> {
            ServicioCategoria categoria = invocation.getArgument(0);
            categoria.setId(10L);
            return categoria;
        });

        ServicioCategoria categoria = servicioCategoriaService.obtenerOCrearTipoParaOrden("arreglo floral");

        assertThat(categoria.getCodigo()).isEqualTo("ARREGLO_FLORAL");
        assertThat(categoria.getNombre()).isEqualTo("Arreglo Floral");
        assertThat(categoria.getOrden()).isEqualTo(100);
        assertThat(categoria.getActiva()).isTrue();
    }

    @Test
    void reutilizaYReactivaCategoriaExistentePorCodigo() {
        ServicioCategoria existente = new ServicioCategoria();
        existente.setId(3L);
        existente.setCodigo("SUBLIMACION");
        existente.setNombre("Sublimacion");
        existente.setActiva(false);

        when(servicioCategoriaRepository.findByCodigo("SUBLIMACION")).thenReturn(Optional.of(existente));
        when(servicioCategoriaRepository.save(existente)).thenReturn(existente);

        ServicioCategoria categoria = servicioCategoriaService.obtenerOCrearTipoParaOrden("SUBLIMACION");

        assertThat(categoria.getId()).isEqualTo(3L);
        assertThat(categoria.getActiva()).isTrue();
        verify(servicioCategoriaRepository).save(existente);
    }

    @Test
    void noPermiteGuardarDosTiposConElMismoNombre() {
        ServicioCategoria existente = new ServicioCategoria();
        existente.setId(5L);
        existente.setNombre("Impresion");

        ServicioCategoriaDTO dto = new ServicioCategoriaDTO();
        dto.setNombre("impresion");

        when(servicioCategoriaRepository.findByNombreIgnoreCase("Impresion")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> servicioCategoriaService.guardarTipo(dto))
                .hasMessageContaining("Ya existe un tipo de trabajo");
    }
}
