package com.originspecs.dataprep.model;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;

@Data
public class WorkSheetData {

    private String name;
    private int index;

    // All rows within the detected header range, stored in order (top → bottom).
    // Used by DefaultWorkBookProcessor to resolve a single header label per column.
    // Empty after processing — only the resolved headers field is carried forward.
    private List<List<String>> rawHeaderRows = new ArrayList<>();

    // Resolved single-row headers: one label per column, set by DefaultWorkBookProcessor
    // after column filtering and header resolution.
    private List<String> headers = new ArrayList<>();

    private List<RowData> rows = new ArrayList<>();

    // Original sheet dimensions before any processing
    private int originalRowCount;
    private int originalColumnCount;

    // Header range metadata: 0-based row indices as detected in the raw sheet
    private int headerRangeStart;
    private int headerRangeEnd;
}
