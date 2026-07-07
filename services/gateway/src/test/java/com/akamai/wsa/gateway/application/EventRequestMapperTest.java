package com.akamai.wsa.gateway.application;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.contracts.Severity;
import com.akamai.wsa.gateway.interfaces.rest.dto.GeoLocationDto;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.akamai.wsa.gateway.interfaces.rest.dto.RuleDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventRequestMapperTest {

    private final EventRequestMapper mapper = new EventRequestMapper();

    @Test
    void mapsEveryFieldOntoTheContract() {
        IngestEventRequest ingestEventRequest = new IngestEventRequest(
                "evt-00132", Instant.parse("2026-05-20T14:32:10Z"), 14227, "pol_web1",
                "203.0.113.42", "www.example.com", "/api/v1/login", "POST", 403, "Mozilla/5.0",
                new RuleDto("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                        Severity.CRITICAL, AttackCategory.INJECTION),
                Action.DENY, new GeoLocationDto("CN", "Beijing"), 1024, 256);

        RawEventMessage rawEventMessage = mapper.toRawEventMessage(ingestEventRequest);

        assertThat(rawEventMessage.eventId()).isEqualTo("evt-00132");
        assertThat(rawEventMessage.timestamp()).isEqualTo(Instant.parse("2026-05-20T14:32:10Z"));
        assertThat(rawEventMessage.configId()).isEqualTo(14227);
        assertThat(rawEventMessage.clientIp()).isEqualTo("203.0.113.42");
        assertThat(rawEventMessage.rule().category()).isEqualTo(AttackCategory.INJECTION);
        assertThat(rawEventMessage.rule().severity()).isEqualTo(Severity.CRITICAL);
        assertThat(rawEventMessage.action()).isEqualTo(Action.DENY);
        assertThat(rawEventMessage.geoLocation().country()).isEqualTo("CN");
        assertThat(rawEventMessage.requestSize()).isEqualTo(1024);
        assertThat(rawEventMessage.responseSize()).isEqualTo(256);
    }

    @Test
    void mapsNullGeoLocationToNull() {
        IngestEventRequest ingestEventRequest = new IngestEventRequest(
                "evt-2", Instant.parse("2026-05-20T14:32:10Z"), 1, null,
                "10.0.0.1", null, "/", null, 200, null,
                new RuleDto("r1", "rule", null, Severity.LOW, AttackCategory.BOT),
                Action.MONITOR, null, 0, 0);

        RawEventMessage rawEventMessage = mapper.toRawEventMessage(ingestEventRequest);

        assertThat(rawEventMessage.geoLocation()).isNull();
    }
}
