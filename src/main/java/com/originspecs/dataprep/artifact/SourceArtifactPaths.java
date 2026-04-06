package com.originspecs.dataprep.artifact;

import java.nio.file.Path;

/**
 * Resolves paths under {@code local-data/artifacts} for storing a copy of the
 * original ministry workbook keyed by {@code sourceArtifactId}, and the sidecar
 * filename next to each cleaned output workbook.
 */
public final class SourceArtifactPaths {

    /** Written next to each cleaned {@code .xls}; contains a single line — the UUID string. */
    public static final String SOURCE_ARTIFACT_ID_SIDECAR_SUFFIX = ".source-artifact-id";

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

    /**
     * Sidecar for {@code cleanedOutputWorkbook} (e.g. {@code out/foo.xls} → {@code out/foo.xls.source-artifact-id}).
     */
    public static Path sourceArtifactIdSidecar(Path cleanedOutputWorkbook) {
        String name = cleanedOutputWorkbook.getFileName().toString();
        return cleanedOutputWorkbook.resolveSibling(name + SOURCE_ARTIFACT_ID_SIDECAR_SUFFIX);
    }
}
