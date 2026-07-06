package com.akamai.wsa.interfaces.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1")
class PingController {

    @GetMapping("/ping")
    Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
