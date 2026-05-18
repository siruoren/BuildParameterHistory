# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.0-SNAPSHOT] - 2026-05-18

### Added
- Automatic recording of build parameters for each build execution
- Web UI displaying build parameter history in a paginated table
- Filter records by build result (SUCCESS, FAILURE, UNSTABLE, ABORTED, NOT_BUILT)
- Filter records by parameter name and parameter value
- Global search across all fields (job name, build ID, result, time, parameters)
- Download history file feature via API endpoint
- Download button on history page

### Changed
- Build parameter history is automatically limited to latest 200 records per job
- Older records are automatically removed when limit is exceeded

### Removed
- Clear All History button removed from UI (no longer needed due to auto-cleanup)

## [0.0.1-SNAPSHOT] - Initial Release

### Features
- Record build parameters including job name, build ID, start/end time, duration, result
- Store parameter name-value pairs
- Display history in a user-friendly table view
