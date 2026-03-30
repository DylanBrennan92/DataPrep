package com.originspecs.dataprep.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Parses CLI arguments into a {@link Config}. Does not touch the filesystem.
 */
public final class ConfigParser {

    public static final String SOURCE_ARTIFACT_ID_FLAG = "--source-artifact-id";

    private static final String DEFAULT_INPUT_DIR = "src/main/resources/local-data/input";
    private static final String DEFAULT_OUTPUT_DIR = "src/main/resources/local-data/output";
    private static final String DEFAULT_ARTIFACTS_DIR = Constants.ARTIFACTS_DIR;
    private static final double DEFAULT_COLUMN_THRESHOLD = 0.01;

    private ConfigParser() {}

    /**
     * Builds config from CLI args.
     * <ul>
     *   <li>Optional {@code --source-artifact-id &lt;uuid&gt;} — at most once</li>
     *   <li>Optional column threshold (0.0–1.0), default 0.01 — any order relative to the flag</li>
     * </ul>
     */
    public static Config parse(String[] args) {
        List<String> thresholdTokens = new ArrayList<>();
        Optional<String> artifactId = Optional.empty();
        for (int i = 0; i < args.length; i++) {
            if (SOURCE_ARTIFACT_ID_FLAG.equals(args[i])) {
                if (artifactId.isPresent()) {
                    throw new IllegalArgumentException("Duplicate " + SOURCE_ARTIFACT_ID_FLAG + " — at most one allowed");
                }
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value after " + SOURCE_ARTIFACT_ID_FLAG);
                }
                String id = args[++i].trim();
                if (id.isEmpty()) {
                    throw new IllegalArgumentException("sourceArtifactId must not be blank");
                }
                UUID.fromString(id);
                artifactId = Optional.of(id);
            } else {
                thresholdTokens.add(args[i]);
            }
        }
        if (thresholdTokens.size() > 1) {
            throw new IllegalArgumentException("At most one columnThreshold argument allowed");
        }
        double columnThreshold = thresholdTokens.isEmpty()
                ? DEFAULT_COLUMN_THRESHOLD
                : parseColumnThreshold(thresholdTokens.get(0));
        return new Config(
                Path.of(DEFAULT_INPUT_DIR),
                Path.of(DEFAULT_OUTPUT_DIR),
                Path.of(DEFAULT_ARTIFACTS_DIR),
                artifactId,
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
