package com.akamai.wsa.enrichment.infrastructure.dedup;

import com.akamai.wsa.enrichment.domain.port.ProcessedEventLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "wsa.storage", havingValue = "redis")
public class RedisProcessedEventLog implements ProcessedEventLog {

    private static final String KEY_PREFIX = "wsa:enrichment:processed:";
    private static final Duration DEDUP_RETENTION = Duration.ofHours(1);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisProcessedEventLog(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean markProcessed(String eventId) {
        Boolean firstSight = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + eventId, "1", DEDUP_RETENTION);
        return Boolean.TRUE.equals(firstSight);
    }
}
