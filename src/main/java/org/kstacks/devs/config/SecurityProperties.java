package org.kstacks.devs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties("devs.security")
public record SecurityProperties(
    boolean jwtEnabled,
    boolean allowInsecureAdmin,
    String publicKey,
    String issuer,
    Set<String> adminRoles,
    Set<String> studentRoles,
    Set<String> adminSubjects
) {
    public SecurityProperties {
        publicKey = publicKey == null ? "" : publicKey.trim();
        issuer = issuer == null ? "" : issuer.trim();
        adminRoles = adminRoles == null ? Set.of("DEVS_ADMIN", "ADMIN") : Set.copyOf(adminRoles);
        studentRoles = studentRoles == null ? Set.of("STUDENT") : Set.copyOf(studentRoles);
        adminSubjects = adminSubjects == null ? Set.of() : Set.copyOf(adminSubjects);
    }
}
