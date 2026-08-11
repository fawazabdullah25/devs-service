package org.kstacks.devs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties("devs")
public record WebProperties(String frontendOrigins) {
    public List<String> allowedOrigins() {
        if (frontendOrigins == null || frontendOrigins.isBlank()) return List.of("http://localhost:3000");
        return Arrays.stream(frontendOrigins.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
    }
}
