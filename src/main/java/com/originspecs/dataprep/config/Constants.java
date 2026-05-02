package com.originspecs.dataprep.config;

public class Constants {

    // Default I/O directories — single source of truth referenced by ConfigParser and CliParser
    public static final String DEFAULT_INPUT_DIR    = "src/main/resources/local-data/input";
    public static final String DEFAULT_OUTPUT_DIR   = "src/main/resources/local-data/output";
    /** Directory for immutable copies of original ministry workbooks (one file per generated sourceArtifactId). */
    public static final String ARTIFACTS_DIR        = "src/main/resources/local-data/artifacts";

    // Header constants
    public static final String CAR_NAME_JP = "車名";
    public static final String CAR_NAME_EN = "Car Name";
    public static final String COMMON_NAME_JP = "通称名";
    public static final String COMMON_NAME_EN = "Common Name";

    private Constants(){
    }

}
