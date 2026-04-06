package com.originspecs.dataprep.orchestration;

import com.originspecs.dataprep.artifact.SourceArtifactPaths;
import com.originspecs.dataprep.config.CarListBuilder;
import com.originspecs.dataprep.config.Config;
import com.originspecs.dataprep.config.PermittedHeadersBuilder;
import com.originspecs.dataprep.input.InputWorkbooks;
import com.originspecs.dataprep.model.CarBrand;
import com.originspecs.dataprep.model.WorkBookData;
import com.originspecs.dataprep.processor.WorkBookProcessor;
import com.originspecs.dataprep.reader.WorkBookReader;
import com.originspecs.dataprep.writer.WorkBookWriter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
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
    private final Supplier<UUID> newSourceArtifactId;

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
        this.newSourceArtifactId = UUID::randomUUID;
    }

    /**
     * Constructor for tests — inject components; each workbook gets a random {@link UUID} from {@link UUID#randomUUID()}.
     */
    public DataPrepOrchestrator(WorkBookReader reader, WorkBookProcessor processor, WorkBookWriter writer) {
        this(reader, processor, writer, UUID::randomUUID);
    }

    /**
     * Full constructor for testing — inject any implementation of each component and UUID source.
     */
    public DataPrepOrchestrator(
            WorkBookReader reader,
            WorkBookProcessor processor,
            WorkBookWriter writer,
            Supplier<UUID> newSourceArtifactId) {
        this.reader = reader;
        this.processor = processor;
        this.writer = writer;
        this.newSourceArtifactId = Objects.requireNonNull(newSourceArtifactId, "newSourceArtifactId");
    }

    /**
     * Executes the complete data preparation pipeline for all .xls files in the input directory.
     *
     * @param config Configuration containing input/output/artifacts directories and column threshold
     * @throws IOException if reading or writing fails
     */
    public void execute(Config config) throws IOException {
        log.info("Starting data preparation pipeline");
        log.info("Input dir: {} | Output dir: {} | Artifacts dir: {} | Column threshold: {}",
                config.inputDir(), config.outputDir(), config.artifactsDir(), config.columnThreshold());

        Files.createDirectories(config.outputDir());
        Files.createDirectories(config.artifactsDir());

        List<Path> inputFiles = InputWorkbooks.listSorted(config.inputDir());
        if (inputFiles.isEmpty()) {
            log.warn("No .xls files to process — exiting");
            return;
        }

        for (Path inputFile : inputFiles) {
            Path outputFile = config.outputFileFor(inputFile);
            UUID sourceArtifactId = newSourceArtifactId.get();
            String idString = sourceArtifactId.toString();
            Path artifactPath = SourceArtifactPaths.artifactFile(config.artifactsDir(), idString, inputFile);

            log.info("Processing: {} → {} | sourceArtifactId: {}",
                    inputFile.getFileName(), outputFile.getFileName(), idString);
            Files.copy(inputFile, artifactPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied source artifact: {}", artifactPath.toAbsolutePath());

            WorkBookData workBook = read(inputFile);
            WorkBookData processed = process(workBook, config.columnThreshold());
            write(processed, outputFile);

            Path sidecar = SourceArtifactPaths.sourceArtifactIdSidecar(outputFile);
            Files.writeString(sidecar, idString, StandardCharsets.UTF_8);
            log.info("Wrote lineage sidecar: {} (use this UUID with spec-extractor --source-artifact-id)",
                    sidecar.toAbsolutePath());
        }

        log.info("Pipeline completed successfully — {} file(s) processed", inputFiles.size());
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
