package com.originspecs.dataprep.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InputWorkbooksTest {

    @Test
    void shouldReturnSortedXlsOnly_whenMultipleFilesInDirectory(@TempDir Path temp) throws Exception {
        Files.createFile(temp.resolve("b.xls"));
        Files.createFile(temp.resolve("notes.txt"));
        Files.createFile(temp.resolve("a.xls"));

        List<Path> paths = InputWorkbooks.listSorted(temp);

        assertThat(paths).containsExactly(temp.resolve("a.xls"), temp.resolve("b.xls"));
    }

    @Test
    void shouldReturnEmptyList_whenDirectoryHasNoXls(@TempDir Path temp) {
        List<Path> paths = InputWorkbooks.listSorted(temp);

        assertThat(paths).isEmpty();
    }
}
