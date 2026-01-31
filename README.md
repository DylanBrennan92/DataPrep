# Spec Extractor

A lightweight Java CLI tool that transforms unstructured Excel (.xls) data into structured JSON records. 
Designed for converting legacy spreadsheet data into JSON format for database seeding, API integration,
or data migration purposes.

## Technology Stack

- Java 21 - Modern Java with records, sealed interfaces, and pattern matching
- Apache POI 5.4.0 - Excel file parsing and data extraction
- Jackson 2.17.0 - JSON serialization with pretty printing
- Lombok - Reduced boilerplate code
- SLF4J & Logback - Configurable logging with multiple log levels
- Maven - Build automation and dependency management

### Dependencies

* Describe any prerequisites, libraries, OS version, etc., needed before installing program.
* ex. Windows 10

### Building from source

git clone https://github.com/DylanBrennan92/spec-extractor.git
cd spec-extractor
mvn clean package

### CLI Arguments

| Argument | Required | Description | Example |
|----------|----------|-------------|---------|
| `recordType` | Yes | Record type to extract: `EMPLOYEE` or `VEHICLE` | `VEHICLE`, `EMPLOYEE` |
| `inputFile.xls` | Yes | Path to the Excel (.xls) file to process | `nissan.xls`, `staff.xls` |
| `outputFile.json` | Yes | Path where the JSON output should be saved | `output-nissan.json`, `employees.json` |
| `requiredCells` | Yes | Minimum number of filled cells for a row to be kept | `20`, `7` |
| `columnThreshold` | No | Minimum fill ratio (0.0–1.0) to keep a column; default `0.1` (10%) | `0.01`, `0.05` |

**Usage:**  
`java -jar spec-extractor.jar <recordType> <inputFile.xls> <outputFile.json> <requiredCells> [columnThreshold]`

## Example usage

```bash
# Vehicle specs (e.g. Nissan spreadsheet) — 20 required cells, 1% column threshold
java -jar target/spec-extractor-1.0-SNAPSHOT-jar-with-dependencies.jar VEHICLE src/main/resources/nissan.xls output-nissan.json 20 0.01

# Employee data — 7 required cells, default 10% column threshold
java -jar target/spec-extractor-1.0-SNAPSHOT-jar-with-dependencies.jar EMPLOYEE src/main/resources/staff.xls output.json 7

# With DEBUG logging
java -DLOG_LEVEL=DEBUG -jar target/spec-extractor-1.0-SNAPSHOT-jar-with-dependencies.jar VEHICLE src/main/resources/nissan.xls output-nissan.json 20 0.01
```

## Logging options

Set the `LOG_LEVEL` system property to control verbosity:

```bash
# INFO (default — progress and results)
java -jar target/spec-extractor-*.jar VEHICLE input.xls output.json 20

# DEBUG (detailed processing)
java -DLOG_LEVEL=DEBUG -jar target/spec-extractor-*.jar VEHICLE input.xls output.json 20 0.01

# TRACE (maximum verbosity for troubleshooting)
java -DLOG_LEVEL=TRACE -jar target/spec-extractor-*.jar VEHICLE input.xls output.json 20 0.01
```