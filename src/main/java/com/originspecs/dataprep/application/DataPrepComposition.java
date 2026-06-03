package com.originspecs.dataprep.application;

import com.originspecs.dataprep.config.CarListBuilder;
import com.originspecs.dataprep.config.PermittedHeadersBuilder;
import com.originspecs.dataprep.model.CarBrand;
import com.originspecs.dataprep.orchestration.DataPrepOrchestrator;
import com.originspecs.dataprep.processor.DefaultWorkBookProcessor;
import com.originspecs.dataprep.processor.WorkBookProcessor;
import com.originspecs.dataprep.reader.PoiWorkBookReader;
import com.originspecs.dataprep.reader.WorkBookReader;
import com.originspecs.dataprep.writer.PoiWorkBookWriter;
import com.originspecs.dataprep.writer.WorkBookWriter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Composition root: wires concrete adapters and CSV-backed configuration into the orchestrator.
 */
public final class DataPrepComposition {

    private static final String CAR_LIST_FILE = "autoList.csv";

    private DataPrepComposition() {}

    /**
     * Production pipeline using POI reader/writer and default processor with classpath CSV data.
     */
    public static DataPrepOrchestrator defaultOrchestrator() {
        Map<String, String> permittedHeaders = PermittedHeadersBuilder.load();
        Set<String> japaneseBrandNames = loadJapaneseBrandNames();
        WorkBookReader reader = new PoiWorkBookReader(japaneseBrandNames);
        WorkBookProcessor processor = new DefaultWorkBookProcessor(permittedHeaders, japaneseBrandNames);
        WorkBookWriter writer = new PoiWorkBookWriter();
        return new DataPrepOrchestrator(reader, processor, writer);
    }

    private static Set<String> loadJapaneseBrandNames() {
        List<CarBrand> carBrands = CarListBuilder.populateBrandList(CAR_LIST_FILE);
        return carBrands.stream()
                .map(CarBrand::japanese)
                .collect(Collectors.toSet());
    }
}
