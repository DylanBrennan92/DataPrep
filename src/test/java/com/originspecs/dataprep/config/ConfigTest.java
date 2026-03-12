package com.originspecs.dataprep.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Config} argument parsing.
 *
 * Note: {@code Config.validate()} checks the input directory exists on disk, so
 * we test only {@code Config.fromArgs()} here, which is pure parsing logic.
 */
class ConfigTest {

    // --- Happy path ---

    @Test
    void fromArgs_noArguments_usesDefaultThreshold() {
        Config config = Config.fromArgs(new String[]{});

        assertThat(config.inputDir()).isEqualTo(Path.of("src/main/resources/local-data/input"));
        assertThat(config.outputDir()).isEqualTo(Path.of("src/main/resources/local-data/output"));
        assertThat(config.columnThreshold()).isEqualTo(0.01);
    }

    @Test
    void fromArgs_singleArgument_usesAsThreshold() {
        Config config = Config.fromArgs(new String[]{"0.05"});

        assertThat(config.inputDir()).isEqualTo(Path.of("src/main/resources/local-data/input"));
        assertThat(config.outputDir()).isEqualTo(Path.of("src/main/resources/local-data/output"));
        assertThat(config.columnThreshold()).isEqualTo(0.05);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "1.0", "0.5", "0.01"})
    void fromArgs_boundaryAndTypicalThresholds_areAccepted(String threshold) {
        Config config = Config.fromArgs(new String[]{threshold});

        assertThat(config.columnThreshold()).isBetween(0.0, 1.0);
    }

    // --- Wrong argument count ---

    @Test
    void fromArgs_tooManyArguments_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Config.fromArgs(new String[]{"0.01", "extra"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At most 1 argument");
    }

    // --- Invalid threshold ---

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "1.1", "2.0", "-1.0"})
    void fromArgs_thresholdOutOfRange_throwsIllegalArgumentException(String threshold) {
        assertThatThrownBy(() -> Config.fromArgs(new String[]{threshold}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columnThreshold");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "0.1x", "NaN"})
    void fromArgs_thresholdNotNumeric_throwsIllegalArgumentException(String threshold) {
        assertThatThrownBy(() -> Config.fromArgs(new String[]{threshold}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columnThreshold");
    }
}
