# DataPrep

A lightweight Java CLI tool that pre-processes XLS files produced by Japan's Ministry of Land, Infrastructure, Transport and Tourism (MLIT) fuel economy database. It normalises messy multi-row headers, removes sparse columns, and writes a clean single-header-row XLS ready to be consumed by the [spec-extractor](https://github.com/DylanBrennan92/spec-extractor) application.

For **every** input workbook, DataPrep generates a new **`sourceArtifactId`** (UUID v4), copies the **original** ministry bytes to `local-data/artifacts/{uuid}.{extension}` **before** cleaning, then writes the cleaned `.xls` beside a **lineage sidecar**: `local-data/output/{same-name}.xls.source-artifact-id` (one line: that UUID). Use that UUID when you run [spec-extractor](https://github.com/DylanBrennan92/spec-extractor) with `--source-artifact-id` so JSON rows get matching `sourceArtifactId` (spec-extractor is documented separately).

## How It Works

The pipeline runs in four stages:

1. **Read** — `WorkBookReader` opens the XLS workbook, detects the multi-row header range per sheet (anchored to `車名`), and reads all data rows into memory.
2. **Process** — `WorkBookProcessor` drops columns below the fill threshold, resolves the multi-row headers into a single English label using `permittedHeaders.csv`, and removes or deduplicates any remaining duplicate columns using fill-rate comparison.
3. **Write** — `WorkBookWriter` writes the cleaned workbook (one header row + data rows per sheet) to the output path.
4. **Orchestrate** — `DataPrepOrchestrator` wires reader, processor, and writer. Per input file it generates a UUID, archives the original under `local-data/artifacts/`, runs read → process → write, then writes the `.source-artifact-id` sidecar next to the cleaned file. `Main` parses CLI via `CliParser` (`ConfigParser` + `ConfigValidator`) and runs the orchestrator.

### Pipeline Sequence Diagram

> Mermaid source and draw.io import instructions: [`src/main/resources/diagrams/dataprep-pipeline.md`](src/main/resources/diagrams/dataprep-pipeline.md)

```mermaid
sequenceDiagram
    actor User
    participant Main
    participant CliParser
    participant DataPrepOrchestrator
    participant WorkBookReader
    participant HeaderRangeDetector
    participant WorkBookProcessor
    participant HeaderResolver
    participant WorkBookWriter

    User->>Main: java -jar dataprep.jar [columnThreshold]
    Main->>CliParser: parseOrExit(args)
    CliParser-->>Main: Config(...)
    Main->>DataPrepOrchestrator: execute(config)
    Note over DataPrepOrchestrator: Loads permittedHeaders.csv (JP→EN mapping)<br/>Loads autoList.csv (brand name list)
    loop For each .xls in input dir
        Note over DataPrepOrchestrator: New UUID → copy original → artifacts/{uuid}.xls<br/>then write output + {name}.xls.source-artifact-id
        DataPrepOrchestrator->>WorkBookReader: read(inputFile)
        loop For each worksheet in workbook
            WorkBookReader->>HeaderRangeDetector: detect(sheet, brandNames)
            Note over HeaderRangeDetector: Anchors on 車名 row<br/>Scans back for pre-headers<br/>Scans forward for first brand row
            HeaderRangeDetector-->>WorkBookReader: HeaderRange(startRow, endRow)
            WorkBookReader-->>WorkBookReader: extractRawHeaderRows(headerRange)
            WorkBookReader-->>WorkBookReader: extractDataRows(dataStartRow)
        end
        WorkBookReader-->>DataPrepOrchestrator: WorkBookData
        DataPrepOrchestrator->>WorkBookProcessor: process(workBookData, columnThreshold)
        loop For each worksheet
            WorkBookProcessor-->>WorkBookProcessor: determineColumnsToKeep(threshold)
            WorkBookProcessor->>HeaderResolver: resolve(rawHeaderRows, columnsToKeep)
            Note over HeaderResolver: Scans bottom-to-top per column<br/>Normalises newlines, matches permittedHeaders<br/>Falls back to bottom-most non-empty value
            HeaderResolver-->>WorkBookProcessor: resolvedHeaders[]
            WorkBookProcessor-->>WorkBookProcessor: resolveDuplicates() — fill-rate comparison
            WorkBookProcessor-->>WorkBookProcessor: fillDownGroupColumns() — Car Name & Common Name
        end
        WorkBookProcessor-->>DataPrepOrchestrator: WorkBookData (processed)
        DataPrepOrchestrator->>WorkBookWriter: write(workBookData, outputPath)
        WorkBookWriter-->>DataPrepOrchestrator: output file written
    end
    DataPrepOrchestrator-->>Main: done
    Main-->>User: Exit 0
```

## Project Structure

```
DataPrep/
├── autoList.csv                   # Canonical Japanese car brand names
├── src/main/java/…/
│   ├── config/          # CliParser, ConfigParser, ConfigValidator, Config, constants, CSV loaders
│   ├── artifact/        # SourceArtifactPaths — artifact filename under local-data/artifacts/
│   ├── input/           # InputWorkbooks — single policy for listing .xls files
│   ├── model/           # Data models: WorkBookData, WorkSheetData, RowData, CarBrand
│   ├── orchestration/   # DataPrepOrchestrator — pipeline entry point
│   ├── processor/       # WorkBookProcessor, HeaderResolver
│   ├── reader/          # WorkBookReader, HeaderRangeDetector, HeaderRange
│   └── writer/          # WorkBookWriter
└── src/main/resources/
    ├── diagrams/
    │   └── dataprep-pipeline.md   # Mermaid sequence diagram + draw.io import guide
    ├── local-data/
    │   ├── input/                 # Place .xls input files here
    │   ├── output/                # Processed output files (same filenames)
    │   ├── artifacts/             # One original copy per workbook: {uuid}.{extension}
    │   └── permittedHeaders.csv   # Japanese → English header mapping
    └── logback.xml
```

## Technology Stack

| Library | Version | Purpose |
|---|---|---|
| Java | 21 | Records, pattern matching, text blocks |
| Apache POI | 5.4.0 | XLS parsing and writing |
| Lombok | 1.18.30 | `@Slf4j`, `@Data` — reduced boilerplate |
| SLF4J + Logback | 2.0.9 / 1.4.14 | Coloured console logging |
| Maven | — | Build and dependency management |

## Prerequisites

- Java 21+
- Maven 3.8+

## Building

```bash
git clone https://github.com/DylanBrennan92/DataPrep
cd DataPrep
mvn clean package
```

The runnable fat-JAR is produced at `target/dataprep-1.0-SNAPSHOT-jar-with-dependencies.jar`.

## Running

**Run from the DataPrep project root.** The application processes all `.xls` files in `src/main/resources/local-data/input/` and writes cleaned workbooks to `src/main/resources/local-data/output/` (same base filenames). For each file it **always** archives the untouched original under `artifacts/` with a **new** UUID and records that id in a sidecar file next to the cleaned `.xls`.

```bash
java -jar target/dataprep-1.0-SNAPSHOT-jar-with-dependencies.jar [columnThreshold]
```

CLI flags such as `--source-artifact-id` are **not** supported; ids are generated inside the app.

### Arguments

| Argument | Description |
|---|---|
| `columnThreshold` | Optional. Minimum data fill ratio `0.0–1.0` to keep a column. Default: `0.01` |

### Input/Output

| Location | Purpose |
|---|---|
| `src/main/resources/local-data/input/` | Place your `.xls` source files here (any count; each gets its own UUID and artifact copy) |
| `src/main/resources/local-data/output/` | Cleaned `.xls` files (same filenames as input) |
| `src/main/resources/local-data/output/*.xls.source-artifact-id` | One UTF-8 text file per output workbook: a single line containing the UUID for that file |
| `src/main/resources/local-data/artifacts/` | Original byte copy of each input — `{uuid}.{extension}` (extension matches the source file) |

### Column Threshold Guide

| Value | Effect |
|---|---|
| `0.01` | Keep any column with at least 1% fill — recommended for most inputs (default) |
| `0.05` | Keep columns with ≥ 5% fill — removes very sparse annotation columns |
| `0.10` | Keep columns with ≥ 10% fill — more aggressive; may drop sparse-but-valid columns like Car Name |

> The **Car Name** (`車名`) column is always kept regardless of the threshold.

### Examples

```bash
# Process all files in input/ with default threshold (0.01)
java -jar target/dataprep-1.0-SNAPSHOT-jar-with-dependencies.jar

# Process all files with 5% fill threshold
java -jar target/dataprep-1.0-SNAPSHOT-jar-with-dependencies.jar 0.05
```

After a run, read the UUID for a given cleaned file from `local-data/output/<filename>.xls.source-artifact-id` or from the INFO logs (`sourceArtifactId: ...`).

### Debug Logging

```bash
java -DLOG_LEVEL=DEBUG -jar target/dataprep-1.0-SNAPSHOT-jar-with-dependencies.jar 0.01
```

## Configuration Files

### `src/main/resources/local-data/permittedHeaders.csv`

Maps Japanese column headers from the source XLS to English output labels. Add a new row whenever a header label in the source file is not being resolved (a `WARN` log is emitted for unmatched labels).

```csv
japanese,english
車名,Car Name
通称名,Common Name
型式,Model Type
...
```

### `autoList.csv` (project root)

Contains the canonical list of Japanese car brand names (e.g. `トヨタ`, `ホンダ`). This is used by `HeaderRangeDetector` to identify where header rows end and data rows begin.

```csv
brand,english
トヨタ,Toyota
ホンダ,Honda
...
```
