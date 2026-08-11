package org.kstacks.devs.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.GrantedAuthority;

import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RolesConverterTests {
    private final SecurityProperties properties = new SecurityProperties(
        true,
        false,
        "test-key",
        "kstacks",
        Set.of("DEVS_ADMIN", "ADMIN"),
        Set.of("STUDENT"),
        Set.of("admin-user-id")
    );
    private final SecurityConfiguration.RolesConverter converter = new SecurityConfiguration.RolesConverter(properties);

    @Test
    void convertsCentralRoleAndRealmClaims() {
        var jwt = jwt("student-user", Map.of(
            "roles", List.of("STUDENT"),
            "realm_access", Map.of("roles", List.of("DEVS_ADMIN"))
        ));

        assertThat(converter.convert(jwt).getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_DEVS_ADMIN");
    }

    @Test
    void grantsAdminOnlyToConfiguredCentralSubject() {
        assertThat(converter.convert(jwt("admin-user-id", Map.of())).getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_DEVS_ADMIN", "ROLE_ADMIN");
        assertThat(converter.convert(jwt("regular-user-id", Map.of())).getAuthorities()).isEmpty();
    }

    @Test
    void verifiesTheCentralEs256TokenShape() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var keys = generator.generateKeyPair();
        var publicKey = (ECPublicKey) keys.getPublic();
        var privateKey = (ECPrivateKey) keys.getPrivate();
        var configured = new SecurityProperties(
            true,
            false,
            Base64.getEncoder().encodeToString(publicKey.getEncoded()),
            "kstacks-test",
            Set.of("DEVS_ADMIN"),
            Set.of("STUDENT"),
            Set.of()
        );
        var token = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("access").build(),
            new JWTClaimsSet.Builder()
                .subject("central-user-id")
                .issuer("kstacks-test")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("type", "access")
                .build()
        );
        token.sign(new ECDSASigner(privateKey));

        var decoder = new SecurityConfiguration.JwtSecurity().jwtDecoder(configured);

        assertThat(decoder.decode(token.serialize()).getSubject()).isEqualTo("central-user-id");
    }

    private Jwt jwt(String subject, Map<String, Object> claims) {
        var now = Instant.now();
        var builder = Jwt.withTokenValue("test-token")
            .header("alg", "ES256")
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
