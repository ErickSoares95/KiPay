package io.github.ericksoares95.kipay.accounts.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import io.github.ericksoares95.kipay.accounts.TestcontainersConfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises the request validation inside the Spring context (constructor-injected {@code Clock} in the constraint
 * validator, Jackson 3 deserialization) through the test-only probe route; the production controller is task 5.3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ErrorProbeController.class })
class AccountOpeningRequestApiTests {

    // 2026-06-15T01:00Z: already 15/06 in UTC, still 14/06 22:00 in Sao Paulo.
    private static final Instant NOW = Instant.parse("2026-06-15T01:00:00Z");
    private static final String URL = "/test-probe/account-opening";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;

    @Autowired
    AccountOpeningRequestApiTests(MockMvc mockMvc, JsonMapper jsonMapper) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
    }

    private MockHttpServletResponse send(String name, String birthDate) throws Exception {
        String body = "{\"fullName\":\"" + name + "\",\"cpf\":\"52998224725\",\"birthDate\":\"" + birthDate + "\"}";
        return mockMvc.perform(post(URL).with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private void assertValidationError(MockHttpServletResponse response, String... fields) throws Exception {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("errors").findValuesAsString("field")).containsExactlyInAnyOrder(fields);
    }

    @Test
    @DisplayName("data de nascimento futura é recusada com VALIDATION_ERROR e o campo birthDate, no contexto Spring")
    void futureBirthDateIsRejected() throws Exception {
        MockHttpServletResponse response = send("Ana Souza", "2030-01-01");

        assertValidationError(response, "birthDate");
        assertThat(response.getContentAsString()).doesNotContain("2030");
    }

    @Test
    @DisplayName("data de nascimento igual ao dia UTC, que em São Paulo ainda é amanhã, é recusada como futura")
    void utcDayBirthDateIsFutureInSaoPaulo() throws Exception {
        assertValidationError(send("Ana Souza", "2026-06-15"), "birthDate");
    }

    @Test
    @DisplayName("data de nascimento igual ao dia de São Paulo é aceita")
    void saoPauloDayBirthDateIsAccepted() throws Exception {
        MockHttpServletResponse response = send("Ana Souza", "2026-06-14");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("data de nascimento em formato inválido vira 400 VALIDATION_ERROR com o campo birthDate e sem eco do valor")
    void malformedBirthDateIsValidationError() throws Exception {
        for (String malformed : new String[] { "31/12/2000", "abc" }) {
            MockHttpServletResponse response = send("Ana Souza", malformed);

            assertValidationError(response, "birthDate");
            assertThat(response.getContentAsString()).doesNotContain(malformed);
        }
    }

    @Test
    @DisplayName("nome em branco e data malformada são indicados juntos")
    void blankNameWithMalformedBirthDateReportsBothFields() throws Exception {
        assertValidationError(send("   ", "abc"), "fullName", "birthDate");
    }

    @Test
    @DisplayName("data de nascimento ausente ou vazia é indicada como campo birthDate")
    void missingOrEmptyBirthDateIsRejected() throws Exception {
        MockHttpServletResponse missing = mockMvc.perform(post(URL).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Ana Souza\",\"cpf\":\"52998224725\"}")).andReturn().getResponse();

        assertValidationError(missing, "birthDate");
        assertValidationError(send("Ana Souza", ""), "birthDate");
    }

    @Test
    @DisplayName("JSON quebrado vira 400 VALIDATION_ERROR com o campo body e sem eco do conteúdo")
    void brokenJsonIsValidationError() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post(URL).with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\": \"segredo-123\", "))
                .andReturn().getResponse();

        assertValidationError(response, "body");
        assertThat(response.getContentAsString()).doesNotContain("segredo-123");
    }

    @Test
    @DisplayName("campo de tipo errado vira 400 VALIDATION_ERROR com o nome do campo e sem eco do valor")
    void wrongTypeFieldIsValidationError() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post(URL).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":{\"x\":\"segredo-123\"},\"birthDate\":\"1990-01-01\"}"))
                .andReturn().getResponse();

        assertValidationError(response, "fullName");
        assertThat(response.getContentAsString()).doesNotContain("segredo-123");
    }

    @Test
    @DisplayName("nome em branco e data futura são indicados juntos")
    void blankNameWithFutureBirthDateReportsBothFields() throws Exception {
        assertValidationError(send("   ", "2030-01-01"), "fullName", "birthDate");
    }
}
