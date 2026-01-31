package com.originspecs.specextractor;

import com.originspecs.specextractor.model.Employee;
import com.originspecs.specextractor.model.Vehicle;
import com.originspecs.specextractor.processor.DataProcessor;
import com.originspecs.specextractor.processor.RecordBuilder;
import com.originspecs.specextractor.processor.RowParser;
import com.originspecs.specextractor.reader.FileReader;
import com.originspecs.specextractor.writer.JsonFileWriter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class Main {

    public enum RecordType {
        EMPLOYEE,
        VEHICLE
    }

    public static void main(String[] args) {
        double columnThreshold = 0.1; // Default 10%

        if (args.length < 4) {
            log.error("Insufficient arguments. Expected at least 4, got {}", args.length);
            log.info("Usage: java -jar spec-extractor.jar <recordType> <inputFile.xls> <outputFile.json> <requiredCells> [columnThreshold]");
            log.info("Record types: EMPLOYEE, VEHICLE");
            log.info("columnThreshold: Optional, default 0.1 (10% minimum fill to keep column)");
            log.info("Example:");
            log.info("java -jar target/spec-extractor.jar VEHICLE nissan.xls output.json 20");
            log.info("java -jar target/spec-extractor.jar EMPLOYEE staff.xls employees.json 7 0.05");
            System.exit(1);
        }

        try {
            RecordType recordType = RecordType.valueOf(args[0].toUpperCase());
            String inputFile = args[1];
            String outputFile = args[2];
            int requiredCells = Integer.parseInt(args[3]);

            // Optional 5th argument for threshold
            if (args.length > 4) {
                columnThreshold = Double.parseDouble(args[4]);
                if (columnThreshold < 0 || columnThreshold > 1) {
                    log.error("columnThreshold must be between 0.0 and 1.0");
                    System.exit(1);
                }
            }

            if (requiredCells <= 0) {
                log.error("Required cells must be a positive integer");
                System.exit(1);
            }

            log.info("Processing {} records", recordType);
            log.info("Input file: {}", inputFile);
            log.info("Output file: {}", outputFile);
            log.info("Column threshold: {}%", String.format("%.1f", columnThreshold * 100));

            // Process based on record type
            switch (recordType) {
                case EMPLOYEE -> processEmployee(inputFile, outputFile, requiredCells, columnThreshold);
                case VEHICLE -> processVehicle(inputFile, outputFile, requiredCells, columnThreshold);
            }

            log.info("Processing completed successfully");

        } catch (IllegalArgumentException e) {
            log.error("Invalid record type. Must be EMPLOYEE or VEHICLE");
            System.exit(1);
        } catch (Exception e) {
            log.error("Failed to process files", e);
            System.exit(1);
        }
    }

    private static void processEmployee(String inputFile, String outputFile,
                                        int requiredCells, double columnThreshold) throws Exception {
        // Create parser
        RowParser<Employee> employeeParser = new RowParser<>(
                requiredCells,
                RecordBuilder.employeeBuilder,
                RecordBuilder.employeeValidator
        );

        // Create reader with threshold
        FileReader<Employee> fileReader = new FileReader<>(employeeParser, columnThreshold);

        // Read data
        List<Employee> employees = fileReader.readXls(inputFile);

        if (employees.isEmpty()) {
            log.warn("No employees were parsed from the XLS file");
            return;
        }

        // Write to JSON
        JsonFileWriter<Employee> jsonWriter = new JsonFileWriter<>();
        jsonWriter.write(employees, Path.of(outputFile));

        // DataProcessor is now optional - you could remove it entirely
        // Or keep it for other processing if needed
        DataProcessor<Employee> processor = new DataProcessor<>(employeeParser, jsonWriter);
        log.info("Processed {} employee records", employees.size());
    }

    private static void processVehicle(String inputFile, String outputFile,
                                       int requiredCells, double columnThreshold) throws Exception {
        // Create parser
        RowParser<Vehicle> vehicleParser = new RowParser<>(
                requiredCells,
                RecordBuilder.vehicleBuilder,
                RecordBuilder.vehicleValidator
        );

        // Create reader with threshold
        FileReader<Vehicle> fileReader = new FileReader<>(vehicleParser, columnThreshold);

        // Read data
        List<Vehicle> vehicles = fileReader.readXls(inputFile);

        if (vehicles.isEmpty()) {
            log.warn("No vehicles were parsed from the XLS file");
            return;
        }

        // Write to JSON
        JsonFileWriter<Vehicle> jsonWriter = new JsonFileWriter<>();
        jsonWriter.write(vehicles, Path.of(outputFile));

        DataProcessor<Vehicle> processor = new DataProcessor<>(vehicleParser, jsonWriter);
        log.info("Processed {} vehicle records", vehicles.size());
    }
}