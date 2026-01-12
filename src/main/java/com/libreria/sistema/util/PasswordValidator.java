package com.libreria.sistema.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Validador de contraseñas fuertes
 */
public class PasswordValidator {

    /**
     * Valida que una contraseña cumpla con los requisitos de seguridad
     * @param password La contraseña a validar
     * @return Lista de errores (vacía si la contraseña es válida)
     */
    public static List<String> validate(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("La contraseña no puede estar vacía");
            return errors;
        }

        // Longitud mínima
        if (password.length() < Constants.MIN_PASSWORD_LENGTH) {
            errors.add("La contraseña debe tener al menos " + Constants.MIN_PASSWORD_LENGTH + " caracteres");
        }

        // Longitud máxima razonable
        if (password.length() > 128) {
            errors.add("La contraseña no puede exceder 128 caracteres");
        }

        // Debe contener al menos una mayúscula
        if (!password.matches(".*[A-Z].*")) {
            errors.add("La contraseña debe contener al menos una letra mayúscula");
        }

        // Debe contener al menos una minúscula
        if (!password.matches(".*[a-z].*")) {
            errors.add("La contraseña debe contener al menos una letra minúscula");
        }

        // Debe contener al menos un dígito
        if (!password.matches(".*\\d.*")) {
            errors.add("La contraseña debe contener al menos un número");
        }

        // Debe contener al menos un carácter especial
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            errors.add("La contraseña debe contener al menos un carácter especial (!@#$%^&*()_+-=[]{};':\"\\|,.<>/?)");
        }

        // No debe contener espacios
        if (password.contains(" ")) {
            errors.add("La contraseña no debe contener espacios");
        }

        return errors;
    }

    /**
     * Valida y retorna un mensaje de error único o null si es válida
     */
    public static String validateAndGetMessage(String password) {
        List<String> errors = validate(password);
        if (errors.isEmpty()) {
            return null;
        }
        return String.join("; ", errors);
    }
}
