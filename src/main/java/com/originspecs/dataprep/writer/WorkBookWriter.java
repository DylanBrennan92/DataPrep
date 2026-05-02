package com.originspecs.dataprep.writer;

import com.originspecs.dataprep.model.RowData;
import com.originspecs.dataprep.model.WorkBookData;
import com.originspecs.dataprep.model.WorkSheetData;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class WorkBookWriter {

    /** Hard row limit for the legacy HSSF (.xls) format. */
    static final int XLS_MAX_ROWS = 65_536;

    /**
     * Writes a WorkBookData model to a new .xls file, preserving the sheet structure.
     * Headers are written as the first row of each sheet.
     *
     * @param workBook   The workbook model to write
     * @param outputPath Where to save the new file
     */
    public void write(WorkBookData workBook, Path outputPath) throws IOException {
        log.info("Writing workbook '{}' to {}", workBook.getFileName(), outputPath.toAbsolutePath());

        ensureOutputDirectoryExists(outputPath);

        try (Workbook workbook = new HSSFWorkbook();
             OutputStream os = Files.newOutputStream(outputPath)) {

            for (WorkSheetData sheetData : workBook.getWorksheets()) {
                Sheet sheet = workbook.createSheet(sheetData.getName());
                writeSheet(sheet, sheetData);
            }

            workbook.write(os);
            log.info("Workbook written successfully: {} sheet(s)", workBook.getWorksheetCount());
        }
    }

    private void writeSheet(Sheet sheet, WorkSheetData sheetData) {
        int rowIndex = 0;

        // 1 header row + N data rows — warn early so truncation is never silent
        int totalRows = (sheetData.getHeaders().isEmpty() ? 0 : 1) + sheetData.getRows().size();
        if (totalRows > XLS_MAX_ROWS) {
            log.warn("Sheet '{}': {} rows exceeds XLS limit of {} — output will be truncated at row {}",
                    sheetData.getName(), totalRows, XLS_MAX_ROWS, XLS_MAX_ROWS);
        }

        if (!sheetData.getHeaders().isEmpty()) {
            writeRow(sheet.createRow(rowIndex++), sheetData.getHeaders());
        }

        for (RowData rowData : sheetData.getRows()) {
            writeRow(sheet.createRow(rowIndex++), rowData.getCellValues());
        }

        log.debug("Sheet '{}' written: {} row(s)", sheetData.getName(), rowIndex);
    }

    private void writeRow(Row row, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            row.createCell(i).setCellValue(values.get(i));
        }
    }

    private void ensureOutputDirectoryExists(Path outputPath) throws IOException {
        Path parentDir = outputPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            log.info("Creating output directory: {}", parentDir);
            Files.createDirectories(parentDir);
        }
    }
}
