package com.originspecs.dataprep.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ConfigParser}, {@link ConfigValidator}, and {@link Config} path helpers.
 */
class ConfigTest {

    private static final UUID SAMPLE_ARTIFACT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void shouldUseDefaultThresholdAndNoArtifactId_whenNoArguments() {
        Config config = ConfigParser.parse(new String[]{});

        assertThat(config.inputDir()).isEqualTo(Path.of("src/main/resources/local-data/input"));
        assertThat(config.outputDir()).isEqualTo(Path.of("src/main/resources/local-data/output"));
        assertThat(config.artifactsDir()).isEqualTo(Path.of(Constants.ARTIFACTS_DIR));
        assertThat(config.sourceArtifactId()).isEmpty();
        assertThat(config.columnThreshold()).isEqualTo(0.01);
    }

    @Test
    void shouldUseGivenThreshold_whenSingleNumericArgument() {
        Config config = ConfigParser.parse(new String[]{"0.05"});

        assertThat(config.columnThreshold()).isEqualTo(0.05);
        assertThat(config.sourceArtifactId()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "1.0", "0.5", "0.01"})
    void shouldAcceptThreshold_whenValueInRange(String threshold) {
        Config config = ConfigParser.parse(new String[]{threshold});

        assertThat(config.columnThreshold()).isBetween(0.0, 1.0);
    }

    @Test
    void shouldSetSourceArtifactIdAndDefaultThreshold_whenOnlyFlagAndId() {
        String id = SAMPLE_ARTIFACT_ID.toString();
        Config config = ConfigParser.parse(new String[]{"--source-artifact-id", id});

        assertThat(config.sourceArtifactId()).contains(id);
        assertThat(config.columnThreshold()).isEqualTo(0.01);
    }

    @Test
    void shouldParseFlagAndThresholdInEitherOrder_whenBothProvided() {
        String id = SAMPLE_ARTIFACT_ID.toString();
        Config first = ConfigParser.parse(new String[]{"--source-artifact-id", id, "0.2"});
        Config second = ConfigParser.parse(new String[]{"0.2", "--source-artifact-id", id});

        assertThat(first.sourceArtifactId()).contains(id);
        assertThat(first.columnThreshold()).isEqualTo(0.2);
        assertThat(second.sourceArtifactId()).contains(id);
        assertThat(second.columnThreshold()).isEqualTo(0.2);
    }

    @Test
    void shouldThrow_whenSourceArtifactFlagHasNoValue() {
        assertThatThrownBy(() -> ConfigParser.parse(new String[]{"--source-artifact-id"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing value");
    }

    @Test
    void shouldThrow_whenSourceArtifactIdIsInvalidUuid() {
        assertThatThrownBy(() -> ConfigParser.parse(new String[]{"--source-artifact-id", "not-a-uuid"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrow_whenDuplicateSourceArtifactFlag() {
        String id = SAMPLE_ARTIFACT_ID.toString();
        assertThatThrownBy(() -> ConfigParser.parse(new String[]{"--source-artifact-id", id, "--source-artifact-id", id}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate")
                .hasMessageContaining("--source-artifact-id");
    }

    @Test
    void shouldThrow_whenMoreThanOneThresholdToken() {
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
    void shouldSucceed_whenSourceArtifactIdSetAndExactlyOneXls(@TempDir Path temp) throws Exception {
        Path input = temp.resolve("input");
        Files.createDirectories(input);
        Files.createFile(input.resolve("book.xls"));

        Config config = new Config(
                input, temp.resolve("out"), temp.resolve("artifacts"),
                Optional.of(SAMPLE_ARTIFACT_ID.toString()), 0.01);

        ConfigValidator.validate(config);
    }

    @Test
    void shouldThrow_whenSourceArtifactIdSetAndZeroXls(@TempDir Path temp) throws Exception {
        Path input = temp.resolve("input");
        Files.createDirectories(input);

        Config config = new Config(
                input, temp.resolve("out"), temp.resolve("artifacts"),
                Optional.of(SAMPLE_ARTIFACT_ID.toString()), 0.01);

        assertThatThrownBy(() -> ConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one .xls");
    }

    @Test
    void shouldThrow_whenSourceArtifactIdSetAndTwoXls(@TempDir Path temp) throws Exception {
        Path input = temp.resolve("input");
        Files.createDirectories(input);
        Files.createFile(input.resolve("a.xls"));
        Files.createFile(input.resolve("b.xls"));

        Config config = new Config(
                input, temp.resolve("out"), temp.resolve("artifacts"),
                Optional.of(SAMPLE_ARTIFACT_ID.toString()), 0.01);

        assertThatThrownBy(() -> ConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one .xls");
    }

    @Test
    void shouldResolveArtifactPathWithOriginalExtension_whenArtifactCopyDestinationCalled() {
        Path artifacts = Path.of("/tmp/artifacts");
        Path input = Path.of("ignored/dir/ministry_data.XLS");
        Config config = new Config(
                Path.of("in"), Path.of("out"), artifacts,
                Optional.of(SAMPLE_ARTIFACT_ID.toString()), 0.01);

        Path dest = config.artifactCopyDestination(input);

        assertThat(dest.getParent()).isEqualTo(artifacts);
        assertThat(dest.getFileName().toString()).isEqualTo(SAMPLE_ARTIFACT_ID + ".XLS");
    }
}
