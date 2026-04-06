package com.originspecs.dataprep.config;

import java.nio.file.Path;

/**
 * Immutable paths and options for one data-prep run. Built via {@link ConfigParser#parse(String[])}
 * and checked with {@link ConfigValidator#validate(Config)}.
 */
public record Config(
        Path inputDir,
        Path outputDir,
        Path artifactsDir,
        double columnThreshold
) {

    public Path outputFileFor(Path inputFile) {
        return outputDir.resolve(inputFile.getFileName());
    }
}
