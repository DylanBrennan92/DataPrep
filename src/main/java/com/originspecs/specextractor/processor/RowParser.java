package com.originspecs.specextractor.processor;

import com.originspecs.specextractor.model.DataRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public class RowParser<T extends DataRecord> {

    @FunctionalInterface
    public interface Validator<T> {
        boolean isValid(T record);
    }

    private final int requiredCells;
    private final Function<List<String>, T> recordBuilder;  // Changed: takes List<String> of cell values
    private final Validator<T> validator;

    public RowParser(int requiredCells, Function<List<String>, T> recordBuilder, Validator<T> validator) {
        this.requiredCells = requiredCells;
        this.recordBuilder = recordBuilder;
        this.validator = validator;
    }

    /**
     * Parse a row using only specific columns
     */
    public Optional<T> parse(Row row, DataFormatter formatter, List<Integer> validColumns) {
        if (row == null) {
            log.trace("Row is null");
            return Optional.empty();
        }

        if (validColumns.size() < requiredCells) {
            log.debug("Not enough valid columns ({} < {})", validColumns.size(), requiredCells);
            return Optional.empty();
        }

        try {
            // Extract values only from valid columns
            List<String> cellValues = extractCellValuesFromColumns(row, formatter, validColumns);

            T record = recordBuilder.apply(cellValues);

            if (validator.isValid(record)) {
                log.info("Successfully parsed row {}: {}", row.getRowNum(), record);
                return Optional.of(record);
            } else {
                log.debug("Row {} contains invalid data", row.getRowNum());
                return Optional.empty();
            }

        } catch (Exception e) {
            log.debug("Failed to parse row {}: {}", row.getRowNum(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Backward compatibility - parse using all columns
     */
    public Optional<T> parse(Row row, DataFormatter formatter) {
        // Create a list of all column indices
        if (row == null) return Optional.empty();
        List<Integer> allColumns = List.of();
        int lastCell = Math.max(row.getLastCellNum() - 1, 0);
        for (int i = 0; i <= lastCell; i++) {
            allColumns.add(i);
        }
        return parse(row, formatter, allColumns);
    }

    private List<String> extractCellValuesFromColumns(Row row, DataFormatter formatter, List<Integer> columns) {
        return columns.stream()
                .map(colIndex -> {
                    var cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    return cell != null ? formatter.formatCellValue(cell).strip() : "";
                })
                .toList();
    }

    // Keep the RowData class if you're using it elsewhere
    public static class RowData {
        private final List<String> cellValues;

        public RowData(List<String> cellValues) {
            this.cellValues = cellValues;
        }

        public String getCellValue(int index) {
            return index < cellValues.size() ? cellValues.get(index) : "";
        }
    }
}