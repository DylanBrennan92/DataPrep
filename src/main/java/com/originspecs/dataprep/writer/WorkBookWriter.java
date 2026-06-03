package com.originspecs.dataprep.writer;

import com.originspecs.dataprep.model.WorkBookData;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Persists a processed {@link WorkBookData} model to an .xls file.
 */
public interface WorkBookWriter {

    /**
     * @param workBook   processed workbook model
     * @param outputPath destination .xls path
     * @throws IOException if the file cannot be written
     */
    void write(WorkBookData workBook, Path outputPath) throws IOException;
}
