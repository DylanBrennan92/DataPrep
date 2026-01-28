package com.originspecs.specextractor.processor;

import com.originspecs.specextractor.model.Employee;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

import java.util.Optional;

@Slf4j
public class EmployeeRowParser {

    private static final int REQUIRED_CELLS = 7;

    public Optional<Employee> parse(Row row, DataFormatter formatter) {
        if (row == null) {
            log.trace("Row is null");
            return Optional.empty();
        }

        if (row.getLastCellNum() < REQUIRED_CELLS) {
            log.debug("Row {} has insufficient cells ({} < {})",
                    row.getRowNum(), row.getLastCellNum(), REQUIRED_CELLS);
            return Optional.empty();
        }

        try {
            var employee = buildEmployeeFromRow(row, formatter);

            if (isValidEmployee(employee)) {
                log.info("Successfully parsed row {}: {} - {}",
                        row.getRowNum(), employee.id(), employee.name());
                return Optional.of(employee);
            } else {
                log.debug("Row {} contains invalid employee data", row.getRowNum());
                return Optional.empty();
            }

        } catch (Exception e) {
            log.debug("Failed to parse row {}: {}", row.getRowNum(), e.getMessage());
            return Optional.empty();
        }
    }

    private Employee buildEmployeeFromRow(Row row, DataFormatter formatter) {
        var id = extractCellValue(row, 0, formatter);
        var name = extractCellValue(row, 1, formatter);

        log.trace("Row {}: id='{}', name='{}'", row.getRowNum(), id, name);

        return new Employee(
                extractCellValue(row, 0, formatter),
                extractCellValue(row, 1, formatter),
                extractCellValue(row, 2, formatter),
                extractCellValue(row, 3, formatter),
                extractCellValue(row, 4, formatter),
                extractCellValue(row, 5, formatter),
                extractCellValue(row, 6, formatter)
        );
    }

    private String extractCellValue(Row row, int cellIndex, DataFormatter formatter) {
        var cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        var value = cell != null ? formatter.formatCellValue(cell).strip() : "";
        return value;
    }

    private boolean isValidEmployee(Employee employee) {
        var isValid = employee.id() != null && !employee.id().isBlank() &&
                employee.name() != null && !employee.name().isBlank();

        if (!isValid) {
            log.trace("Invalid employee: id='{}', name='{}'",
                    employee.id(), employee.name());
        }

        return isValid;
    }
}