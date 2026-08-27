package org.kstacks.devs.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    CorsConfigurationSource corsConfigurationSource(WebProperties properties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "If-Match"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/devs/**", configuration);
        return source;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "devs.security.jwt-enabled", havingValue = "true")
    static class JwtSecurity {
        @Bean
        JwtDecoder jwtDecoder(SecurityProperties properties) {
            if (properties.publicKey().isBlank()) {
                throw new IllegalStateException("DEVS_JWT_PUBLIC_KEY is required when DEVS_JWT_ENABLED=true");
            }
            try {
                var encodedKey = properties.publicKey()
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
                var keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey));
                var publicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(keySpec);
                var jwk = new ECKey.Builder(Curve.P_256, publicKey)
                        .algorithm(JWSAlgorithm.ES256)
                        .keyID("access")
                        .build();
                var decoder = NimbusJwtDecoder
                        .withJwkSource(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)))
                        .jwsAlgorithm(SignatureAlgorithm.ES256)
                        .build();
                if (!properties.issuer().isBlank()) {
                    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
                }
                return decoder;
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "DEVS_JWT_PUBLIC_KEY must be an ES256 X.509 public key in Base64 or PEM form", exception);
            }
        }

        @Bean
        SecurityFilterChain jwtFilterChain(
                HttpSecurity http,
                SecurityProperties properties,
                @Qualifier("corsConfigurationSource") CorsConfigurationSource cors) throws Exception {
            var adminAuthorities = properties.adminRoles().stream()
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .toArray(String[]::new);
            http
                    .csrf(csrf -> csrf.disable())
                    .cors(configurer -> configurer.configurationSource(cors))
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .dispatcherTypeMatchers(
                                    DispatcherType.FORWARD,
                                    DispatcherType.ERROR)
                            .permitAll()
                            .requestMatchers(
                                    "/actuator/health/**",
                                    "/devs/api/v1/public/**",
                                    "/devs/api/v1/webhooks/mux")
                            .permitAll()
                            .requestMatchers("/devs/api/v1/admin/**").hasAnyAuthority(adminAuthorities)
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(resource -> resource.jwt(
                            jwt -> jwt.jwtAuthenticationConverter(new RolesConverter(properties))));
            return http.build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "devs.security.jwt-enabled", havingValue = "false", matchIfMissing = true)
    static class LockedSecurity {
        @Bean
        SecurityFilterChain lockedFilterChain(
                HttpSecurity http,
                SecurityProperties properties,
                @Qualifier("corsConfigurationSource") CorsConfigurationSource cors) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .cors(configurer -> configurer.configurationSource(cors))
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> {
                        auth.dispatcherTypeMatchers(
                                DispatcherType.FORWARD,
                                DispatcherType.ERROR).permitAll();

                        auth.requestMatchers(
                                "/actuator/health/**",
                                "/devs/api/v1/public/**",
                                "/devs/api/v1/webhooks/mux").permitAll();

                        if (properties.allowInsecureAdmin()) {
                            auth.requestMatchers("/devs/api/v1/admin/**").permitAll();
                        } else {
                            auth.requestMatchers("/devs/api/v1/admin/**").denyAll();
                        }

                        auth.anyRequest().denyAll();
                    })
                    .httpBasic(httpBasic -> httpBasic.disable())
                    .formLogin(form -> form.disable());
            return http.build();
        }
    }

    static final class RolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {
        private final SecurityProperties properties;

        RolesConverter(SecurityProperties properties) {
            this.properties = properties;
        }

        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            var authorities = new ArrayList<SimpleGrantedAuthority>();
            addRoles(authorities, jwt.getClaimAsStringList("roles"));
            var realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
                addRoles(authorities, roles.stream().map(Object::toString).toList());
            }
            var scopes = jwt.getClaimAsString("scope");
            if (scopes != null) {
                scopes.lines().flatMap(line -> List.of(line.split(" ")).stream())
                        .filter(scope -> !scope.isBlank())
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                        .forEach(authorities::add);
            }
            if (properties.adminSubjects().contains(jwt.getSubject())) {
                addRoles(authorities, properties.adminRoles());
            }
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        }

        private void addRoles(List<SimpleGrantedAuthority> authorities, Collection<String> roles) {
            if (roles == null)
                return;
            roles.stream().filter(role -> role != null && !role.isBlank())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
    }
}
