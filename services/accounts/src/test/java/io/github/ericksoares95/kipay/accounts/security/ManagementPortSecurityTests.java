package io.github.ericksoares95.kipay.accounts.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.github.ericksoares95.kipay.accounts.TestcontainersConfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
class ManagementPortSecurityTests {

    private final int managementPort;

    @Autowired
    ManagementPortSecurityTests(@LocalManagementPort int managementPort) {
        this.managementPort = managementPort;
    }

    @Test
    @DisplayName("o health e o Prometheus da porta de management respondem 200 sem token")
    void healthAndPrometheusAreOpen() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> health = get(client, "/actuator/health");
            HttpResponse<String> prometheus = get(client, "/actuator/prometheus");

            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(prometheus.statusCode()).isEqualTo(200);
            assertThat(prometheus.body()).contains("jvm_");
        }
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
