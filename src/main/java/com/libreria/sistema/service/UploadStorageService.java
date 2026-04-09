package com.libreria.sistema.service;

import com.libreria.sistema.util.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

@Service
public class UploadStorageService {

    @Value("${app.upload-dir}")
    private String uploadDir;

    public String guardarImagen(MultipartFile imagen) throws IOException {
        if (imagen == null || imagen.isEmpty()) {
            return null;
        }

        if (imagen.getSize() > Constants.MAX_FILE_SIZE) {
            throw new IllegalArgumentException("El archivo es demasiado grande. Tamaño máximo: 10MB");
        }

        String contentType = imagen.getContentType();
        if (contentType == null || !Arrays.asList(Constants.ALLOWED_IMAGE_MIME_TYPES).contains(contentType)) {
            throw new IllegalArgumentException("Tipo de archivo no permitido. Solo se permiten imágenes JPG, PNG y WEBP");
        }

        String nombreOriginal = imagen.getOriginalFilename();
        if (nombreOriginal == null || !nombreOriginal.contains(".")) {
            throw new IllegalArgumentException("Nombre de archivo inválido");
        }

        String extension = nombreOriginal.substring(nombreOriginal.lastIndexOf(".")).toLowerCase();
        if (!Arrays.asList(Constants.ALLOWED_IMAGE_EXTENSIONS).contains(extension)) {
            throw new IllegalArgumentException("Extensión de archivo no permitida. Solo: JPG, JPEG, PNG, WEBP");
        }

        Path rootPath = Paths.get(uploadDir).toAbsolutePath();
        if (!Files.exists(rootPath)) {
            Files.createDirectories(rootPath);
        }

        String nombreUnico = UUID.randomUUID() + extension;
        Files.copy(imagen.getInputStream(), rootPath.resolve(nombreUnico));
        return nombreUnico;
    }
}
