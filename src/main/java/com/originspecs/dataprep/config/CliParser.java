package com.originspecs.dataprep.config;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CliParser {

    private static final String USAGE = """
            Usage: java -jar DataPrep.jar [columnThreshold]
            Processes all .xls files in src/main/resources/local-data/input/
            and writes to src/main/resources/local-data/output/ (same filenames).
            columnThreshold: Optional, 0.0–1.0 (default 0.01). Min fill ratio to keep a column.
            Example: java -jar target/DataPrep.jar 0.01
            Example: java -jar target/DataPrep.jar
            """;

    /**
     * Parses CLI arguments into a validated Config, or logs error, prints usage and exits the process.
     */
    public static Config parseOrExit(String[] args) {

        try {
            Config config = Config.fromArgs(args);
            config.validate();
            return config;
        } catch (IllegalArgumentException e) {
            log.error("Invalid arguments: {}", e.getMessage());
            log.error(USAGE);
            System.exit(1);
            return null;
        }
    }
}
