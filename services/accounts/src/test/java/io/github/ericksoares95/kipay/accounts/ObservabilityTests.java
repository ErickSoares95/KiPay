package io.github.ericksoares95.kipay.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityTests {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityTests.class);

    private final int serverPort;
    private final int managementPort;
    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;
    private final JsonMapper jsonMapper;

    @Autowired
    ObservabilityTests(@LocalServerPort int serverPort, @LocalManagementPort int managementPort,
            ObservationRegistry observationRegistry, Tracer tracer, JsonMapper jsonMapper) {
        this.serverPort = serverPort;
        this.managementPort = managementPort;
        this.observationRegistry = observationRegistry;
        this.tracer = tracer;
        this.jsonMapper = jsonMapper;
    }

    @Test
    @DisplayName("o /actuator/prometheus responde na porta de management com http_server_requests e não responde na porta da aplicação")
    void prometheusOnlyOnManagementPort() throws Exception {
        HttpResponse<String> onServerPort = get(serverPort, "/actuator/prometheus");
        HttpResponse<String> onManagementPort = get(managementPort, "/actuator/prometheus");

        assertThat(serverPort).isNotEqualTo(managementPort);
        // With the resource server the application port answers 401 to any route that is not public (was 404 before 3.1).
        assertThat(onServerPort.statusCode()).isEqualTo(401);
        assertThat(onManagementPort.statusCode()).isEqualTo(200);
        assertThat(onManagementPort.body()).contains("http_server_requests");
    }

    @Test
    @DisplayName("uma linha de log dentro de uma observação sai em JSON (ECS) com o traceId do span corrente")
    void logLineIsJsonWithTraceId(CapturedOutput output) {
        String[] traceId = new String[1];
        Observation.createNotStarted("kipay.test.log", observationRegistry).observe(() -> {
            traceId[0] = tracer.currentSpan().context().traceId();
            log.info("observability-log-line");
        });

        String line = output.getOut().lines()
                .filter(candidate -> candidate.contains("observability-log-line"))
                .findFirst()
                .orElseThrow();
        JsonNode json = jsonMapper.readTree(line);

        assertThat(traceId[0]).isNotBlank();
        assertThat(json.get("message").asString()).isEqualTo("observability-log-line");
        assertThat(json.get("traceId").asString()).isEqualTo(traceId[0]);
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
