package com.originspecs.specextractor;

import com.originspecs.specextractor.model.Employee;
import com.originspecs.specextractor.processor.DataProcessor;
import com.originspecs.specextractor.reader.FileReader;
import com.originspecs.specextractor.writer.JsonFileWriter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class Main {
    public static void main(String[] args) {

        if (args.length < 2) {
            log.error("Insufficient arguments. Expected 2, got {}", args.length);
            log.info("Usage: java -jar spec-extractor.jar <inputFile.xls> <outputFile.json>");
            log.info("Real example with DEBUG level logs: ");
            log.info("java -DLOG_LEVEL=DEBUG -jar target/spec-extractor-1.0-SNAPSHOT-jar-with-dependencies.jar \\\n" +
                    "                         src/main/resources/staff.xls output.json");

            System.exit(1);
        }

        var inputFile = args[0];
        var outputFile = args[1];

        log.info("Input file: {}", inputFile);
        log.info("Output file: {}", outputFile);

        try {

            // Read XLS file
            log.debug("=== Reading XLS file ===");
            var fileReader = new FileReader();
            List<Employee> employees = fileReader.readXls(inputFile);
            log.info("Read {} employees from XLS", employees.size());

            if (employees.isEmpty()) {
                log.warn("No employees were parsed from the XLS file");
            }

            // Convert to JSON and write to file
            log.debug("=== Writing JSON output ===");
            var outputPath = Path.of(outputFile);
            var jsonWriter = new JsonFileWriter();
            jsonWriter.write(employees, outputPath);

            log.info("Processing completed successfully");

        } catch (Exception e) {
            log.error("Failed to process files", e);
            System.exit(1);
        }
    }
}