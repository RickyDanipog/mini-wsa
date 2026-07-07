package com.akamai.wsa.enrichment.infrastructure.window;

import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "wsa.storage", havingValue = "redis")
public class RedisOffenderWindow implements OffenderWindow {

    private static final String KEY_PREFIX = "wsa:enrichment:offender:";
    private static final Duration WINDOW_LENGTH = Duration.ofMinutes(10);
    private static final Duration TTL_MARGIN = Duration.ofMinutes(1);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisOffenderWindow(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void recordEvent(String clientIp, Instant receivedAt) {
        String clientKey = keyForClient(clientIp);
        long epochMillis = receivedAt.toEpochMilli();
        stringRedisTemplate.opsForZSet().add(clientKey, Long.toString(epochMillis), epochMillis);
        stringRedisTemplate.expire(clientKey, WINDOW_LENGTH.plus(TTL_MARGIN));
    }

    @Override
    public long countRecentEventsFromClient(String clientIp, Duration window, Instant asOf) {
        String clientKey = keyForClient(clientIp);
        long asOfMillis = asOf.toEpochMilli();
        long windowStartMillis = asOf.minus(window).toEpochMilli();
        stringRedisTemplate.opsForZSet().removeRangeByScore(clientKey, Double.NEGATIVE_INFINITY, windowStartMillis - 1);
        Long recentCount = stringRedisTemplate.opsForZSet().count(clientKey, windowStartMillis, asOfMillis);
        return recentCount == null ? 0L : recentCount;
    }

    private static String keyForClient(String clientIp) {
        return KEY_PREFIX + clientIp;
    }
}
