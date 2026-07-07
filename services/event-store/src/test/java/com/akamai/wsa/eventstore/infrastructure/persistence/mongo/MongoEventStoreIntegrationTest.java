package com.akamai.wsa.eventstore.infrastructure.persistence.mongo;

import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.port.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static com.akamai.wsa.eventstore.testsupport.StoredEventFixtures.storedEvent;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "wsa.storage=mongo",
        "spring.kafka.listener.auto-startup=false"
})
@Testcontainers
class MongoEventStoreIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_CONTAINER = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_CONTAINER::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> "wsa");
    }

    @Autowired
    EventStore eventStore;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clearCollection() {
        mongoTemplate.dropCollection(StoredEventDocument.class);
        ((MongoEventStore) eventStore).ensureIndexes();
    }

    @Test
    void resolvesMongoEventStoreAdapter() {
        assertThat(eventStore).isInstanceOf(MongoEventStore.class);
    }

    @Test
    void savesAndCountsAndFindsByConfigId() {
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227), storedEvent("evt-2", 99999)));

        assertThat(eventStore.countAll()).isEqualTo(2);
        assertThat(eventStore.findByConfigId(14227)).extracting(StoredEvent::eventId).containsExactly("evt-1");
    }

    @Test
    void upsertsIdempotentlyOnDuplicateEventId() {
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227)));
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227)));

        assertThat(eventStore.countAll()).isEqualTo(1);
    }

    @Test
    void persistsSharedContractShapeKeyedByEventId() {
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227)));

        StoredEventDocument document = mongoTemplate.findById("evt-1", StoredEventDocument.class);
        assertThat(document).isNotNull();
        assertThat(document.eventId()).isEqualTo("evt-1");
        assertThat(document.rule().severity().name()).isEqualTo("CRITICAL");
        assertThat(document.action().name()).isEqualTo("DENY");
        assertThat(document.geoLocation().country()).isEqualTo("CN");
    }

    @Test
    void createsSharedContractIndexes() {
        List<String> indexNames = mongoTemplate.indexOps(StoredEventDocument.class).getIndexInfo()
                .stream()
                .map(indexInfo -> indexInfo.getName())
                .toList();

        assertThat(indexNames).contains("configId_timestamp", "clientIp_timestamp", "timestamp");
    }
}
