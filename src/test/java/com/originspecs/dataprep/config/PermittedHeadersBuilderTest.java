package com.originspecs.dataprep.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PermittedHeadersBuilder}.
 */
class PermittedHeadersBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void load_validCsvFile_returnsMappingOfAllEntries() throws IOException {
        Path csv = writeFile("headers.csv",
                "japanese,english",
                "車名,Car Name",
                "通称名,Common Name",
                "型式,Model Type");

        Map<String, String> result = PermittedHeadersBuilder.load(csv.toString());

        assertThat(result)
                .containsEntry("車名", "Car Name")
                .containsEntry("通称名", "Common Name")
                .containsEntry("型式", "Model Type")
                .hasSize(3);
    }

    @Test
    void load_csvWithBlankLines_blankLinesAreSkipped() throws IOException {
        Path csv = writeFile("headers.csv",
                "japanese,english",
                "車名,Car Name",
                "",
                "通称名,Common Name",
                "");

        Map<String, String> result = PermittedHeadersBuilder.load(csv.toString());

        assertThat(result).hasSize(2);
    }

    @Test
    void load_headerRowIsSkipped_notIncludedInResult() throws IOException {
        Path csv = writeFile("headers.csv",
                "japanese,english",
                "車名,Car Name");

        Map<String, String> result = PermittedHeadersBuilder.load(csv.toString());

        assertThat(result).doesNotContainKey("japanese");
        assertThat(result).hasSize(1);
    }

    @Test
    void load_duplicateJapaneseKeys_firstValueWins() throws IOException {
        Path csv = writeFile("headers.csv",
                "japanese,english",
                "車名,Car Name",
                "車名,Vehicle Name");

        Map<String, String> result = PermittedHeadersBuilder.load(csv.toString());

        assertThat(result.get("車名")).isEqualTo("Car Name");
    }

    @Test
    void load_fromTestResources_loadsFile() throws URISyntaxException {
        Path testResource = Paths.get(
                Objects.requireNonNull(
                        PermittedHeadersBuilderTest.class.getResource("/local-data/permittedHeaders.csv"),
                        "classpath resource /local-data/permittedHeaders.csv (from src/test/resources)")
                        .toURI());
        Map<String, String> result = PermittedHeadersBuilder.load(testResource.toString());

        assertThat(result)
                .containsEntry("車名", "Car Name")
                .containsEntry("通称名", "Common Name")
                .containsEntry("型式", "Model Type")
                .hasSize(3);
    }

    @Test
    void load_missingFile_returnsEmptyMapWithoutThrowing() {
        Path nonExistent = tempDir.resolve("missing").resolve("headers.csv");
        Map<String, String> result = PermittedHeadersBuilder.load(nonExistent.toString());

        assertThat(result).isEmpty();
    }

    // --- Helper ---

    private Path writeFile(String filename, String... lines) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, String.join("\n", lines));
        return file;
    }
}
