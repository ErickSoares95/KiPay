package io.github.ericksoares95.kipay.accounts.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.hibernate.generator.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidV7Tests {

    @Test
    @DisplayName("o UuidV7 gera UUID versão 7 com variante RFC 9562")
    void generatesVersion7WithRfcVariant() {
        UUID uuid = UuidV7.generate();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("os 48 bits iniciais do UuidV7 guardam o timestamp em milissegundos")
    void embedsTimestampInMillis() {
        long millis = 1_790_000_000_123L;

        UUID uuid = UuidV7.generate(millis);

        assertThat(uuid.getMostSignificantBits() >>> 16).isEqualTo(millis);
    }

    @Test
    @DisplayName("UuidV7 de instantes crescentes é ordenável no tempo")
    void orderedByTime() {
        List<UUID> ids = IntStream.range(0, 50).mapToObj(i -> UuidV7.generate(1_790_000_000_000L + i)).toList();

        assertThat(ids).isSorted();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("o gerador usado pelas entidades (@IdGeneratorType) produz UUID versão 7")
    void entityIdGeneratorProducesVersion7() throws Exception {
        GeneratedUuidV7 annotation = AccountHolder.class.getDeclaredField("id").getAnnotation(GeneratedUuidV7.class);
        UuidV7Generator generator = new UuidV7Generator(annotation);

        Object id = generator.generate(null, null, null, EventType.INSERT);

        assertThat(id).isInstanceOf(UUID.class);
        assertThat(((UUID) id).version()).isEqualTo(7);
        assertThat(generator.getEventTypes()).containsExactly(EventType.INSERT);
    }
}
