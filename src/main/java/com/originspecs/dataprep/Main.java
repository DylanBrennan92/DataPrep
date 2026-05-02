package com.originspecs.dataprep;

import com.originspecs.dataprep.config.Config;
import com.originspecs.dataprep.config.CliParser;
import com.originspecs.dataprep.orchestration.DataPrepOrchestrator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {

    public static void main(String[] args) {
        Config config = CliParser.parseOrExit(args);

        try {
            DataPrepOrchestrator orchestrator = new DataPrepOrchestrator();
            orchestrator.execute(config);
        } catch (Exception e) {
            log.error("Data preparation failed", e);
            System.exit(1);
        }
    }
}