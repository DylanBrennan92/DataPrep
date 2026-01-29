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
| `inputFile.xls` | Yes | Path to the Excel (.xls) file to process | `staff.xls`, `data/employees.xls` |
| `outputFile.json` | Yes | Path where the JSON output should be saved | `output.json`, `results/employees.json` |

## Example usage

```
java -jar target/spec-extractor-1.0-SNAPSHOT-jar-with-dependencies.jar src/main/resources/staff.xls output.json
```

## Logging Options
```
# INFO level (default - shows progress and results)
java -jar target/spec-extractor-*.jar input.xls output.json

# DEBUG level (detailed processing information)
java -DLOG_LEVEL=DEBUG -jar target/spec-extractor-*.jar input.xls output.json

# TRACE level (maximum verbosity for troubleshooting)
java -DLOG_LEVEL=TRACE -jar target/spec-extractor-*.jar input.xls output.json
```