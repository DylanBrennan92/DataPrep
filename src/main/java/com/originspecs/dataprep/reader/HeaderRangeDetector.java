package com.originspecs.dataprep.reader;

import com.originspecs.dataprep.config.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detects the full header row range within an XLS sheet.
 *
 * <p>Detection strategy:
 * <ol>
 *   <li>Scan rows to find the one containing {@link Constants#CAR_NAME_JP} ("車名") —
 *       this anchors the header block and is used to find where the block starts.</li>
 *   <li>Walk backwards from that row to find the header block start: the first row
 *       with fewer than {@value MIN_HEADER_CELLS} non-empty cells is treated as
 *       pre-header metadata; the block starts at the next row.</li>
 *   <li>From the "車名" row, scan <em>forward</em> until the Car Name column (the
 *       column containing "車名") contains a known Japanese brand name — that row is
 *       the data start; the header range ends at the row immediately before it. This
 *       correctly captures sub-header rows and works when the Car Name column is not
 *       column A (e.g. Lexus sheets with metadata in leading columns).</li>
 * </ol>
 *
 * <p>If no brand names are provided, the "車名" row is used as the header range end
 * (legacy fallback behaviour).
 */
@Slf4j
public class HeaderRangeDetector {

    private static final int MIN_HEADER_CELLS = 3;

    private final DataFormatter formatter = new DataFormatter();
    private final Set<String> japaneseBrandNames;
    /** NFKC-normalized brand names for matching half-width katakana (e.g. ﾄﾖﾀ) to full-width (トヨタ). */
    private final Set<String> normalizedBrandNames;

    /** Creates a detector without brand-based data-start detection (legacy fallback). */
    public HeaderRangeDetector() {
        this(Set.of());
    }

    /**
     * Creates a detector that scans forward past "車名" to find where car data actually
     * starts, using the provided set of Japanese brand names.
     *
     * @param japaneseBrandNames Set of Japanese car brand names (e.g. "ホンダ", "トヨタ")
     */
    public HeaderRangeDetector(Set<String> japaneseBrandNames) {
        this.japaneseBrandNames = japaneseBrandNames != null ? japaneseBrandNames : Set.of();
        this.normalizedBrandNames = this.japaneseBrandNames.stream()
                .map(b -> Normalizer.normalize(b, Normalizer.Form.NFKC))
                .collect(Collectors.toSet());
    }

    /**
     * Detects the header range for the given sheet.
     *
     * @param sheet The POI sheet to analyse
     * @return Optional containing the detected HeaderRange, or empty if "車名" is not found
     */
    public Optional<HeaderRange> detect(Sheet sheet) {
        int carNameRowIndex = findCarNameRowIndex(sheet);

        if (carNameRowIndex == -1) {
            log.warn("Could not find '{}' in sheet '{}' — header range detection failed",
                    Constants.CAR_NAME_JP, sheet.getSheetName());
            return Optional.empty();
        }

        int startRowIndex = findHeaderRangeStart(sheet, carNameRowIndex);
        var endAndCol = findHeaderRangeEndAndCarNameColumn(sheet, carNameRowIndex);
        int endRowIndex = endAndCol.endRowIndex();
        int carNameColIndex = endAndCol.carNameColumnIndex();

        HeaderRange range = new HeaderRange(startRowIndex, endRowIndex, carNameColIndex);

        log.info("Sheet '{}': detected header range rows {}-{}, data starts at row {}, Car Name col {}",
                sheet.getSheetName(), startRowIndex, endRowIndex, range.dataStartRowIndex(), carNameColIndex);

        return Optional.of(range);
    }

    /**
     * Scans all rows to find the first one containing the "車名" value in any cell.
     *
     * @return 0-based row index, or -1 if not found
     */
    private int findCarNameRowIndex(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (Constants.CAR_NAME_JP.equals(formatter.formatCellValue(cell).strip())) {
                    log.debug("Found '{}' at row {} in sheet '{}'",
                            Constants.CAR_NAME_JP, row.getRowNum(), sheet.getSheetName());
                    return row.getRowNum();
                }
            }
        }
        return -1;
    }

    /**
     * Walks backwards from the "車名" row to find where the header block starts.
     * Stops at the first row with fewer than {@value MIN_HEADER_CELLS} non-empty cells.
     */
    private int findHeaderRangeStart(Sheet sheet, int carNameRowIndex) {
        for (int i = carNameRowIndex - 1; i >= 0; i--) {
            Row row = sheet.getRow(i);
            if (row == null || countNonEmptyCells(row) < MIN_HEADER_CELLS) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Returns all column indices where "車名" appears in the given row.
     * Uses merged-cell expansion so that columns covered by a merge (which only
     * store the value in the origin cell) are included. This allows scanning for
     * brands in the correct column when metadata columns (e.g. Lexus) share a
     * merged header with the actual data column.
     */
    private List<Integer> findColumnsWithCarName(Sheet sheet, int carNameRowIndex) {
        Set<Integer> colSet = new LinkedHashSet<>();
        Map<String, String> mergedValues = MergedCellValues.build(sheet, formatter);
        Row row = sheet.getRow(carNameRowIndex);
        if (row == null) return List.of();

        int lastCol = row.getLastCellNum();
        for (int c = 0; c < lastCol; c++) {
            String value = getCellValueWithMerge(sheet, row, c, mergedValues);
            if (Constants.CAR_NAME_JP.equals(value)) {
                colSet.add(c);
            }
        }
        return new ArrayList<>(colSet);
    }

    private String getCellValueWithMerge(Sheet sheet, Row row, int colIndex, Map<String, String> mergedValues) {
        String key = row.getRowNum() + ":" + colIndex;
        if (mergedValues.containsKey(key)) {
            return mergedValues.get(key);
        }
        Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell).strip();
    }

    private record EndAndColumn(int endRowIndex, int carNameColumnIndex) {}

    /**
     * Scans forward from the "車名" row to find where real car data begins.
     * Checks every column that contains 車名 — when metadata columns (e.g. Lexus
     * sheets) inherit 車名 from merged headers, the actual brand data is in a
     * different column. The first column that has a brand name identifies both
     * the data start row and the true Car Name column.
     *
     * @param sheet           The POI sheet to scan
     * @param carNameRowIndex The row containing "車名"
     * @return EndAndColumn with last header row index and the Car Name column index
     */
    private EndAndColumn findHeaderRangeEndAndCarNameColumn(Sheet sheet, int carNameRowIndex) {
        List<Integer> colsWithCarName = findColumnsWithCarName(sheet, carNameRowIndex);
        if (colsWithCarName.isEmpty()) {
            log.warn("Sheet '{}': no column with '{}' in row {} — using row as header end, col 0",
                    sheet.getSheetName(), Constants.CAR_NAME_JP, carNameRowIndex);
            return new EndAndColumn(carNameRowIndex, 0);
        }

        if (japaneseBrandNames.isEmpty()) {
            log.debug("No brand names configured — using '車名' row {} as header range end", carNameRowIndex);
            int firstCol = colsWithCarName.get(0);
            return new EndAndColumn(carNameRowIndex, firstCol);
        }

        int lastRow = sheet.getLastRowNum();
        for (int i = carNameRowIndex + 1; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            for (int colIndex : colsWithCarName) {
                Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null) continue;

                String cellValue = formatter.formatCellValue(cell).strip();
                if (isKnownBrand(cellValue)) {
                    log.debug("Sheet '{}': found brand '{}' at row {} (col {}) — header range ends at row {}, Car Name col {}",
                            sheet.getSheetName(), cellValue, i, colIndex, i - 1, colIndex);
                    return new EndAndColumn(i - 1, colIndex);
                }
            }
        }

        int fallbackCol = colsWithCarName.get(0);
        log.warn("Sheet '{}': no brand name found after '車名' row {} (checked cols {}) — falling back to row {} as header end, col {}",
                sheet.getSheetName(), carNameRowIndex, colsWithCarName, carNameRowIndex, fallbackCol);
        return new EndAndColumn(carNameRowIndex, fallbackCol);
    }

    private int countNonEmptyCells(Row row) {
        int count = 0;
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).strip().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Matches cell value against brand names using NFKC normalization (half-width ﾄﾖﾀ → full-width トヨタ). */
    private boolean isKnownBrand(String cellValue) {
        if (cellValue == null || cellValue.isEmpty()) return false;
        String normalized = Normalizer.normalize(cellValue, Normalizer.Form.NFKC);
        return normalizedBrandNames.contains(normalized);
    }
}
