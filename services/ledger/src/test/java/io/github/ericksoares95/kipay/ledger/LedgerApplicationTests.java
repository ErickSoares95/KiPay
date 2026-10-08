package io.github.ericksoares95.kipay.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Instant;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.kafka.core.KafkaAdmin;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
class LedgerApplicationTests {

    private final int managementPort;
    private final JsonMapper jsonMapper;
    private final Flyway flyway;
    private final KafkaAdmin kafkaAdmin;
    private final AsyncTaskExecutor applicationTaskExecutor;

    @Autowired
    LedgerApplicationTests(@LocalManagementPort int managementPort, JsonMapper jsonMapper, Flyway flyway,
            KafkaAdmin kafkaAdmin, AsyncTaskExecutor applicationTaskExecutor) {
        this.managementPort = managementPort;
        this.jsonMapper = jsonMapper;
        this.flyway = flyway;
        this.kafkaAdmin = kafkaAdmin;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Test
    @DisplayName("o health da porta de management responde UP com PostgreSQL e Kafka do Testcontainers")
    void managementHealthIsUp() throws Exception {
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + "/actuator/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jsonMapper.readTree(response.body()).get("status").asString()).isEqualTo("UP");
    }

    @Test
    @DisplayName("o Flyway roda contra o PostgreSQL do Testcontainers")
    void flywayUsesPostgres() throws Exception {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        assertThat(flyway.info()).isNotNull();
    }

    @Test
    @DisplayName("o Kafka do Testcontainers responde ao KafkaAdmin")
    void kafkaIsReachable() {
        assertThat(kafkaAdmin.clusterId()).isNotBlank();
    }

    @Test
    @DisplayName("o JsonMapper do contexto é o do Jackson 3 e escreve datas em ISO-8601")
    void jsonMapperIsJackson3() {
        assertThat(jsonMapper.writeValueAsString(Instant.parse("2026-10-08T12:00:00Z")))
                .isEqualTo("\"2026-10-08T12:00:00Z\"");
    }

    @Test
    @DisplayName("as tarefas do executor da aplicação rodam em virtual threads")
    void applicationExecutorUsesVirtualThreads() throws Exception {
        assertThat(applicationTaskExecutor.submit(() -> Thread.currentThread().isVirtual()).get()).isTrue();
    }
}
