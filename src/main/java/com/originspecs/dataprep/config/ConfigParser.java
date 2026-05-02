package com.originspecs.dataprep.config;

import java.nio.file.Path;

/**
 * Parses CLI arguments into a {@link Config}. Does not touch the filesystem.
 */
public final class ConfigParser {

    private static final double DEFAULT_COLUMN_THRESHOLD = 0.01;

    private ConfigParser() {}

    /**
     * Builds config from CLI args: optional column threshold (0.0–1.0), default 0.01.
     */
    public static Config parse(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--")) {
                throw new IllegalArgumentException(
                        "Unsupported option: " + arg + ". Pass only an optional columnThreshold (0.0–1.0). "
                                + "A sourceArtifactId (UUID) is generated automatically for each input workbook.");
            }
        }
        if (args.length > 1) {
            throw new IllegalArgumentException("At most one columnThreshold argument allowed");
        }
        double columnThreshold = args.length == 0
                ? DEFAULT_COLUMN_THRESHOLD
                : parseColumnThreshold(args[0]);
        return new Config(
                Path.of(Constants.DEFAULT_INPUT_DIR),
                Path.of(Constants.DEFAULT_OUTPUT_DIR),
                Path.of(Constants.ARTIFACTS_DIR),
                columnThreshold
        );
    }

    private static double parseColumnThreshold(String arg) {
        try {
            double value = Double.parseDouble(arg);
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0 || value > 1) {
                throw new IllegalArgumentException("columnThreshold must be between 0.0 and 1.0, got: " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("columnThreshold must be a number between 0.0 and 1.0: " + arg, e);
        }
    }
}
