package com.libreria.sistema.controller;

import com.libreria.sistema.repository.KardexRepository;
import com.libreria.sistema.repository.ProductoRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class InventarioControllerTest {

    @Test
    void vistaAjusteDebeRedirigirAlControlDeStock() {
        InventarioController controller = new InventarioController(
                mock(ProductoRepository.class),
                mock(KardexRepository.class));

        assertEquals("redirect:/stock", controller.vistaAjuste(null));
    }
}
