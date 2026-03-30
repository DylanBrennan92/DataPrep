package com.originspecs.dataprep.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Policy for which files under an input directory count as ministry workbooks — exactly one definition for the whole app.
 */
public final class InputWorkbooks {

    private static final String XLS_SUFFIX = ".xls";

    private InputWorkbooks() {}

    /**
     * All {@code .xls} files directly under {@code inputDir}, sorted by path string.
     *
     * @throws IllegalStateException if the directory cannot be read (wraps {@link IOException})
     */
    public static List<Path> listSorted(Path inputDir) {
        try (Stream<Path> stream = Files.list(inputDir)) {
            return stream
                    .filter(p -> p.toString().toLowerCase().endsWith(XLS_SUFFIX))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list input directory: " + inputDir.toAbsolutePath(), e);
        }
    }
}
