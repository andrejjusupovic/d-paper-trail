package com.papertrail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * T001 [FR: all] Application entry point.
 * {@code @SpringBootApplication} turns on component scanning of {@code com.papertrail.*}
 * and auto-configuration; {@code @ConfigurationPropertiesScan} picks up typed config records.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PaperTrailApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperTrailApplication.class, args);
    }
}
