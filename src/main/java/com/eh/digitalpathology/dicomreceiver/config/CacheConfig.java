package com.eh.digitalpathology.dicomreceiver.config;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, String> studyBarcodeCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite( Duration.ofMinutes(5))
                .maximumSize(1000)
                .build();
    }
}
