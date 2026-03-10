package com.originspecs.dataprep.config;

import com.originspecs.dataprep.model.PermittedHeader;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class PermittedHeadersBuilder {

    private static final String DEFAULT_FILE = "src/main/resources/local-data/permittedHeaders.csv";

    /**
     * Loads permitted header mappings from the default file path ({@value DEFAULT_FILE}).
     *
     * @return Map of Japanese label → English label; empty map if file cannot be read
     */
    public static Map<String, String> load() {
        return load(DEFAULT_FILE);
    }

    /**
     * Loads permitted header mappings from the given CSV file.
     * Expected format: {@code japanese,english} with a header row.
     *
     * @param fileName Path to the CSV file (relative to working directory)
     * @return Map of Japanese label → English label; empty map if file cannot be read
     */
    public static Map<String, String> load(String fileName) {
        Path path = Path.of(fileName);

        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            Map<String, String> permittedHeaders = lines
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(PermittedHeadersBuilder::parseLine)
                    .filter(header -> !header.japanese().isBlank())
                    .collect(Collectors.toMap(
                            PermittedHeader::japanese,
                            PermittedHeader::english,
                            (existing, duplicate) -> existing
                    ));

            log.info("Loaded {} permitted header mappings from '{}'", permittedHeaders.size(), fileName);
            return permittedHeaders;

        } catch (IOException e) {
            log.error("Could not read permitted headers file '{}': {} — header matching will be skipped", fileName, e.getMessage());
            return Map.of();
        }
    }

    private static PermittedHeader parseLine(String line) {
        String[] parts = line.split(",", 2);
        String japanese = parts.length > 0 ? parts[0].trim() : "";
        String english = parts.length > 1 ? parts[1].trim() : japanese;
        return new PermittedHeader(japanese, english);
    }
}
