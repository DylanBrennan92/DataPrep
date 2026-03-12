package com.originspecs.dataprep.config;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
public record Config(
        Path inputDir,
        Path outputDir,
        double columnThreshold
) {

    private static final String DEFAULT_INPUT_DIR = "src/main/resources/local-data/input";
    private static final String DEFAULT_OUTPUT_DIR = "src/main/resources/local-data/output";
    private static final double DEFAULT_COLUMN_THRESHOLD = 0.01;

    /**
     * Creates config from CLI args. Accepts 0 or 1 argument:
     * <ul>
     *   <li>0 args: use default column threshold 0.01</li>
     *   <li>1 arg: column threshold (0.0–1.0)</li>
     * </ul>
     * Input and output directories use defaults relative to the working directory.
     */
    public static Config fromArgs(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("At most 1 argument (columnThreshold) allowed");
        }
        double columnThreshold = args.length >= 1 ? parseColumnThreshold(args[0]) : DEFAULT_COLUMN_THRESHOLD;
        return new Config(
                Path.of(DEFAULT_INPUT_DIR),
                Path.of(DEFAULT_OUTPUT_DIR),
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
            throw new IllegalArgumentException("columnThreshold must be a number between 0.0 and 1.0: " + arg);
        }
    }

    public void validate() {
        if (!inputDir.toFile().exists()) {
            throw new IllegalArgumentException("Input directory does not exist: " + inputDir.toAbsolutePath());
        }
        if (!inputDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("Input path is not a directory: " + inputDir.toAbsolutePath());
        }
        try (Stream<Path> stream = Files.list(inputDir)) {
            long xlsCount = stream.filter(p -> p.toString().toLowerCase().endsWith(".xls")).count();
            if (xlsCount == 0) {
                log.warn("No .xls files found in input directory: {}", inputDir.toAbsolutePath());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot list input directory: " + e.getMessage());
        }
        var outputParent = outputDir.toFile();
        if (!outputParent.exists()) {
            log.info("Output directory will be created: {}", outputDir.toAbsolutePath());
        }
    }

    /** Returns paths to all .xls files in the input directory. */
    public List<Path> inputFiles() {
        try (Stream<Path> stream = Files.list(inputDir)) {
            return stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".xls"))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot list input directory: " + e.getMessage());
        }
    }

    /** Returns the output path for a given input file (same filename in output dir). */
    public Path outputFileFor(Path inputFile) {
        return outputDir.resolve(inputFile.getFileName());
    }
}
