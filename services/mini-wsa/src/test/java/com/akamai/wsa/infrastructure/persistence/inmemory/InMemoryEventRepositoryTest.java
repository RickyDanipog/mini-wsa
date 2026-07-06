package com.akamai.wsa.infrastructure.persistence.inmemory;

import com.akamai.wsa.domain.model.SecurityEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventRepositoryTest {

    private final InMemoryEventRepository repository = new InMemoryEventRepository();

    @Test
    void savedEventsAreCounted() {
        repository.save(new SecurityEvent("evt-1", 14227, Instant.parse("2026-05-20T14:32:10Z")));
        repository.save(new SecurityEvent("evt-2", 14227, Instant.parse("2026-05-20T14:33:10Z")));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void findsByConfigId() {
        repository.save(new SecurityEvent("evt-1", 14227, Instant.parse("2026-05-20T14:32:10Z")));
        repository.save(new SecurityEvent("evt-2", 99999, Instant.parse("2026-05-20T14:33:10Z")));

        List<SecurityEvent> result = repository.findByConfigId(14227);

        assertThat(result).extracting(SecurityEvent::eventId).containsExactly("evt-1");
    }
}
