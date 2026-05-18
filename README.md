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
- **Build Result**: Filter by SUCCESS, FAILURE, UNSTABLE, ABORTED, etc.
- **Parameter Name**: Search by parameter name
- **Parameter Value**: Search by parameter value
- **Global Search**: Search across all fields

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

## Requirements

- Jenkins 2.479.2 or higher
- Java 11 or higher

## License

MIT License - see LICENSE file for details

## Version

1.0.0
