package com.originspecs.dataprep.artifact;

import java.nio.file.Path;

/**
 * Resolves paths under {@code local-data/artifacts} for storing a copy of the
 * original ministry workbook keyed by {@code sourceArtifactId}.
 */
public final class SourceArtifactPaths {

    private SourceArtifactPaths() {}

    /** Original filename extension including the leading dot, or empty if none. */
    public static String extensionWithDot(Path inputFile) {
        String name = inputFile.getFileName() != null ? inputFile.getFileName().toString() : inputFile.toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    public static Path artifactFile(Path artifactsDir, String sourceArtifactId, Path originalInputFile) {
        return artifactsDir.resolve(sourceArtifactId + extensionWithDot(originalInputFile));
    }
}
