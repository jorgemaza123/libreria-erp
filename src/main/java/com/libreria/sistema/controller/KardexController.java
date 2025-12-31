package com.libreria.sistema.controller;

import com.libreria.sistema.model.Kardex;
import com.libreria.sistema.repository.KardexRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/kardex")
public class KardexController {

    private final KardexRepository kardexRepository;

    public KardexController(KardexRepository kardexRepository) {
        this.kardexRepository = kardexRepository;
    }

    @GetMapping
    public String verKardex(@RequestParam(defaultValue = "0") int page, Model model) {
        // 1. Paginación de la tabla (Igual que antes)
        Pageable pageRequest = PageRequest.of(page, 20, Sort.by("id").descending());
        Page<Kardex> pageKardex = kardexRepository.findAll(pageRequest);

        model.addAttribute("movimientos", pageKardex);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageKardex.getTotalPages());

        // 2. Datos para Gráficos y Tarjetas (NUEVO)
        long entradas = kardexRepository.countByTipo("ENTRADA");
        long salidas = kardexRepository.countByTipo("SALIDA");
        long ajustes = kardexRepository.countByTipo("AJUSTE");

        model.addAttribute("countEntradas", entradas);
        model.addAttribute("countSalidas", salidas);
        model.addAttribute("countAjustes", ajustes);
        model.addAttribute("countTotal", entradas + salidas + ajustes);

        return "kardex/lista";
    }
}