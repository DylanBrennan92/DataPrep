package com.originspecs.specextractor.processor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.originspecs.specextractor.model.Employee;
import com.originspecs.specextractor.writer.JsonFileWriter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class DataProcessor {

    private final EmployeeRowParser rowParser;
    private final JsonFileWriter jsonWriter;

    public DataProcessor(){
        this.rowParser = new EmployeeRowParser();
        this.jsonWriter = new JsonFileWriter();
    }

    public DataProcessor(EmployeeRowParser rowParser, JsonFileWriter jsonWriter){
        this.rowParser = rowParser;
        this.jsonWriter = jsonWriter;
    }

    public Employee createEmployeeFromRow(Row row, DataFormatter formatter){
        return rowParser.parse(row, formatter).orElse(null);
    }

    public void convertAndWriteToFile(List<Employee> employees, String outputPath) throws IOException{
        jsonWriter.write(employees, Path.of(outputPath));
    }

}
