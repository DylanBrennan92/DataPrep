package com.originspecs.dataprep.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliParserTest {

    @Test
    void shouldReturnValidatedConfig_whenArgumentsAreValid(@TempDir Path temp) throws Exception {
        Path input = temp.resolve("input");
        Files.createDirectories(input);
        Files.createFile(input.resolve("book.xls"));

        Config config = CliParser.parse(new String[]{"0.05"});

        assertThat(config.columnThreshold()).isEqualTo(0.05);
    }

    @Test
    void shouldThrow_whenUnsupportedFlag() {
        assertThatThrownBy(() -> CliParser.parse(new String[]{"--help"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported option");
    }

    @Test
    void shouldExposeUsageText() {
        assertThat(CliParser.usage())
                .contains("columnThreshold")
                .contains(Constants.DEFAULT_INPUT_DIR);
    }
}
