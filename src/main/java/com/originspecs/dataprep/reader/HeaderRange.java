package com.originspecs.dataprep.reader;

/**
 * Represents the detected range of header rows within an XLS sheet.
 * Row indices are 0-based (as returned by Apache POI).
 *
 * <p>When the sheet has metadata columns to the left of the Car Name column (e.g. Lexus
 * sheets), {@code carNameColumnIndex} identifies the column containing 車名 that actually
 * holds brand/model data. Columns before this index are metadata and should be excluded.
 *
 * <p>Example: a sheet with metadata on rows 0-2, column headers on rows 3-7,
 * and data starting on row 8 would produce HeaderRange(startRowIndex=3, endRowIndex=7).
 */
public record HeaderRange(int startRowIndex, int endRowIndex, int carNameColumnIndex) {

    /** Creates a range with carNameColumnIndex=0 (assumes Car Name is in column A). */
    public HeaderRange(int startRowIndex, int endRowIndex) {
        this(startRowIndex, endRowIndex, 0);
    }

    /**
     * The row index where actual data begins (one row after the last header row).
     */
    public int dataStartRowIndex() {
        return endRowIndex + 1;
    }

    /**
     * Returns true if the given row index falls within the header range.
     */
    public boolean isHeaderRow(int rowIndex) {
        return rowIndex >= startRowIndex && rowIndex <= endRowIndex;
    }

    /**
     * Returns true if the given row index is before the header range (metadata/pre-header rows).
     */
    public boolean isPreHeaderRow(int rowIndex) {
        return rowIndex < startRowIndex;
    }
}
