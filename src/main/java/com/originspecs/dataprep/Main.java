package com.originspecs.dataprep;

import com.originspecs.dataprep.application.DataPrepComposition;
import com.originspecs.dataprep.config.CliParser;
import com.originspecs.dataprep.config.Config;
import com.originspecs.dataprep.orchestration.DataPrepOrchestrator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        final Config config;
        try {
            config = CliParser.parse(args);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Invalid arguments or environment: {}", e.getMessage());
            log.error(CliParser.usage());
            System.exit(1);
            return;
        }

        try {
            DataPrepOrchestrator orchestrator = DataPrepComposition.defaultOrchestrator();
            orchestrator.execute(config);
        } catch (Exception e) {
            log.error("Data preparation failed", e);
            System.exit(1);
        }
    }
}
