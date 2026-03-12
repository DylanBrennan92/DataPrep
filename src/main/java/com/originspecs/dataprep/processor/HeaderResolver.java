package com.originspecs.dataprep.processor;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves multi-row header data into a single English header label per column.
 *
 * <p>Resolution order per column:
 * <ol>
 *   <li>Scan header rows bottom-to-top (most specific → most general).</li>
 *   <li>Return the English translation of the first value that matches a permitted header.</li>
 *   <li>If no permitted match is found, fall back to the bottom-most non-empty value and log a warning
 *       (so unrecognised labels can be added to permittedHeaders.csv over time).</li>
 *   <li>If all header rows are empty for a column, return an empty string.</li>
 * </ol>
 *
 * <p>If no permitted headers map is provided (empty map), behaviour falls back to
 * the bottom-most non-empty value for every column.
 */
@Slf4j
public class HeaderResolver {

    private final Map<String, String> permittedHeaders;

    /**
     * Creates a resolver that matches and translates using the given permitted headers map.
     *
     * @param permittedHeaders Map of Japanese label → English label loaded from permittedHeaders.csv
     */
    public HeaderResolver(Map<String, String> permittedHeaders) {
        this.permittedHeaders = permittedHeaders;
        log.debug("HeaderResolver initialised with {} permitted header entries", permittedHeaders.size());
    }

    /**
     * Resolves a single header label for each column in {@code columnsToKeep}.
     *
     * @param rawHeaderRows All header rows in top-to-bottom order
     * @param columnsToKeep The column indices to resolve (already filtered by threshold)
     * @return Resolved single-row header list, one label per kept column
     */
    public List<String> resolve(List<List<String>> rawHeaderRows, List<Integer> columnsToKeep) {
        List<String> resolved = new ArrayList<>(columnsToKeep.size());

        for (int colIndex : columnsToKeep) {
            resolved.add(resolveColumn(rawHeaderRows, colIndex));
        }

        log.debug("Resolved {} header labels from {} header row(s)", resolved.size(), rawHeaderRows.size());
        return resolved;
    }

    private String resolveColumn(List<List<String>> rawHeaderRows, int colIndex) {
        String fallback = "";

        // Scan bottom-to-top (most specific label first)
        for (int rowIdx = rawHeaderRows.size() - 1; rowIdx >= 0; rowIdx--) {
            List<String> headerRow = rawHeaderRows.get(rowIdx);
            if (colIndex >= headerRow.size()) continue;

            String raw = headerRow.get(colIndex).trim();
            if (raw.isEmpty()) continue;

            // Normalise embedded newlines/whitespace so multi-line XLS cells match clean CSV keys
            String value = normalize(raw);

            // Record the first non-empty value as fallback before checking permitted list
            if (fallback.isEmpty()) {
                fallback = value;
            }

            if (permittedHeaders.containsKey(value)) {
                String resolved = permittedHeaders.get(value);
                // 型式 under 原動機 (Engine) group → Engine; otherwise Model Type
                if ("Model Type".equals(resolved) && isUnderEngineGroup(rawHeaderRows, colIndex, rowIdx)) {
                    return "Engine";
                }
                return resolved;
            }
        }

        if (fallback.isEmpty()) {
            log.warn("Column {}: no header label found in {} header row(s)", colIndex, rawHeaderRows.size());
        } else if (!permittedHeaders.isEmpty()) {
            log.warn("Column {}: no permitted match found for '{}' — using as-is (add to permittedHeaders.csv if needed)",
                    colIndex, fallback);
        }

        return fallback;
    }

    /**
     * Returns true if this column has 原動機 (Engine) in any header row above the given row.
     * Used to disambiguate 型式: vehicle model type vs engine model type under 原動機.
     */
    private static boolean isUnderEngineGroup(List<List<String>> rawHeaderRows, int colIndex, int belowRowIdx) {
        for (int r = 0; r < belowRowIdx; r++) {
            if (colIndex >= rawHeaderRows.get(r).size()) continue;
            String cell = normalize(rawHeaderRows.get(r).get(colIndex));
            if ("原動機".equals(cell)) return true;
        }
        return false;
    }

    /**
     * Strips embedded newlines and collapses whitespace so that multi-line cell values
     * (common in Japanese government XLS files) match the clean keys in permittedHeaders.csv.
     * e.g. "総排\n気量\n（L）" → "総排気量（L）"
     */
    private static String normalize(String value) {
        return value.replace("\r\n", "").replace("\r", "").replace("\n", "").strip();
    }
}
