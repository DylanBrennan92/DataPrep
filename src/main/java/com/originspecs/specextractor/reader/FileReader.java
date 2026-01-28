package com.originspecs.specextractor.reader;

import com.originspecs.specextractor.model.Employee;
import com.originspecs.specextractor.processor.EmployeeRowParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
public class FileReader {

    private final EmployeeRowParser rowParser;

    public FileReader() {
        this.rowParser = new EmployeeRowParser();
    }

    public FileReader(EmployeeRowParser rowParser) {
        this.rowParser = rowParser;
    }

    public List<Employee> readXls(String inputPath) throws IOException {
        log.info("Reading XLS file: {}", inputPath);

        try (var fis = new FileInputStream(inputPath);
             var workbook = new HSSFWorkbook(fis)) {

            var sheet = workbook.getSheetAt(0);
            var formatter = new DataFormatter();

            List<Employee> employees = extractEmployees(sheet, formatter);

            log.info("Read {} employees from XLS file", employees.size());
            return employees;

        } catch (IOException e) {
            log.error("Error reading XLS file: {}", inputPath, e);
            throw e;
        }
    }

    private List<Employee> extractEmployees(Sheet sheet, DataFormatter formatter) {
        var totalRows = sheet.getLastRowNum();
        log.debug("Extracting employees from {} rows (excluding header)", totalRows);

        var employees = IntStream.rangeClosed(1, totalRows)
                .mapToObj(rowIndex -> {
                    var row = sheet.getRow(rowIndex);
                    if (row == null) {
                        log.trace("Row {} is null, skipping", rowIndex);
                        return null;
                    }
                    if (row.getLastCellNum() < 7) {
                        log.trace("Row {} has insufficient cells ({} < 7), skipping",
                                rowIndex, row.getLastCellNum());
                        return null;
                    }
                    return rowParser.parse(row, formatter).orElse(null);
                })
                .filter(employee -> employee != null)
                .peek(employee -> log.trace("Parsed employee: {} - {}",
                        employee.id(), employee.name()))
                .toList();

        log.debug("Successfully parsed {} out of {} rows", employees.size(), totalRows);
        return employees;
    }
}