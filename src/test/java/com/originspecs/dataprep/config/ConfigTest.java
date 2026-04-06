package com.originspecs.dataprep.config;

import com.originspecs.dataprep.artifact.SourceArtifactPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ConfigParser}, {@link ConfigValidator}, and path helpers.
 */
class ConfigTest {

    private static final UUID SAMPLE_ARTIFACT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void shouldUseDefaultThreshold_whenNoArguments() {
        Config config = ConfigParser.parse(new String[]{});

        assertThat(config.inputDir()).isEqualTo(Path.of("src/main/resources/local-data/input"));
        assertThat(config.outputDir()).isEqualTo(Path.of("src/main/resources/local-data/output"));
        assertThat(config.artifactsDir()).isEqualTo(Path.of(Constants.ARTIFACTS_DIR));
        assertThat(config.columnThreshold()).isEqualTo(0.01);
    }

    @Test
    void shouldUseGivenThreshold_whenSingleNumericArgument() {
        Config config = ConfigParser.parse(new String[]{"0.05"});

        assertThat(config.columnThreshold()).isEqualTo(0.05);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "1.0", "0.5", "0.01"})
    void shouldAcceptThreshold_whenValueInRange(String threshold) {
        Config config = ConfigParser.parse(new String[]{threshold});

        assertThat(config.columnThreshold()).isBetween(0.0, 1.0);
    }

    @Test
    void shouldThrow_whenUnsupportedFlag() {
        assertThatThrownBy(() -> ConfigParser.parse(new String[]{"--source-artifact-id", SAMPLE_ARTIFACT_ID.toString()}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported option");
    }

    @Test
    void shouldThrow_whenMoreThanOneArgument() {
        assertThatThrownBy(() -> ConfigParser.parse(new String[]{"0.01", "extra"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At most one columnThreshold");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "1.1", "2.0", "-1.0"})
    void shouldThrow_whenThresholdOutOfRange(String threshold) {
        assertThatThrownBy(() -> ConfigParser.parse(new String[]{threshold}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columnThreshold");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "0.1x", "NaN"})
    void shouldThrow_whenThresholdNotNumeric(String threshold) {
        assertThatThrownBy(() -> ConfigParser.parse(new String[]{threshold}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columnThreshold");
    }

    @Test
    void shouldValidate_whenInputDirExistsWithAnyNumberOfXls(@TempDir Path temp) throws Exception {
        Path input = temp.resolve("input");
        Files.createDirectories(input);
        Files.createFile(input.resolve("a.xls"));
        Files.createFile(input.resolve("b.xls"));

        Config config = new Config(
                input, temp.resolve("out"), temp.resolve("artifacts"), 0.01);

        ConfigValidator.validate(config);
    }

    @Test
    void shouldResolveArtifactPathWithOriginalExtension_whenArtifactFileCalled() {
        Path artifacts = Path.of("/tmp/artifacts");
        Path input = Path.of("ignored/dir/ministry_data.XLS");
        Path dest = SourceArtifactPaths.artifactFile(artifacts, SAMPLE_ARTIFACT_ID.toString(), input);

        assertThat(dest.getParent()).isEqualTo(artifacts);
        assertThat(dest.getFileName().toString()).isEqualTo(SAMPLE_ARTIFACT_ID + ".XLS");
    }

    @Test
    void shouldResolveSidecarNextToOutput_whenSourceArtifactIdSidecarCalled() {
        Path output = Path.of("/data/local/output/cleaned.xls");
        Path sidecar = SourceArtifactPaths.sourceArtifactIdSidecar(output);

        assertThat(sidecar.getParent()).isEqualTo(output.getParent());
        assertThat(sidecar.getFileName().toString()).isEqualTo("cleaned.xls" + SourceArtifactPaths.SOURCE_ARTIFACT_ID_SIDECAR_SUFFIX);
    }
}
