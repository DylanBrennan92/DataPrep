package com.originspecs.dataprep.reader;

import com.originspecs.dataprep.model.RowData;
import com.originspecs.dataprep.model.WorkBookData;
import com.originspecs.dataprep.model.WorkSheetData;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class WorkBookReader {

    private final DataFormatter formatter = new DataFormatter();
    private final HeaderRangeDetector headerRangeDetector;
    private final Set<String> japaneseBrandNames;
    private final Set<String> normalizedBrandNames;

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
        this.japaneseBrandNames = japaneseBrandNames != null ? japaneseBrandNames : Set.of();
        this.normalizedBrandNames = this.japaneseBrandNames.stream()
                .map(b -> Normalizer.normalize(b, Normalizer.Form.NFKC))
                .collect(Collectors.toSet());
        this.headerRangeDetector = new HeaderRangeDetector(this.japaneseBrandNames);
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

        // Exclude metadata columns to the left of the Car Name column (e.g. Lexus sheets)
        int dataStartCol = Math.max(0, headerRange.carNameColumnIndex());
        if (dataStartCol > 0) {
            log.info("Sheet '{}': excluding {} metadata column(s) before Car Name (col {})",
                    sheet.getSheetName(), dataStartCol, headerRange.carNameColumnIndex());
        }

        // Build merged cell map once for the whole sheet so header rows resolve correctly
        Map<String, String> mergedCellValues = buildMergedCellValueMap(sheet);

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
                List<String> fullHeaderRow = readHeaderRow(row, mergedCellValues);
                List<String> headerRow = sliceFromColumn(fullHeaderRow, dataStartCol);
                rawHeaderRows.add(headerRow);
                maxColumnCount = Math.max(maxColumnCount, headerRow.size());
                continue;
            }

            List<String> fullRow = readRow(row, evaluator, mergedCellValues);
            rows.add(new RowData(sliceFromColumn(fullRow, dataStartCol)));
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
     * Reads a data row, evaluating formula cells to their computed value and
     * propagating merged-region values to non-origin cells. This ensures that
     * group-identifier columns (e.g. Car Name, Common Name) which use vertical
     * merges in the source file are filled for every row in the merged region,
     * not just the top cell.
     */
    private List<String> readRow(Row row, FormulaEvaluator evaluator, Map<String, String> mergedCellValues) {
        int lastCellNum = row.getLastCellNum();
        List<String> cellValues = new ArrayList<>(Math.max(lastCellNum, 0));
        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null || formatter.formatCellValue(cell, evaluator).strip().isEmpty()) {
                String cellKey = row.getRowNum() + ":" + i;
                cellValues.add(mergedCellValues.getOrDefault(cellKey, ""));
            } else {
                cellValues.add(evaluateCell(cell, evaluator));
            }
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
     * Returns a sublist from the given column index onward. Used to exclude metadata
     * columns to the left of the Car Name column.
     */
    private static List<String> sliceFromColumn(List<String> values, int fromIndex) {
        if (fromIndex <= 0 || fromIndex >= values.size()) return values;
        return new ArrayList<>(values.subList(fromIndex, values.size()));
    }

    /**
     * Checks if the first data row has a known Japanese car brand name in the Car Name column.
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

        int carNameColIndex = headerRange.carNameColumnIndex();
        Cell carNameCell = firstDataRow.getCell(carNameColIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        String cellValue = carNameCell == null ? "" : formatter.formatCellValue(carNameCell).strip();

        String normalized = Normalizer.normalize(cellValue, Normalizer.Form.NFKC);
        if (normalizedBrandNames.contains(normalized)) {
            log.debug("Sheet '{}': data start confirmed — first row has brand '{}' in Car Name column (col {})",
                    sheet.getSheetName(), cellValue, carNameColIndex);
        } else {
            log.warn("Sheet '{}': first data row at index {} has '{}' in Car Name column (col {}) — not a known brand; " +
                    "header detection may be incorrect", sheet.getSheetName(), dataStartRow, cellValue, carNameColIndex);
        }
    }

    /**
     * Builds a map of (rowIndex:colIndex) → value for every non-origin cell
     * in each merged region. The origin (top-left) cell already holds the value
     * in POI; this map covers all other cells in the region so they can be looked
     * up during header row reading when the cell is blank.
     *
     * <p>Both horizontal and vertical expansion are intentional: group headers in
     * the source XLS often span multiple rows (vertical) AND multiple columns
     * (horizontal), and every cell in the merge needs the value available as a
     * fallback. Duplicate-label resolution is handled downstream in
     * {@link com.originspecs.dataprep.processor.WorkBookProcessor} using data
     * fill-rate comparison, not by restricting expansion here.
     */
    private Map<String, String> buildMergedCellValueMap(Sheet sheet) {
        Map<String, String> mergedValues = new HashMap<>();

        for (CellRangeAddress region : sheet.getMergedRegions()) {
            Row firstRow = sheet.getRow(region.getFirstRow());
            if (firstRow == null) continue;

            Cell originCell = firstRow.getCell(region.getFirstColumn(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = originCell == null ? "" : formatter.formatCellValue(originCell).strip();

            if (value.isEmpty()) continue;

            for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                    if (r == region.getFirstRow() && c == region.getFirstColumn()) continue;
                    mergedValues.put(r + ":" + c, value);
                }
            }
        }

        log.debug("Sheet '{}': resolved {} merged cell positions", sheet.getSheetName(), mergedValues.size());
        return mergedValues;
    }
}
