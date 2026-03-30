package com.originspecs.dataprep.config;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CliParser {

    private static final String USAGE = """
            Usage: java -jar DataPrep.jar [--source-artifact-id <uuid>] [columnThreshold]
            Processes all .xls files in src/main/resources/local-data/input/
            and writes to src/main/resources/local-data/output/ (same filenames).
            --source-artifact-id: Optional, at most once. When set, input must contain exactly one .xls file; the original is copied to
            src/main/resources/local-data/artifacts/<uuid>.<original extension> before processing.
            columnThreshold: Optional, 0.0–1.0 (default 0.01). Min fill ratio to keep a column.
            Example: java -jar target/DataPrep.jar 0.01
            Example: java -jar target/DataPrep.jar --source-artifact-id 550e8400-e29b-41d4-a716-446655440000
            Example: java -jar target/DataPrep.jar --source-artifact-id 550e8400-e29b-41d4-a716-446655440000 0.01
            """;

    /**
     * Parses CLI arguments into a validated Config, or logs error, prints usage and exits the process.
     */
    public static Config parseOrExit(String[] args) {

        try {
            Config config = ConfigParser.parse(args);
            ConfigValidator.validate(config);
            return config;
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Invalid arguments or environment: {}", e.getMessage());
            log.error(USAGE);
            System.exit(1);
            throw new AssertionError("unreachable: process should have exited", e);
        }
    }
}
