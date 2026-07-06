package com.akamai.wsa.infrastructure.persistence.inmemory;

import com.akamai.wsa.domain.model.SecurityEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.akamai.wsa.testsupport.SecurityEventFixtures.enrichedEvent;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventStoreTest {

    private final InMemoryEventStore eventStore = new InMemoryEventStore();

    @Test
    void savesAndCountsEnrichedEvents() {
        eventStore.saveAll(List.of(
                enrichedEvent("evt-1", 14227, "203.0.113.42", "/api/v1/login"),
                enrichedEvent("evt-2", 14227, "203.0.113.43", "/admin")
        ));

        assertThat(eventStore.countAll()).isEqualTo(2);
    }

    @Test
    void findsEventsByConfigId() {
        eventStore.saveAll(List.of(
                enrichedEvent("evt-1", 14227, "203.0.113.42", "/api/v1/login"),
                enrichedEvent("evt-2", 99999, "203.0.113.43", "/admin")
        ));

        List<SecurityEvent> result = eventStore.findByConfigId(14227);

        assertThat(result).extracting(SecurityEvent::eventId).containsExactly("evt-1");
    }
}
