package io.github.ericksoares95.kipay.accounts.security;

import java.time.Instant;
import java.util.List;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signs tokens with an RSA pair generated in the test, so no Keycloak is needed. The decoder reuses the production
 * validator ({@link SecurityConfig#jwtValidator}).
 */
public final class TestTokens {

    public static final String ISSUER = "http://localhost:8080/realms/kipay";
    public static final String AUDIENCE = "accounts";
    public static final String SUBJECT = "11111111-2222-3333-4444-555555555555";

    private static final String KEY_ID = "test-key";

    private final RSAKey signingKey = generate();
    private final RSAKey otherKey = generate();

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (com.nimbusds.jose.JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public JwtDecoder decoder() {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
            decoder.setJwtValidator(SecurityConfig.jwtValidator(ISSUER, AUDIENCE));
            return decoder;
        } catch (com.nimbusds.jose.JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String valid() {
        return sign(signingKey, ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(600));
    }

    public String expired() {
        return sign(signingKey, ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(3600));
    }

    public String otherAudience() {
        return sign(signingKey, ISSUER, List.of("other-service"), Instant.now().plusSeconds(600));
    }

    public String otherIssuer() {
        return sign(signingKey, "http://evil.example/realms/kipay", List.of(AUDIENCE),
                Instant.now().plusSeconds(600));
    }

    public String badSignature() {
        return sign(otherKey, ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(600));
    }

    private static String sign(RSAKey key, String issuer, List<String> audience, Instant expiresAt) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        Instant issuedAt = expiresAt.minusSeconds(900);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(SUBJECT)
                .audience(audience)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
