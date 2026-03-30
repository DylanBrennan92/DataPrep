package com.originspecs.dataprep.config;

import com.originspecs.dataprep.artifact.SourceArtifactPaths;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Immutable paths and options for one data-prep run. Built via {@link ConfigParser#parse(String[])}
 * and checked with {@link ConfigValidator#validate(Config)}.
 */
public record Config(
        Path inputDir,
        Path outputDir,
        Path artifactsDir,
        Optional<String> sourceArtifactId,
        double columnThreshold
) {

    /**
     * Destination path for the copied original when {@link #sourceArtifactId()} is present;
     * call only in that case or {@link Optional#orElseThrow()} will run.
     */
    public Path artifactCopyDestination(Path inputFile) {
        return SourceArtifactPaths.artifactFile(artifactsDir, sourceArtifactId.orElseThrow(), inputFile);
    }

    public Path outputFileFor(Path inputFile) {
        return outputDir.resolve(inputFile.getFileName());
    }
}
