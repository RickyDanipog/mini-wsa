package com.akamai.wsa.application.stats;

/**
 * A top-targeted-path row: the request path and how many events hit it.
 */
public record PathStatistics(String path, long count) {
}
