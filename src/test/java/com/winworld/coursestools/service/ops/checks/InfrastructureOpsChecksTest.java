package com.winworld.coursestools.service.ops.checks;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfrastructureOpsChecksTest {

    @Test
    void lokiQueryUriEncodesTheLogQlSelector() {
        Instant now = Instant.parse("2026-11-05T13:00:00Z");

        URI uri = InfrastructureOpsChecks.lokiNewestLineUri("http://77.232.135.132:3100", now);

        assertEquals("/loki/api/v1/query_range", uri.getPath());
        assertTrue(uri.getRawQuery().startsWith("query=%7Bcontainer_name%3D%22backend%22%7D&"), uri.getRawQuery());
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);
        assertTrue(query.contains("query={container_name=\"backend\"}"), query);
        assertTrue(query.contains("limit=1"), query);
        assertTrue(query.contains("direction=backward"), query);
        assertTrue(query.contains("end=" + now.toEpochMilli() * 1_000_000), query);
    }
}
