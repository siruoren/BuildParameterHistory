# Build Parameter History Plugin

A Jenkins plugin that records and displays build parameter history for Jenkins jobs.

## Features

- Automatically records build parameters for each build execution
- Displays parameter history in a clean, searchable table view
- Filter records by build result, parameter name, parameter value
- Global search across all fields
- Paginated display with configurable page size
- Download history file via API or UI button
- Automatic cleanup: keeps only the latest 200 records per job
- Supports all standard Jenkins build parameters
- **Internationalization**: Full support for Chinese and English based on browser language settings
- **Record selection & batch delete**: Select multiple records and delete them in batch
- **Clear history**: Option to clear all build parameter history for a job

## Installation

1. Build the plugin:
   ```bash
   mvn clean package
   ```

2. The `.hpi` file will be generated in the `target/` directory

3. Install via Jenkins web UI:
   - Go to **Manage Jenkins** → **Plugins** → **Advanced settings**
   - Upload the `.hpi` file or point to the generated file

## Usage

### View History

1. Navigate to any Jenkins job with build parameters
2. Click **"Build Parameter History"** in the left sidebar
3. View the history table with all recorded builds

### Filter Records

Use the filter panel to narrow down results:
- **Build Result**: Filter by Success, Failure, Unstable, Aborted, Not Built
- **Parameter Name**: Search by parameter name
- **Parameter Value**: Search by parameter value
- **Global Search**: Search across all fields

### Internationalization (i18n)

The plugin automatically adapts to your browser's language setting:

| Browser Language | Display Language |
|------------------|------------------|
| English (default) | English |
| Chinese (zh-CN) | 简体中文 |

No manual configuration needed - the plugin detects `Accept-Language` header and switches accordingly.

### Record Management

- **Batch Delete**: Select records using checkboxes, then click "Delete Selected"
- **Select All**: Use the checkbox in table header to select/deselect all records on current page
- **Clear All**: Click "Clear All History" button to remove all records for the job

### Download History

#### Via Web UI
Click the **"Download History"** button at the bottom of the history page.

#### Via API
Download the history file using curl:

```bash
curl -u username:api-token -o history.txt \
  "http://jenkins-url/job/your-job/buildParameterHistory/downloadHistory"
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/job/{job}/buildParameterHistory` | GET | View history page |
| `/job/{job}/buildParameterHistory/downloadHistory` | POST | Download history file |
| `/job/{job}/buildParameterHistory/filterResults` | POST | Filter records with criteria |
| `/job/{job}/buildParameterHistory/deleteRecords` | POST | Delete selected records |
| `/job/{job}/buildParameterHistory/clearHistory` | POST | Clear all history for job |

## Configuration

### Record Limit

By default, the plugin keeps only the **latest 200 records** per job. Older records are automatically removed when new builds are recorded.

To change this limit, modify the `MAX_RECORDS` constant in `BuildParameterHistoryService.java`.

### History File

Records are stored in each job's directory as a file named `param_history`.

## Building

```bash
# Build the plugin
mvn clean package

# Run tests
mvn test

# Skip tests
mvn package -DskipTests
```

## Testing

The project includes a comprehensive unit test suite with **57 test cases**:

### Test Classes

| Test Class | Tests | Coverage |
|------------|-------|----------|
| `BuildParameterRecordTest` | 21 | Model: constructors, getters/setters, safe URL, duration, formatted time, ParameterEntry |
| `BuildParameterHistoryServiceTest` | 36 | Service: format/parse round-trip, filter logic (result/keyword/param name/value), file I/O, cache expiry, max records config, singleton |

### Running Tests

```bash
# Run all tests
mvn test -Denforcer.skip=true

# Run a specific test class
mvn test -Denforcer.skip=true -Dtest=BuildParameterRecordTest

# Run with verbose output
mvn test -Denforcer.skip=true -X
```

## Requirements

- Jenkins 2.479.2 or higher
- Java 11 or higher

## License

MIT License - see LICENSE file for details

## Version

1.0.0
