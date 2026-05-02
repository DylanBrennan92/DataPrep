package com.originspecs.dataprep.reader;

import com.originspecs.dataprep.model.RowData;
import com.originspecs.dataprep.model.WorkBookData;
import com.originspecs.dataprep.model.WorkSheetData;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class WorkBookReader {

    private final DataFormatter formatter = new DataFormatter();
    private final HeaderRangeDetector headerRangeDetector;
    private final Set<String> japaneseBrandNames;

    /** Creates a reader without brand-based header detection or data-start validation. */
    public WorkBookReader() {
        this(Set.of());
    }

    /**
     * Creates a reader that uses the provided brand names to:
     * <ul>
     *   <li>Correctly detect the full header range (scanning past "車名" to the first brand row)</li>
     *   <li>Validate that the first data row of each sheet starts with a known brand</li>
     * </ul>
     *
     * @param japaneseBrandNames Set of Japanese brand names (e.g. "ホンダ", "トヨタ")
     */
    public WorkBookReader(Set<String> japaneseBrandNames) {
        this.japaneseBrandNames = japaneseBrandNames;
        this.headerRangeDetector = new HeaderRangeDetector(japaneseBrandNames);
    }

    /**
     * Reads an .xls workbook and maps all sheets into a WorkBookData model.
     * Header rows are detected using {@link HeaderRangeDetector} — the row containing
     * "車名" is used as the column header row; pre-header metadata rows are skipped.
     * Falls back to treating the first row as the header if detection fails.
     *
     * @param inputPath Path to the .xls file
     * @return WorkBookData containing all sheets and their rows
     */
    public WorkBookData read(Path inputPath) throws IOException {
        log.info("Reading XLS workbook from {}", inputPath.toAbsolutePath());

        try (InputStream is = Files.newInputStream(inputPath);
             Workbook workbook = new HSSFWorkbook(is)) {

            // Evaluator resolves formula cells to their computed value rather than raw formula text
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            WorkBookData workBookData = new WorkBookData();
            workBookData.setFileName(inputPath.getFileName().toString());
            workBookData.setWorksheetCount(workbook.getNumberOfSheets());

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                workBookData.getWorksheets().add(readSheet(sheet, i, evaluator));
            }

            log.info("Read {} worksheet(s) from '{}'", workBookData.getWorksheetCount(), workBookData.getFileName());
            return workBookData;
        }
    }

    private WorkSheetData readSheet(Sheet sheet, int index, FormulaEvaluator evaluator) {
        WorkSheetData worksheetData = new WorkSheetData();
        worksheetData.setName(sheet.getSheetName());
        worksheetData.setIndex(index);
        worksheetData.setOriginalRowCount(sheet.getLastRowNum() + 1);

        HeaderRange headerRange = headerRangeDetector.detect(sheet)
                .orElseGet(() -> {
                    log.warn("Sheet '{}': header detection failed, falling back to row 0 as header", sheet.getSheetName());
                    return new HeaderRange(0, 0);
                });

        worksheetData.setHeaderRangeStart(headerRange.startRowIndex());
        worksheetData.setHeaderRangeEnd(headerRange.endRowIndex());

        validateDataStartRow(sheet, headerRange);

        // Build merged cell map once for the whole sheet so header rows resolve correctly
        Map<String, String> mergedCellValues = MergedCellValues.build(sheet, formatter);

        List<List<String>> rawHeaderRows = new ArrayList<>();
        List<RowData> rows = new ArrayList<>();
        int maxColumnCount = 0;

        for (Row row : sheet) {
            int rowIndex = row.getRowNum();

            if (headerRange.isPreHeaderRow(rowIndex)) {
                log.trace("Sheet '{}': skipping pre-header row {}", sheet.getSheetName(), rowIndex);
                continue;
            }

            if (headerRange.isHeaderRow(rowIndex)) {
                // Collect every header range row — merged cell values are expanded here
                List<String> headerRow = readHeaderRow(row, mergedCellValues);
                rawHeaderRows.add(headerRow);
                maxColumnCount = Math.max(maxColumnCount, headerRow.size());
                continue;
            }

            rows.add(new RowData(readRow(row, evaluator)));
        }

        worksheetData.setRawHeaderRows(rawHeaderRows);
        worksheetData.setOriginalColumnCount(maxColumnCount);
        worksheetData.setRows(rows);

        log.debug("Sheet '{}': header rows {}-{} ({} raw header rows), {} columns, {} data rows",
                worksheetData.getName(),
                headerRange.startRowIndex(), headerRange.endRowIndex(),
                rawHeaderRows.size(), maxColumnCount, rows.size());
        return worksheetData;
    }

    /**
     * Reads a header row, expanding merged cell values so every column
     * in a merged region gets the value of its top-left cell.
     */
    private List<String> readHeaderRow(Row row, Map<String, String> mergedCellValues) {
        int lastCellNum = row.getLastCellNum();
        List<String> cellValues = new ArrayList<>(Math.max(lastCellNum, 0));
        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String cellKey = row.getRowNum() + ":" + i;

            if (cell == null || formatter.formatCellValue(cell).strip().isEmpty()) {
                // Check if this blank cell is covered by a merged region
                cellValues.add(mergedCellValues.getOrDefault(cellKey, ""));
            } else {
                cellValues.add(formatter.formatCellValue(cell).strip());
            }
        }
        return cellValues;
    }

    /**
     * Reads a plain data row, evaluating any formula cells to their computed value.
     * Falls back to an empty string if evaluation fails for a particular cell.
     */
    private List<String> readRow(Row row, FormulaEvaluator evaluator) {
        int lastCellNum = row.getLastCellNum();
        List<String> cellValues = new ArrayList<>(Math.max(lastCellNum, 0));
        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            cellValues.add(cell == null ? "" : evaluateCell(cell, evaluator));
        }
        return cellValues;
    }

    /**
     * Evaluates a cell using the provided formula evaluator so that formula cells
     * return their computed result rather than the raw formula string.
     * Falls back gracefully to the cached value or an empty string on error.
     */
    private String evaluateCell(Cell cell, FormulaEvaluator evaluator) {
        try {
            return formatter.formatCellValue(cell, evaluator).strip();
        } catch (Exception e) {
            log.debug("Formula evaluation failed for cell [{},{}]: {} — using cached value",
                    cell.getRowIndex(), cell.getColumnIndex(), e.getMessage());
            return formatter.formatCellValue(cell).strip();
        }
    }

    /**
     * Checks if the first data row starts with a known Japanese car brand name.
     * Logs a warning if no brand match is found, which may indicate that header
     * detection did not land on the correct row.
     */
    private void validateDataStartRow(Sheet sheet, HeaderRange headerRange) {
        if (japaneseBrandNames.isEmpty()) return;

        int dataStartRow = headerRange.dataStartRowIndex();
        Row firstDataRow = sheet.getRow(dataStartRow);
        if (firstDataRow == null) {
            log.warn("Sheet '{}': no data row found at expected start index {}", sheet.getSheetName(), dataStartRow);
            return;
        }

        Cell firstCell = firstDataRow.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        String firstValue = firstCell == null ? "" : formatter.formatCellValue(firstCell).strip();

        if (japaneseBrandNames.contains(firstValue)) {
            log.debug("Sheet '{}': data start confirmed — first row starts with brand '{}'",
                    sheet.getSheetName(), firstValue);
        } else {
            log.warn("Sheet '{}': first data row at index {} starts with '{}' which is not a known brand — " +
                    "header detection may be incorrect", sheet.getSheetName(), dataStartRow, firstValue);
        }
    }

}
