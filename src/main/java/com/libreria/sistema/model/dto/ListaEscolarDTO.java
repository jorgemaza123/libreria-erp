package com.libreria.sistema.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * DTO para creación y actualización de Listas Escolares.
 */
@Data
public class ListaEscolarDTO {

    private Long id;

    // --- DATOS DEL ALUMNO ---
    @NotBlank(message = "El nombre del alumno es obligatorio")
    @Size(min = 3, max = 200, message = "El nombre debe tener entre 3 y 200 caracteres")
    private String nombreAlumno;

    @NotBlank(message = "El grado es obligatorio")
    @Size(max = 50, message = "El grado no debe exceder 50 caracteres")
    private String grado;

    @Size(max = 20, message = "La sección no debe exceder 20 caracteres")
    private String seccion;

    @Size(max = 200, message = "El colegio no debe exceder 200 caracteres")
    private String colegio;

    private Integer anioEscolar;

    // --- DATOS DE CONTACTO ---
    private Long clienteId;

    @Size(max = 200, message = "El nombre de contacto no debe exceder 200 caracteres")
    private String contactoNombre;

    @Size(max = 20, message = "El teléfono no debe exceder 20 caracteres")
    private String contactoTelefono;

    @Email(message = "El email no es válido")
    @Size(max = 100, message = "El email no debe exceder 100 caracteres")
    private String contactoEmail;

    // --- TEXTO ORIGINAL ---
    @NotBlank(message = "El texto de la lista es obligatorio")
    private String textoOriginal;

    @Size(max = 50)
    private String fuenteTexto = "OCR_WHATSAPP";
}
