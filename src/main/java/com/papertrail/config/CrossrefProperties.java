package com.papertrail.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * T002 [FR-002] Typed, externally configurable settings for the Crossref client.
 * Bound from {@code papertrail.crossref.*} in application.yml (or env vars such as
 * {@code PAPERTRAIL_CROSSREF_BASE_URL}). Used by CrossrefClientImpl in Phase 4.
 */
@ConfigurationProperties(prefix = "papertrail.crossref")
public record CrossrefProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout) {
}
