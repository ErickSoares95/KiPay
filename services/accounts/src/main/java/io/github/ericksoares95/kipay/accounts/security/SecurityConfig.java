package io.github.ericksoares95.kipay.accounts.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            ProblemDetailAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers(EndpointRequest.to("health", "prometheus")).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    /**
     * Production decoder. The issuer discovery and signing keys are fetched on the first token, so the context starts without
     * Keycloak running.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${kipay.security.audience}") String audience) {
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
            decoder.setJwtValidator(jwtValidator(issuer, audience));
            return decoder;
        });
    }

    /**
     * Validator shared by production and tests: default checks (expiration, not-before), issuer and audience.
     */
    public static OAuth2TokenValidator<Jwt> jwtValidator(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(audience));
    }
}
