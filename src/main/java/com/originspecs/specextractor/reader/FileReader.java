package com.originspecs.specextractor.reader;

import com.originspecs.specextractor.model.DataRecord;
import com.originspecs.specextractor.processor.PreProcessor;
import com.originspecs.specextractor.processor.RowParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Slf4j
public class FileReader<T extends DataRecord> {

    private final RowParser<T> rowParser;
    private final PreProcessor preProcessor;
    private final double columnThreshold; // Threshold for keeping columns (0.0 to 1.0)

    public FileReader(RowParser<T> rowParser) {
        this(rowParser, 0.1); // Default 10% threshold
    }

    public FileReader(RowParser<T> rowParser, double columnThreshold) {
        this.rowParser = rowParser;
        this.preProcessor = new PreProcessor();
        this.columnThreshold = columnThreshold;
    }

    public List<T> readXls(String inputPath) throws IOException {
        log.info("Reading XLS file: {}", inputPath);

        try (var fis = new FileInputStream(inputPath);
             var workbook = new HSSFWorkbook(fis)) {

            var sheet = workbook.getSheetAt(0);
            var formatter = new DataFormatter();

            // Step 1: Detect header rows
            int headerRows = preProcessor.detectHeaderRows(sheet);
            log.info("Detected {} header rows", headerRows);

            // Step 2: Pre-process to identify which columns to keep
            List<Integer> validColumns = preProcessor.removeEmptyColumnsFromSheet(sheet, columnThreshold);

            log.info("After preprocessing: {} valid columns identified", validColumns.size());

            // Step 3: Extract records using only valid columns
            List<T> records = extractRecords(sheet, formatter, validColumns);

            log.info("Read {} records from XLS file", records.size());
            return records;

        } catch (IOException e) {
            log.error("Error reading XLS file: {}", inputPath, e);
            throw e;
        }
    }

    private List<T> extractRecords(Sheet sheet, DataFormatter formatter, List<Integer> validColumns) {
        var totalRows = sheet.getLastRowNum();
        log.debug("Extracting records from {} rows (excluding header)", totalRows);

        return IntStream.rangeClosed(1, totalRows)
                .mapToObj(rowIndex -> {
                    var row = sheet.getRow(rowIndex);
                    if (row == null) {
                        log.trace("Row {} is null, skipping", rowIndex);
                        return null;
                    }
                    // Check if row has enough cells in valid columns
                    if (validColumns.stream().anyMatch(col -> col >= row.getLastCellNum())) {
                        log.trace("Row {} has insufficient cells for valid columns, skipping", rowIndex);
                        return null;
                    }
                    return rowParser.parse(row, formatter, validColumns).orElse(null);
                })
                .filter(Objects::nonNull)
                .toList();
    }
}