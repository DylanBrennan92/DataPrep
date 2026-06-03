package com.originspecs.dataprep.reader;

import com.originspecs.dataprep.model.WorkBookData;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Reads a ministry .xls workbook into the in-memory {@link WorkBookData} model.
 */
public interface WorkBookReader {

    /**
     * @param inputPath path to the .xls file
     * @return workbook model including raw header rows per sheet
     * @throws IOException if the file cannot be read
     */
    WorkBookData read(Path inputPath) throws IOException;
}
