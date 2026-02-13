# DataPrep

A lightweight Java CLI tool that performs pre-processing on XLS files to match the desried output structure.
Normalizing messy data/columns mismatch in order to create structured JSON after this data is fed to the [spec-extractor](https://github.com/DylanBrennan92/spec-extractor)
application.

### Input
Raw .XLS files in original language

### Output
Pre-processed .XLS files in original language with required columns and fields only

## Technology Stack

- Java 21 - Modern Java with records, sealed interfaces, and pattern matching
- Apache POI 5.4.0 - Excel file parsing and data extraction
- Jackson 2.17.0 - JSON serialization with pretty printing
- Lombok - Reduced boilerplate code
- SLF4J & Logback - Configurable logging with multiple log levels
- Maven - Build automation and dependency management

### Dependencies

* Java 21
* Maven

### Building from source

git clone https://github.com/DylanBrennan92/DataPrep
cd DataPrep
mvn clean package

### CLI Arguments

| Argument          | Required | Description                                                        | Example |
|-------------------|----------|--------------------------------------------------------------------|---------|
| `inputFile.xls`   | Yes | Path to the Excel (.xls) file to process                           | `nissan.xls`, `staff.xls` |
| `outputFile.xls`  | Yes | Path where the .xls output should be saved                         | `output-nissan.json`|
| `columnThreshold` | No | Minimum fill ratio (0.0–1.0) to keep a column; default `0.1` (10%) | `0.01`, `0.05` |

**Usage:**  
`java -jar DataPrep.jar <recordType> <inputFile.xls> <outputFile.xls> <requiredCells> [columnThreshold]`

## Example usage

```bash
# Vehicle specs (e.g. Nissan spreadsheet) — 20 required cells, 1% column threshold

java -jar target/dataprep-1.0-SNAPSHOT-jar-with-dependencies.jar src/main/resources/nissan.xls output-nissan.json 0.01

# With DEBUG logging
java -DLOG_LEVEL=DEBUG -jar target/dataprep-1.0-SNAPSHOT-jar-with-dependencies.jar src/main/resources/nissan.xls output-nissan.json 0.01
```

## Logging options

Set the `LOG_LEVEL` system property to control verbosity:

```bash
# INFO (default — progress and results)
java -jar target/DataPrep-*.jar VEHICLE input.xls output.json 20

# DEBUG (detailed processing)
java -DLOG_LEVEL=DEBUG -jar target/DataPrep*.jar VEHICLE input.xls output.json 20 0.01

# TRACE (maximum verbosity for troubleshooting)
java -DLOG_LEVEL=TRACE -jar target/DataPrep*.jar VEHICLE input.xls output.json 20 0.01
```