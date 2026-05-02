package com.originspecs.dataprep.config;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CliParser {

    private static final String USAGE = """
            Usage: java -jar DataPrep.jar [columnThreshold]
            Processes all .xls files in %s
            and writes cleaned workbooks to %s (same filenames).
            For each input file, generates a new sourceArtifactId (UUID), copies the original ministry workbook to
            %s/<uuid>.<original extension>, then processes.
            Writes %s/<name>.xls.source-artifact-id (one line: the UUID) for downstream tools.
            columnThreshold: Optional, 0.0–1.0 (default 0.01). Min fill ratio to keep a column.
            Example: java -jar target/dataprep-1.0-SNAPSHOT-jar-with-dependencies.jar
            Example: java -jar target/dataprep-1.0-SNAPSHOT-jar-with-dependencies.jar 0.05
            """.formatted(
            Constants.DEFAULT_INPUT_DIR,
            Constants.DEFAULT_OUTPUT_DIR,
            Constants.ARTIFACTS_DIR,
            Constants.DEFAULT_OUTPUT_DIR
    );

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
