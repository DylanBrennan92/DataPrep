# DataPrep Pipeline — Sequence Diagram

## How to import into draw.io

1. Open [draw.io](https://app.diagrams.net/) (or the desktop app)
2. Go to **Extras → Edit Diagram**
3. In the format dropdown at the top of the dialog, select **Mermaid**
4. Paste the Mermaid block below (everything inside the triple backticks) and click **OK**

The diagram will be converted into a fully editable draw.io diagram that you can restyle, export as SVG/PNG, or embed in Confluence/Notion.

---

## Mermaid source

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

    User->>Main: java -jar dataprep.jar 0.01

    Main->>CliParser: parseOrExit(args)
    CliParser-->>Main: Config(inputDir, outputDir, columnThreshold)

    Main->>DataPrepOrchestrator: run(config)

    Note over DataPrepOrchestrator: Loads permittedHeaders.csv (JP→EN mapping)<br/>Loads autoList.csv (brand name list)

    loop For each .xls in input dir
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
        WorkBookProcessor-->>WorkBookProcessor: findCarNameColumnIndex() — protect from threshold
        WorkBookProcessor-->>WorkBookProcessor: determineColumnsToKeep(threshold)
        WorkBookProcessor->>HeaderResolver: resolve(rawHeaderRows, columnsToKeep)
        Note over HeaderResolver: Scans each column bottom-to-top<br/>Normalises newlines<br/>Matches against permittedHeaders map<br/>Falls back to bottom-most non-empty value
        HeaderResolver-->>WorkBookProcessor: resolvedHeaders[]
        WorkBookProcessor-->>WorkBookProcessor: drop columns with empty resolved header
        WorkBookProcessor-->>WorkBookProcessor: resolveDuplicates() — fill-rate comparison
        WorkBookProcessor-->>WorkBookProcessor: fillDownGroupColumns() — Car Name & Common Name
    end

    WorkBookProcessor-->>DataPrepOrchestrator: WorkBookData (processed)

    DataPrepOrchestrator->>WorkBookWriter: write(workBookData, outputPath)
    Note over WorkBookWriter: Creates HSSFWorkbook<br/>Writes single header row per sheet<br/>Writes all data rows
    WorkBookWriter-->>DataPrepOrchestrator: output file written

    end
    DataPrepOrchestrator-->>Main: done
    Main-->>User: Exit 0
```
