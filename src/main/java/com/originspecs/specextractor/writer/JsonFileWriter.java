package com.originspecs.specextractor.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.originspecs.specextractor.model.Employee;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class JsonFileWriter <T> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void write(List<T> records, Path outputPath) throws IOException {
        log.debug("Writing {} employees to JSON file", records.size());
        log.debug("Output path: {}", outputPath.toAbsolutePath());

        // Check if output directory exists and is writable
        var parentDir = outputPath.getParent();
        if (parentDir != null) {
            if (!Files.exists(parentDir)) {
                log.info("Creating parent directory: {}", parentDir);
                Files.createDirectories(parentDir);
            }
        }

        try {
            var json = serializeToJson(records);
            Files.writeString(outputPath, json);
            log.info("Successfully wrote JSON file: {}", outputPath.toAbsolutePath());

        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed", e);
            throw new IOException("Failed to serialize records to JSON", e);
        } catch (IOException e) {
            log.error("File write failed for path: {}", outputPath.toAbsolutePath(), e);
            throw e;
        }
    }

    public String serializeToJson(List<T> records) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(records);
    }
}