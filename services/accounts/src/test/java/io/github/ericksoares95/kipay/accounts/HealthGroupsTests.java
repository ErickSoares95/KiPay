package io.github.ericksoares95.kipay.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
class HealthGroupsTests {

    private final int managementPort;
    private final JsonMapper jsonMapper;
    private final HealthEndpointGroups healthEndpointGroups;
    private final Environment environment;

    @Autowired
    HealthGroupsTests(@LocalManagementPort int managementPort, JsonMapper jsonMapper,
            HealthEndpointGroups healthEndpointGroups, Environment environment) {
        this.managementPort = managementPort;
        this.jsonMapper = jsonMapper;
        this.healthEndpointGroups = healthEndpointGroups;
        this.environment = environment;
    }

    @Test
    @DisplayName("o readiness responde UP na porta de management com o banco no ar")
    void readinessIsUp() throws Exception {
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + managementPort + "/actuator/health/readiness")).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jsonMapper.readTree(response.body()).get("status").asString()).isEqualTo("UP");
    }

    @Test
    @DisplayName("o grupo readiness tem readinessState e db, e não tem o Kafka")
    void readinessGroupMembers() {
        HealthEndpointGroup readiness = healthEndpointGroups.get("readiness");

        assertThat(readiness.isMember("readinessState")).isTrue();
        assertThat(readiness.isMember("db")).isTrue();
        assertThat(readiness.isMember("kafka")).isFalse();
    }

    @Test
    @DisplayName("o grupo liveness tem só o livenessState")
    void livenessGroupMembers() {
        HealthEndpointGroup liveness = healthEndpointGroups.get("liveness");

        assertThat(liveness.isMember("livenessState")).isTrue();
        assertThat(environment.getProperty("management.endpoint.health.group.liveness.include"))
                .isEqualTo("livenessState");
        assertThat(liveness.isMember("ping")).isFalse();
        assertThat(liveness.isMember("db")).isFalse();
        assertThat(liveness.isMember("kafka")).isFalse();
    }

    @Test
    @DisplayName("a probabilidade de amostragem efetiva do tracing é 1.0")
    void samplingProbabilityIsOne() {
        assertThat(environment.getProperty("management.tracing.sampling.probability", Double.class)).isEqualTo(1.0);
    }
}