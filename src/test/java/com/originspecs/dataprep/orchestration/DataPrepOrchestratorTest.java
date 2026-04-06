package com.originspecs.dataprep.orchestration;

import com.originspecs.dataprep.artifact.SourceArtifactPaths;
import com.originspecs.dataprep.config.Config;
import com.originspecs.dataprep.model.WorkBookData;
import com.originspecs.dataprep.processor.WorkBookProcessor;
import com.originspecs.dataprep.reader.WorkBookReader;
import com.originspecs.dataprep.writer.WorkBookWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPrepOrchestratorTest {

    private static final UUID ID_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID ID_B = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    @Mock
    private WorkBookReader reader;
    @Mock
    private WorkBookProcessor processor;
    @Mock
    private WorkBookWriter writer;

    @Test
    void shouldCopyArtifactWriteSidecarAndProcess_whenOneInputFile(@TempDir Path temp) throws Exception {
        Path inputDir = temp.resolve("input");
        Path outputDir = temp.resolve("output");
        Path artifactsDir = temp.resolve("artifacts");
        Files.createDirectories(inputDir);
        Path xls = inputDir.resolve("book.xls");
        Files.writeString(xls, "dummy-bytes");

        Config config = new Config(inputDir, outputDir, artifactsDir, 0.01);
        WorkBookData data = new WorkBookData();
        data.setFileName("book.xls");
        when(reader.read(xls)).thenReturn(data);
        when(processor.process(eq(data), eq(0.01))).thenReturn(data);

        DataPrepOrchestrator orchestrator = new DataPrepOrchestrator(reader, processor, writer, () -> ID_A);
        orchestrator.execute(config);

        Path artifact = artifactsDir.resolve(ID_A + ".xls");
        assertThat(Files.readString(artifact)).isEqualTo("dummy-bytes");

        Path outXls = outputDir.resolve("book.xls");
        Path sidecar = SourceArtifactPaths.sourceArtifactIdSidecar(outXls);
        assertThat(Files.readString(sidecar)).isEqualTo(ID_A.toString());

        verify(writer).write(eq(data), eq(outXls));
    }

    @Test
    void shouldUseDistinctUuidPerFile_whenMultipleInputFiles(@TempDir Path temp) throws Exception {
        Path inputDir = temp.resolve("input");
        Path outputDir = temp.resolve("output");
        Path artifactsDir = temp.resolve("artifacts");
        Files.createDirectories(inputDir);
        Path first = inputDir.resolve("a.xls");
        Path second = inputDir.resolve("b.xls");
        Files.writeString(first, "a");
        Files.writeString(second, "b");

        Config config = new Config(inputDir, outputDir, artifactsDir, 0.02);
        AtomicInteger idx = new AtomicInteger();
        List<UUID> ids = List.of(ID_A, ID_B);
        WorkBookData dataA = new WorkBookData();
        dataA.setFileName("a.xls");
        WorkBookData dataB = new WorkBookData();
        dataB.setFileName("b.xls");
        when(reader.read(first)).thenReturn(dataA);
        when(reader.read(second)).thenReturn(dataB);
        when(processor.process(any(WorkBookData.class), anyDouble())).thenAnswer(inv -> inv.getArgument(0));

        DataPrepOrchestrator orchestrator = new DataPrepOrchestrator(
                reader, processor, writer, () -> ids.get(idx.getAndIncrement()));

        orchestrator.execute(config);

        assertThat(Files.readString(artifactsDir.resolve(ID_A + ".xls"))).isEqualTo("a");
        assertThat(Files.readString(artifactsDir.resolve(ID_B + ".xls"))).isEqualTo("b");
        assertThat(Files.readString(SourceArtifactPaths.sourceArtifactIdSidecar(outputDir.resolve("a.xls"))))
                .isEqualTo(ID_A.toString());
        assertThat(Files.readString(SourceArtifactPaths.sourceArtifactIdSidecar(outputDir.resolve("b.xls"))))
                .isEqualTo(ID_B.toString());
    }
}
