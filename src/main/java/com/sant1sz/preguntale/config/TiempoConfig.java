package com.sant1sz.preguntale.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TiempoConfig {

    @Bean
    public Clock reloj(@Value("${app.zona-horaria}") String zonaHoraria) {
        return Clock.system(ZoneId.of(zonaHoraria));
    }
}
