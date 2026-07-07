package com.akamai.wsa.eventstore.infrastructure.persistence.inmemory;

import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.akamai.wsa.eventstore.testsupport.StoredEventFixtures.storedEvent;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventStoreTest {

    private final InMemoryEventStore eventStore = new InMemoryEventStore();

    @Test
    void savesAndCountsEvents() {
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227), storedEvent("evt-2", 14227)));
        assertThat(eventStore.countAll()).isEqualTo(2);
    }

    @Test
    void findsByConfigId() {
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227), storedEvent("evt-2", 99999)));
        assertThat(eventStore.findByConfigId(14227)).extracting(StoredEvent::eventId).containsExactly("evt-1");
    }

    @Test
    void deduplicatesByEventIdOnRedelivery() {
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227)));
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227)));
        assertThat(eventStore.countAll()).isEqualTo(1);
    }
}
