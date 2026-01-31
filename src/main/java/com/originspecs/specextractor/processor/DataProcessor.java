package com.originspecs.specextractor.processor;

import com.originspecs.specextractor.model.DataRecord;
import com.originspecs.specextractor.model.Vehicle;
import com.originspecs.specextractor.reader.FileReader;
import com.originspecs.specextractor.writer.JsonFileWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class DataProcessor<T extends DataRecord> {

    private final RowParser<T> rowParser;
    private final JsonFileWriter<T> jsonWriter;

    public DataProcessor(RowParser<T> rowParser, JsonFileWriter<T> jsonWriter) {
        this.rowParser = rowParser;
        this.jsonWriter = jsonWriter;
    }

    public T createRecordFromRow(Row row, DataFormatter formatter) {
        return rowParser.parse(row, formatter).orElse(null);
    }

    public void convertAndWriteToFile(List<T> records, String outputPath) throws IOException {
        jsonWriter.write(records, Path.of(outputPath));
    }

}