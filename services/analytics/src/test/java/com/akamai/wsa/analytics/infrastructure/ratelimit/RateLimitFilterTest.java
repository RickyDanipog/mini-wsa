package com.akamai.wsa.analytics.infrastructure.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "wsa.rate-limit.max-requests=2",
        "wsa.rate-limit.window-seconds=60"
})
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsTooManyRequestsAfterLimitExceededOnStatsEndpoint() throws Exception {
        mockMvc.perform(get("/v1/stats/summary").with(request -> {
            request.setRemoteAddr("198.51.100.7");
            return request;
        })).andExpect(status().isOk());
        mockMvc.perform(get("/v1/stats/summary").with(request -> {
            request.setRemoteAddr("198.51.100.7");
            return request;
        })).andExpect(status().isOk());

        mockMvc.perform(get("/v1/stats/summary").with(request -> {
            request.setRemoteAddr("198.51.100.7");
            return request;
        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[0]").value("rate limit exceeded: 2 requests per 60 seconds"));
    }

    @Test
    void neverLimitsPingEndpoint() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(get("/v1/ping").with(request -> {
                request.setRemoteAddr("203.0.113.9");
                return request;
            })).andExpect(status().isOk());
        }
    }
}
