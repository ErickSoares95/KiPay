package io.github.ericksoares95.kipay.accounts.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;

import io.github.ericksoares95.kipay.accounts.TestcontainersConfiguration;
import io.github.ericksoares95.kipay.accounts.error.ErrorProbeController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ErrorProbeController.class, SecurityTests.TokenConfiguration.class })
class SecurityTests {

    private static final String PROTECTED = "/test-probe/protected";

    @TestConfiguration(proxyBeanMethods = false)
    static class TokenConfiguration {

        @Bean
        TestTokens testTokens() {
            return new TestTokens();
        }

        @Bean
        @Primary
        JwtDecoder testJwtDecoder(TestTokens tokens) {
            return tokens.decoder();
        }
    }

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;
    private final TestTokens tokens;

    @Autowired
    SecurityTests(MockMvc mockMvc, JsonMapper jsonMapper, TestTokens tokens) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
        this.tokens = tokens;
    }

    private static final List<String> UNAUTHORIZED_BODIES = new ArrayList<>();

    private void assertUnauthorized(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("code").asString()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(response.getContentAsString()).doesNotContain("expired").doesNotContain("audience")
                .doesNotContain("issuer").doesNotContain("signature");
        synchronized (UNAUTHORIZED_BODIES) {
            UNAUTHORIZED_BODIES.add(response.getContentAsString());
            assertThat(UNAUTHORIZED_BODIES).allMatch(UNAUTHORIZED_BODIES.getFirst()::equals);
        }
    }

    private MockHttpServletResponse withToken(String token) throws Exception {
        return mockMvc.perform(get(PROTECTED).header("Authorization", "Bearer " + token)).andReturn().getResponse();
    }

    @Test
    @DisplayName("uma requisição sem token recebe 401 em ProblemDetail com o code AUTHENTICATION_REQUIRED")
    void missingTokenIsUnauthorized() throws Exception {
        assertUnauthorized(mockMvc.perform(get(PROTECTED)).andReturn().getResponse());
    }

    @Test
    @DisplayName("um token expirado recebe 401 em ProblemDetail com o code AUTHENTICATION_REQUIRED")
    void expiredTokenIsUnauthorized() throws Exception {
        assertUnauthorized(withToken(tokens.expired()));
    }

    @Test
    @DisplayName("um token de outra audiência recebe 401 em ProblemDetail com o code AUTHENTICATION_REQUIRED")
    void otherAudienceIsUnauthorized() throws Exception {
        assertUnauthorized(withToken(tokens.otherAudience()));
    }

    @Test
    @DisplayName("um token de outro issuer recebe 401 em ProblemDetail com o code AUTHENTICATION_REQUIRED")
    void otherIssuerIsUnauthorized() throws Exception {
        assertUnauthorized(withToken(tokens.otherIssuer()));
    }

    @Test
    @DisplayName("um token com assinatura inválida recebe 401 em ProblemDetail com o code AUTHENTICATION_REQUIRED")
    void badSignatureIsUnauthorized() throws Exception {
        assertUnauthorized(withToken(tokens.badSignature()));
    }

    @Test
    @DisplayName("um token válido passa pela autenticação e chega ao controller")
    void validTokenReachesController() throws Exception {
        MockHttpServletResponse response = withToken(tokens.valid());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(jsonMapper.readTree(response.getContentAsString()).get("sub").asString())
                .isEqualTo(TestTokens.SUBJECT);
    }

    @Test
    @DisplayName("uma rota sob /v3/api-docs não recebe 401 sem token (o caminho está liberado)")
    void apiDocsPathIsOpen() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/v3/api-docs/swagger-config")).andReturn()
                .getResponse();

        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("uma rota qualquer fora de /v3/api-docs sem token recebe 401")
    void otherRouteWithoutTokenIsUnauthorized() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/accounts")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(jsonMapper.readTree(response.getContentAsString()).get("code").asString())
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }
}
