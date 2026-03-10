package com.originspecs.dataprep.processor;

import com.originspecs.dataprep.config.Constants;
import com.originspecs.dataprep.model.RowData;
import com.originspecs.dataprep.model.WorkBookData;
import com.originspecs.dataprep.model.WorkSheetData;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class WorkBookProcessor {

    private final HeaderResolver headerResolver;
    private final Set<String> japaneseBrandNames;

    /**
     * Creates a processor with the given permitted headers map (no brand names for dedup).
     * Use {@link com.originspecs.dataprep.config.PermittedHeadersBuilder} to load the map.
     */
    public WorkBookProcessor(Map<String, String> permittedHeaders) {
        this(permittedHeaders, Set.of());
    }

    /**
     * Creates a processor with the given permitted headers map and brand names for
     * Car Name column deduplication (drops metadata columns that repeat "車名").
     *
     * @param permittedHeaders Map of Japanese → English header mappings
     * @param japaneseBrandNames Set of known brand names (e.g. レクサス) for Car Name column deduplication
     */
    public WorkBookProcessor(Map<String, String> permittedHeaders, Set<String> japaneseBrandNames) {
        this.headerResolver = new HeaderResolver(permittedHeaders);
        this.japaneseBrandNames = japaneseBrandNames != null ? japaneseBrandNames : Set.of();
    }

    /**
     * Creates a processor with an injected {@link HeaderResolver} (for testing).
     */
    public WorkBookProcessor(HeaderResolver headerResolver) {
        this.headerResolver = headerResolver;
        this.japaneseBrandNames = Set.of();
    }

    /**
     * Processes a WorkBookData by:
     * <ol>
     *   <li>Removing columns whose data fill ratio is below {@code columnThreshold}</li>
     *   <li>Resolving the multi-row header range into a single English header label per remaining column</li>
     * </ol>
     * Returns a new WorkBookData — the input is never mutated.
     *
     * @param workBook        The workbook to process
     * @param columnThreshold Minimum fill ratio (0.0–1.0) required to keep a column
     * @return New WorkBookData with sparse columns removed and headers resolved
     */
    public WorkBookData process(WorkBookData workBook, double columnThreshold) {
        log.info("Processing workbook '{}' with column threshold: {}",
                workBook.getFileName(), columnThreshold);

        WorkBookData processed = new WorkBookData();
        processed.setFileName(workBook.getFileName());
        processed.setWorksheetCount(workBook.getWorksheetCount());

        for (WorkSheetData sheet : workBook.getWorksheets()) {
            processed.getWorksheets().add(applyColumnThreshold(sheet, columnThreshold));
        }

        return processed;
    }

    private WorkSheetData applyColumnThreshold(WorkSheetData sheet, double threshold) {
        // Step 1: locate the Car Name column — it is always kept regardless of fill ratio
        int carNameColIndex = findCarNameColumnIndex(sheet);

        // Step 2: determine which columns have enough data to keep
        List<Integer> columnsToKeep = determineColumnsToKeep(sheet, threshold, carNameColIndex);

        // Step 3: resolve multi-row headers into a single label per remaining column
        List<String> resolvedHeaders = headerResolver.resolve(sheet.getRawHeaderRows(), columnsToKeep);

        // Step 4: drop columns whose resolved header is empty (e.g. spacer/footnote columns)
        List<Integer> namedColumns = new ArrayList<>();
        List<String> namedHeaders = new ArrayList<>();
        for (int i = 0; i < columnsToKeep.size(); i++) {
            if (resolvedHeaders.get(i).isEmpty()) {
                log.info("Removing unlabelled column {} from '{}' (no header resolved)",
                        columnsToKeep.get(i), sheet.getName());
            } else {
                namedColumns.add(columnsToKeep.get(i));
                namedHeaders.add(resolvedHeaders.get(i));
            }
        }

        // Step 5: resolve duplicates — for columns sharing the same label, keep the
        //         highest-fill column; if fills are similar, keep all with (2)/(3) suffix
        List<Integer> finalColumns = new ArrayList<>();
        List<String> finalHeaders = new ArrayList<>();
        resolveDuplicates(namedHeaders, namedColumns, sheet.getRows(), sheet.getName(),
                finalHeaders, finalColumns);

        // Step 6: fill down Car Name and Common Name so every data row is fully labelled.
        //         Car Name fills to all data rows; Common Name fills until the next distinct value.
        List<RowData> filteredRows = filterRows(sheet.getRows(), finalColumns);
        List<RowData> filledRows = fillDownGroupColumns(finalHeaders, filteredRows, sheet.getName());

        // Step 7: remove trailing data — footnote rows, notes (e.g. 注), and blank rows
        //         that appear after the last real data row.
        List<RowData> trimmedRows = removeTrailingData(filledRows, sheet.getName());

        WorkSheetData processed = new WorkSheetData();
        processed.setName(sheet.getName());
        processed.setIndex(sheet.getIndex());
        processed.setOriginalRowCount(sheet.getOriginalRowCount());
        processed.setOriginalColumnCount(sheet.getOriginalColumnCount());
        processed.setHeaders(finalHeaders);
        processed.setRows(trimmedRows);

        log.info("Sheet '{}': {} columns → headers: {}",
                sheet.getName(), finalHeaders.size(), finalHeaders);
        return processed;
    }

    /**
     * Minimum fill-rate ratio below which a duplicate-label column is considered a
     * low-value artefact (e.g. a ※ footnote marker column sitting under a merged
     * group header) and dropped in favour of the higher-fill sibling.
     *
     * <p>Example: if the best-fill "Common Name" column has 20% fill, any other
     * "Common Name" column with fill below {@code 20% × 0.5 = 10%} is dropped.
     * Columns with similar fill rates are kept and numbered (2), (3)…
     */
    private static final double DEDUP_FILL_RATIO_THRESHOLD = 0.5;

    /**
     * Resolves duplicate header labels by comparing data fill rates.
     *
     * <p>For each group of columns that share the same resolved label:
     * <ol>
     *   <li>Find the highest fill rate in the group.</li>
     *   <li>Drop any column whose fill is below
     *       {@value #DEDUP_FILL_RATIO_THRESHOLD} × that maximum — these are
     *       typically footnote/spacer columns that inherited their label from a
     *       merged group header.</li>
     *   <li>If multiple columns survive (genuinely distinct data), append
     *       " (2)", " (3)"… to make each label unique.</li>
     * </ol>
     *
     * @param headers     resolved header labels (parallel to colIndices)
     * @param colIndices  original column indices (before any row filtering)
     * @param rows        raw data rows used to compute fill rates
     * @param sheetName   used in log messages
     * @param outHeaders  populated with the final de-duplicated labels
     * @param outCols     populated with the surviving column indices
     */
    private void resolveDuplicates(List<String> headers, List<Integer> colIndices,
                                   List<RowData> rows, String sheetName,
                                   List<String> outHeaders, List<Integer> outCols) {
        int totalRows = rows.size();

        // Group positions by label (preserving insertion order)
        Map<String, List<Integer>> byLabel = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            byLabel.computeIfAbsent(headers.get(i), k -> new ArrayList<>()).add(i);
        }

        // Decide which positions to drop based on fill rate and Car Name heuristics
        Set<Integer> toDrop = new HashSet<>();
        for (Map.Entry<String, List<Integer>> entry : byLabel.entrySet()) {
            List<Integer> positions = entry.getValue();
            if (positions.size() <= 1) continue;

            // For Car Name columns: drop the one where data is mostly the label "車名" (metadata column)
            if (Constants.CAR_NAME_EN.equals(entry.getKey()) && !japaneseBrandNames.isEmpty()) {
                for (int pos : positions) {
                    if (isLabelColumn(rows, colIndices.get(pos), Constants.CAR_NAME_JP)) {
                        toDrop.add(pos);
                        log.info("Sheet '{}': dropping duplicate column {} ('{}') — data is label '車名', not brand names",
                                sheetName, colIndices.get(pos), entry.getKey());
                    }
                }
                positions = positions.stream().filter(p -> !toDrop.contains(p)).toList();
                if (positions.size() <= 1) continue;
            }

            // For Common Name columns: drop the one where data is mostly the label "通称名" (metadata column)
            if (Constants.COMMON_NAME_EN.equals(entry.getKey())) {
                for (int pos : positions) {
                    if (isLabelColumn(rows, colIndices.get(pos), Constants.COMMON_NAME_JP)) {
                        toDrop.add(pos);
                        log.info("Sheet '{}': dropping duplicate column {} ('{}') — data is label '通称名', not model names",
                                sheetName, colIndices.get(pos), entry.getKey());
                    }
                }
                positions = positions.stream().filter(p -> !toDrop.contains(p)).toList();
                if (positions.size() <= 1) continue;
            }

            double maxFill = positions.stream()
                    .mapToDouble(i -> fillRate(rows, colIndices.get(i), totalRows))
                    .max().orElse(0);

            for (int pos : positions) {
                if (toDrop.contains(pos)) continue;
                double fill = fillRate(rows, colIndices.get(pos), totalRows);
                if (maxFill > 0 && fill < maxFill * DEDUP_FILL_RATIO_THRESHOLD) {
                    toDrop.add(pos);
                    log.info("Sheet '{}': dropping duplicate column {} ('{}') — {}% fill vs {}% best fill",
                            sheetName, colIndices.get(pos), entry.getKey(),
                            String.format("%.1f", fill * 100), String.format("%.1f", maxFill * 100));
                }
            }
        }

        // Build output in original order, numbering any remaining duplicates
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            if (toDrop.contains(i)) continue;

            String label = headers.get(i);
            int count = seen.merge(label, 1, Integer::sum);
            String finalLabel = count == 1 ? label : label + " (" + count + ")";

            if (count > 1) {
                log.info("Sheet '{}': duplicate header '{}' renamed to '{}'", sheetName, label, finalLabel);
            }
            outHeaders.add(finalLabel);
            outCols.add(colIndices.get(i));
        }
    }

    /**
     * Minimum number of non-empty cells a row must have to be considered a
     * "data row" for fill-rate comparison purposes. Rows below this threshold
     * are treated as footnote, blank, or annotation rows and excluded from the
     * fill-rate calculation used during duplicate header resolution.
     *
     * <p>Car-specification rows typically contain 7–10+ fields (model type,
     * engine, weight, fuel economy…). Footnote rows contain 1–3 cells of free
     * text. A threshold of 4 reliably separates the two.
     */
    private static final int DATA_ROW_MIN_CELLS = 4;

    /**
     * Calculates the fill rate for {@code colIndex} considering only rows
     * that look like real data rows (have at least {@value DATA_ROW_MIN_CELLS}
     * non-empty cells). This prevents trailing footnote/annotation rows from
     * inflating the fill rate of certain columns and causing the wrong column
     * to be selected as the "winner" during duplicate header resolution.
     *
     * <p>Falls back to the overall fill rate if no data rows are found.
     */
    /**
     * Returns true if this column appears to be a metadata/label column (repeating the
     * given label) rather than actual data. Used to drop duplicate Car Name / Common
     * Name columns in sheets like Lexus where merged headers create false duplicates.
     */
    private boolean isLabelColumn(List<RowData> rows, int colIndex, String label) {
        List<RowData> dataRows = rows.stream()
                .filter(row -> nonEmptyCellCount(row) >= DATA_ROW_MIN_CELLS)
                .toList();
        if (dataRows.isEmpty()) return false;

        long nonEmpty = 0;
        long labelCount = 0;
        for (RowData row : dataRows) {
            String val = row.getCell(colIndex).trim();
            if (val.isEmpty()) continue;
            nonEmpty++;
            if (label.equals(val)) labelCount++;
        }
        if (nonEmpty < 2) return false;
        return (double) labelCount / nonEmpty >= 0.5;
    }

    private double fillRate(List<RowData> rows, int colIndex, int totalRows) {
        if (totalRows == 0) return 0;

        List<RowData> dataRows = rows.stream()
                .filter(row -> nonEmptyCellCount(row) >= DATA_ROW_MIN_CELLS)
                .toList();

        if (dataRows.isEmpty()) {
            return (double) countNonEmptyCells(rows, colIndex) / totalRows;
        }

        long nonEmptyInDataRows = dataRows.stream()
                .filter(row -> !row.getCell(colIndex).trim().isEmpty())
                .count();

        return (double) nonEmptyInDataRows / dataRows.size();
    }

    private int nonEmptyCellCount(RowData row) {
        int count = 0;
        for (String cell : row.getCellValues()) {
            if (!cell.trim().isEmpty()) count++;
        }
        return count;
    }

    /**
     * Fills down the Car Name and Common Name columns so every data row carries
     * an explicit value rather than relying on the "same as above" blank convention
     * used in the source files.
     *
     * <p><b>Car Name</b> — the brand value (e.g. スバル) is propagated to every
     * valid data row in the sheet. There is typically only one brand per sheet.
     *
     * <p><b>Common Name</b> — the model name (e.g. フォレスター) is propagated
     * downward row by row, resetting whenever a new non-empty model name is
     * encountered. This ensures every variant row within a model group is labelled.
     *
     * <p>Fill stops at the last valid data row (determined by
     * {@link #DATA_ROW_MIN_CELLS}), so trailing footnote / annotation rows are
     * not touched.
     */
    private List<RowData> fillDownGroupColumns(List<String> headers, List<RowData> rows, String sheetName) {
        int carNameIdx   = headers.indexOf(Constants.CAR_NAME_EN);
        int commonNameIdx = headers.indexOf(Constants.COMMON_NAME_EN);

        if (carNameIdx < 0 && commonNameIdx < 0) return rows;

        int lastDataRow = findLastDataRowIndex(rows);

        String lastCarName    = "";
        String lastCommonName = "";
        List<RowData> result  = new ArrayList<>(rows.size());

        for (int i = 0; i < rows.size(); i++) {
            RowData row = rows.get(i);
            boolean isDataRow = i <= lastDataRow;
            List<String> cells = new ArrayList<>(row.getCellValues());

            if (carNameIdx >= 0 && carNameIdx < cells.size()) {
                String val = cells.get(carNameIdx).trim();
                if (!val.isEmpty()) {
                    lastCarName = val;
                } else if (isDataRow && !lastCarName.isEmpty()) {
                    cells.set(carNameIdx, lastCarName);
                }
            }

            if (commonNameIdx >= 0 && commonNameIdx < cells.size()) {
                String val = cells.get(commonNameIdx).trim();
                if (!val.isEmpty()) {
                    lastCommonName = val;
                } else if (isDataRow && !lastCommonName.isEmpty()) {
                    cells.set(commonNameIdx, lastCommonName);
                }
            }

            result.add(new RowData(cells));
        }

        log.debug("Sheet '{}': fill-down applied to Car Name (col {}) and Common Name (col {}) through row {}",
                sheetName, carNameIdx, commonNameIdx, lastDataRow);
        return result;
    }

    /**
     * Removes trailing data rows — footnote text (e.g. 注), notes, and blank rows
     * that appear after the last real data row. Real data rows have at least
     * {@value DATA_ROW_MIN_CELLS} non-empty cells.
     *
     * @param rows      Data rows (after fill-down)
     * @param sheetName Used in log messages
     * @return Rows truncated to exclude trailing content
     */
    private List<RowData> removeTrailingData(List<RowData> rows, String sheetName) {
        int lastDataRow = findLastDataRowIndex(rows);
        if (lastDataRow >= rows.size() - 1) {
            return rows;
        }
        int removed = rows.size() - 1 - lastDataRow;
        log.info("Sheet '{}': removing {} trailing row(s) (footnotes/notes/blank after last data row)",
                sheetName, removed);
        return new ArrayList<>(rows.subList(0, lastDataRow + 1));
    }

    /**
     * Returns the index of the last row that has at least {@value DATA_ROW_MIN_CELLS}
     * non-empty cells. Rows beyond this index are treated as footnote / annotation
     * content and are excluded from fill-down propagation.
     */
    private int findLastDataRowIndex(List<RowData> rows) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (nonEmptyCellCount(rows.get(i)) >= DATA_ROW_MIN_CELLS) {
                return i;
            }
        }
        return rows.size() - 1;
    }

    private List<Integer> determineColumnsToKeep(WorkSheetData sheet, double threshold, int protectedColIndex) {
        int totalRows = sheet.getRows().size();
        if (totalRows == 0) {
            log.warn("Sheet '{}' has no data rows, skipping column analysis", sheet.getName());
            return new ArrayList<>();
        }

        List<Integer> columnsToKeep = new ArrayList<>();
        for (int colIndex = 0; colIndex < sheet.getOriginalColumnCount(); colIndex++) {
            double fillPercentage = (double) countNonEmptyCells(sheet.getRows(), colIndex) / totalRows;

            if (colIndex == protectedColIndex) {
                columnsToKeep.add(colIndex);
                log.info("Keeping column {} ('{}') in '{}': {}% fill — protected (Car Name column)",
                        colIndex, Constants.CAR_NAME_JP, sheet.getName(),
                        String.format("%.1f", fillPercentage * 100));
            } else if (fillPercentage >= threshold) {
                columnsToKeep.add(colIndex);
                log.debug("Keeping column {} in '{}': {}% fill",
                        colIndex, sheet.getName(), String.format("%.1f", fillPercentage * 100));
            } else {
                log.info("Removing column {} from '{}': {}% fill (below {}% threshold)",
                        colIndex, sheet.getName(),
                        String.format("%.1f", fillPercentage * 100),
                        String.format("%.1f", threshold * 100));
            }
        }
        return columnsToKeep;
    }

    /**
     * Finds the column index whose raw header rows contain {@link Constants#CAR_NAME_JP}.
     * This column is protected from threshold-based removal.
     *
     * @return The 0-based column index of the Car Name column, or -1 if not found
     */
    private int findCarNameColumnIndex(WorkSheetData sheet) {
        for (List<String> headerRow : sheet.getRawHeaderRows()) {
            for (int colIndex = 0; colIndex < headerRow.size(); colIndex++) {
                if (Constants.CAR_NAME_JP.equals(headerRow.get(colIndex).trim())) {
                    log.debug("Sheet '{}': Car Name column ('{}') found at index {}",
                            sheet.getName(), Constants.CAR_NAME_JP, colIndex);
                    return colIndex;
                }
            }
        }
        log.warn("Sheet '{}': Car Name column ('{}') not found — no column will be protected from threshold",
                sheet.getName(), Constants.CAR_NAME_JP);
        return -1;
    }

    private int countNonEmptyCells(List<RowData> rows, int columnIndex) {
        int count = 0;
        for (RowData row : rows) {
            if (!row.getCell(columnIndex).trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private List<String> filterByIndices(List<String> values, List<Integer> indicesToKeep) {
        List<String> filtered = new ArrayList<>(indicesToKeep.size());
        for (Integer index : indicesToKeep) {
            filtered.add(index < values.size() ? values.get(index) : "");
        }
        return filtered;
    }

    private List<RowData> filterRows(List<RowData> rows, List<Integer> columnsToKeep) {
        List<RowData> filtered = new ArrayList<>(rows.size());
        for (RowData row : rows) {
            filtered.add(new RowData(filterByIndices(row.getCellValues(), columnsToKeep)));
        }
        return filtered;
    }
}
