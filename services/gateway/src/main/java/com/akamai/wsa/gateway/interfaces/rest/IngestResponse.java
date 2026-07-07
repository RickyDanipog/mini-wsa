package com.akamai.wsa.gateway.interfaces.rest;

/** Success body for a fully-accepted ingest request. */
public record IngestResponse(int acceptedCount) {
}
