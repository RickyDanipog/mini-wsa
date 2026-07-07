package com.akamai.wsa.eventstore.infrastructure.persistence.postgres;

import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static com.akamai.wsa.eventstore.testsupport.StoredEventFixtures.storedEvent;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresEventStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine");

    private PostgresEventStore postgresEventStore;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES_CONTAINER.getJdbcUrl(),
                POSTGRES_CONTAINER.getUsername(),
                POSTGRES_CONTAINER.getPassword());
        dataSource.setDriverClassName(POSTGRES_CONTAINER.getDriverClassName());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS events");
        postgresEventStore = new PostgresEventStore(jdbcTemplate);
        postgresEventStore.ensureSchema();
    }

    @Test
    void savesAndCountsAndFindsByConfigId() {
        postgresEventStore.saveAll(List.of(storedEvent("evt-1", 14227), storedEvent("evt-2", 99999)));

        assertThat(postgresEventStore.countAll()).isEqualTo(2);
        assertThat(postgresEventStore.findByConfigId(14227))
                .extracting(StoredEvent::eventId)
                .containsExactly("evt-1");
    }

    @Test
    void writesIdempotentlyOnDuplicateEventId() {
        postgresEventStore.saveAll(List.of(storedEvent("evt-1", 14227)));
        postgresEventStore.saveAll(List.of(storedEvent("evt-1", 14227)));

        assertThat(postgresEventStore.countAll()).isEqualTo(1);
    }

    @Test
    void roundTripsSharedContractShape() {
        postgresEventStore.saveAll(List.of(storedEvent("evt-1", 14227)));

        StoredEvent stored = postgresEventStore.findByConfigId(14227).getFirst();
        assertThat(stored.eventId()).isEqualTo("evt-1");
        assertThat(stored.timestamp()).isEqualTo(storedEvent("evt-1", 14227).timestamp());
        assertThat(stored.rule().id()).isEqualTo("950001");
        assertThat(stored.rule().severity().name()).isEqualTo("CRITICAL");
        assertThat(stored.rule().category().name()).isEqualTo("INJECTION");
        assertThat(stored.action().name()).isEqualTo("DENY");
        assertThat(stored.geoLocation().country()).isEqualTo("CN");
        assertThat(stored.geoLocation().city()).isEqualTo("Beijing");
        assertThat(stored.threatScore()).isEqualTo(75);
        assertThat(stored.receivedAt()).isEqualTo(storedEvent("evt-1", 14227).receivedAt());
    }
}
