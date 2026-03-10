package com.originspecs.dataprep.orchestration;

import com.originspecs.dataprep.config.CarListBuilder;
import com.originspecs.dataprep.config.Config;
import com.originspecs.dataprep.config.PermittedHeadersBuilder;
import com.originspecs.dataprep.model.CarBrand;
import com.originspecs.dataprep.model.WorkBookData;
import com.originspecs.dataprep.processor.WorkBookProcessor;
import com.originspecs.dataprep.reader.WorkBookReader;
import com.originspecs.dataprep.writer.WorkBookWriter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates the complete data preparation pipeline: read → process → write.
 * Loads permitted headers and car brand lists on startup and wires all components.
 * Contains no business logic.
 */
@Slf4j
public class DataPrepOrchestrator {

    private final WorkBookReader reader;
    private final WorkBookProcessor processor;
    private final WorkBookWriter writer;

    /**
     * Default constructor: loads permitted headers and car brands from their
     * respective CSV files and wires all components.
     */
    public DataPrepOrchestrator() {
        Map<String, String> permittedHeaders = PermittedHeadersBuilder.load();
        List<CarBrand> carBrands = CarListBuilder.populateBrandList("autoList.csv");
        Set<String> japaneseBrandNames = carBrands.stream()
                .map(CarBrand::japanese)
                .collect(Collectors.toSet());

        this.reader = new WorkBookReader(japaneseBrandNames);
        this.processor = new WorkBookProcessor(permittedHeaders, japaneseBrandNames);
        this.writer = new WorkBookWriter();
    }

    /**
     * Full constructor for testing — inject any implementation of each component.
     */
    public DataPrepOrchestrator(WorkBookReader reader, WorkBookProcessor processor, WorkBookWriter writer) {
        this.reader = reader;
        this.processor = processor;
        this.writer = writer;
    }

    /**
     * Executes the complete data preparation pipeline.
     *
     * @param config Configuration containing input/output paths and processing parameters
     * @throws IOException if reading or writing fails
     */
    public void execute(Config config) throws IOException {
        log.info("Starting data preparation pipeline");
        log.info("Input: {} | Output: {} | Column threshold: {}",
                config.inputFile(), config.outputFile(), config.columnThreshold());

        WorkBookData workBook = read(config.inputFile());
        WorkBookData processed = process(workBook, config.columnThreshold());
        write(processed, config.outputFile());

        log.info("Pipeline completed successfully");
    }

    private WorkBookData read(Path inputFile) throws IOException {
        log.debug("Reading workbook");
        return reader.read(inputFile);
    }

    private WorkBookData process(WorkBookData workBook, double columnThreshold) {
        log.debug("Processing workbook");
        return processor.process(workBook, columnThreshold);
    }

    private void write(WorkBookData workBook, Path outputFile) throws IOException {
        log.debug("Writing workbook");
        writer.write(workBook, outputFile);
    }
}
