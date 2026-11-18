package com.papertrail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.papertrail.config.CrossrefProperties;

/**
 * Phase 1 (T001–T005) smoke tests: the full application starts against a real
 * PostgreSQL (Testcontainers), Flyway runs, Hibernate validates, and the
 * operational endpoints are reachable.
 * Note: @Autowired on test fields is the standard JUnit/Spring test idiom; production
 * code uses constructor injection only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PaperTrailApplicationTests {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CrossrefProperties crossrefProperties;

    @Test
    void t001_contextLoads_withFlywayAndHibernateValidate() {
        // T001/T002: if Flyway or ddl-auto=validate failed, the context would not start.
        assertThat(rest).isNotNull();
    }

    @Test
    void t001_constitution7_healthEndpointReportsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void t001_constitution4_openApiDocsAreGenerated() {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"openapi\"");
    }

    @Test
    void t001_constitution4_swaggerUiIsServed() {
        ResponseEntity<String> response = rest.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void t002_fr002_crossrefPropertiesBoundFromConfiguration() {
        assertThat(crossrefProperties.baseUrl()).isEqualTo("https://api.crossref.org");
        assertThat(crossrefProperties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(crossrefProperties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
    }
}
