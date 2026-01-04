package com.libreria.sistema.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Mapea la URL "/images/**" a la carpeta física "uploads" en la raíz del proyecto
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:./uploads/");
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