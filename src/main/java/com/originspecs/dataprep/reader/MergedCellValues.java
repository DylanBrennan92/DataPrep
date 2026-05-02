package com.originspecs.dataprep.reader;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a lookup map of merged-cell values for a given sheet.
 *
 * <p>In the HSSF model, only the top-left (origin) cell of a merged region
 * stores the value; all other cells in the region appear blank. This utility
 * expands each non-origin cell so that header-row and data-row readers can
 * look up the correct value by row:col key.
 */
final class MergedCellValues {

    private MergedCellValues() {}

    /**
     * Returns a {@code "rowIndex:colIndex" → value} map for every non-origin cell
     * in each merged region of the given sheet.
     *
     * @param sheet     The POI sheet to inspect
     * @param formatter Formatter used to read the origin cell value
     * @return Map that is empty when the sheet has no merged regions
     */
    static Map<String, String> build(Sheet sheet, DataFormatter formatter) {
        Map<String, String> mergedValues = new HashMap<>();

        for (CellRangeAddress region : sheet.getMergedRegions()) {
            Row firstRow = sheet.getRow(region.getFirstRow());
            if (firstRow == null) continue;

            Cell originCell = firstRow.getCell(region.getFirstColumn(),
                    Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = originCell == null ? "" : formatter.formatCellValue(originCell).strip();
            if (value.isEmpty()) continue;

            for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                    if (r == region.getFirstRow() && c == region.getFirstColumn()) continue;
                    mergedValues.put(r + ":" + c, value);
                }
            }
        }

        return mergedValues;
    }
}
