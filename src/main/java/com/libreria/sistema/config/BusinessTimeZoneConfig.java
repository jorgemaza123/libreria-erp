package com.libreria.sistema.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class BusinessTimeZoneConfig {

    @Value("${app.timezone:America/Lima}")
    private String appTimezone;

    @PostConstruct
    public void configureDefaultTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(appTimezone)));
    }
}
