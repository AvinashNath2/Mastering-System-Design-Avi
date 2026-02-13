# Email Excel Parser

A Java application that fetches a JSON array of email addresses from a URL and creates an Excel file with each email in a separate cell.

## Features

- Fetches JSON data from a remote URL
- Parses JSON array of email addresses
- Creates an Excel file (.xlsx) with formatted output
- Includes header row with styling
- Auto-sizes column width

## Requirements

- Java 11 or higher
- Maven 3.6 or higher

## Setup

1. Navigate to the project directory:
   ```bash
   cd EmailExcelParser
   ```

2. Compile the project:
   ```bash
   mvn compile
   ```

## Running the Application

Run the application using Maven:

```bash
mvn exec:java -Dexec.mainClass="com.example.EmailExcelParser"
```

Or compile and run manually:

```bash
mvn compile
java -cp target/classes:$(mvn dependency:build-classpath -q -DincludeScope=compile) com.example.EmailExcelParser
```

## Output

The application will create an `emails.xlsx` file in the project root directory containing all email addresses from the JSON array, with each email in a separate cell in column A.

## Dependencies

- **Jackson** (v2.15.2): For JSON parsing
- **Apache POI** (v5.2.4): For Excel file creation
