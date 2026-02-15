package com.libreria.sistema.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Mapea la URL "/uploads/**" a la carpeta física de uploads (configurable)
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }

    /**
     * Bean de RestTemplate para realizar llamadas HTTP
     * Utilizado por FacturacionElectronicaService para comunicarse con APISUNAT
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}