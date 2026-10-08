package io.github.ericksoares95.kipay.accounts.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.ericksoares95.kipay.accounts.TestcontainersConfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ErrorProbeController.class })
class ProblemDetailTests {

    private static final String REJECTED_VALUE = "rejected-value-123";

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;

    @Autowired
    ProblemDetailTests(MockMvc mockMvc, JsonMapper jsonMapper) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
    }

    @Test
    @DisplayName("um erro de validação sai em ProblemDetail RFC 9457 com status 400, code VALIDATION_ERROR e a lista de campos inválidos")
    void validationErrorIsProblemDetailWithFieldList() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/test-probe/validation")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"document\":\"" + REJECTED_VALUE + "\"}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("title").asString()).isEqualTo("Bad Request");
        assertThat(body.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("errors")).hasSize(2);
        assertThat(body.get("errors").findValuesAsString("field")).containsExactlyInAnyOrder("name", "document");
        assertThat(response.getContentAsString()).doesNotContain(REJECTED_VALUE);
    }

    @Test
    @DisplayName("uma falha de acesso ao banco vira 503 em ProblemDetail com o code SERVICE_UNAVAILABLE e sem a mensagem do driver")
    void databaseFailureIsServiceUnavailable() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/test-probe/db-failure").with(jwt()))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(503);
        assertThat(body.get("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getContentAsString()).doesNotContain("internal-host").doesNotContain("jdbc");
    }
}
