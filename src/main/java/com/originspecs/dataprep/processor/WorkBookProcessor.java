package com.originspecs.dataprep.processor;

import com.originspecs.dataprep.model.WorkBookData;

/**
 * Applies column filtering, header resolution, deduplication, and row normalisation to a workbook.
 */
public interface WorkBookProcessor {

    /**
     * @param workBook        workbook as read from source (not mutated)
     * @param columnThreshold minimum fill ratio (0.0–1.0) required to keep a column
     * @return new workbook model with cleaned sheets
     */
    WorkBookData process(WorkBookData workBook, double columnThreshold);
}
