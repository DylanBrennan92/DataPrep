package com.originspecs.dataprep.config;

/**
 * Parses and validates CLI arguments into a {@link Config}.
 * Process exit on failure is handled by {@link com.originspecs.dataprep.Main}.
 */
public final class CliParser {

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

    private CliParser() {}

    /**
     * Parses CLI arguments and validates config against the filesystem.
     *
     * @throws IllegalArgumentException invalid arguments
     * @throws IllegalStateException    environment validation failure
     */
    public static Config parse(String[] args) {
        Config config = ConfigParser.parse(args);
        ConfigValidator.validate(config);
        return config;
    }

    public static String usage() {
        return USAGE;
    }
}
