package com.originspecs.dataprep.config;

import com.originspecs.dataprep.input.InputWorkbooks;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Validates {@link Config} against the filesystem (input layout).
 */
@Slf4j
public final class ConfigValidator {

    private ConfigValidator() {}

    public static void validate(Config config) {
        Path inputDir = config.inputDir();
        if (!Files.exists(inputDir)) {
            throw new IllegalArgumentException("Input directory does not exist: " + inputDir.toAbsolutePath());
        }
        if (!Files.isDirectory(inputDir)) {
            throw new IllegalArgumentException("Input path is not a directory: " + inputDir.toAbsolutePath());
        }
        List<Path> xlsFiles = InputWorkbooks.listSorted(inputDir);
        if (xlsFiles.isEmpty()) {
            log.warn("No .xls files found in input directory: {}", inputDir.toAbsolutePath());
        }
        if (!Files.exists(config.outputDir())) {
            log.info("Output directory will be created: {}", config.outputDir().toAbsolutePath());
        }
    }
}
