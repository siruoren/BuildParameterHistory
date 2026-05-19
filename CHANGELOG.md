# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.0-SNAPSHOT] - 2026-05-19

### Added
- **Unit tests**: Comprehensive test suite (57 tests) covering:
  - `BuildParameterRecordTest` (21 tests): Constructor, getters/setters, safe URL, duration, formatted time, ParameterEntry
  - `BuildParameterHistoryServiceTest` (36 tests): Record format/parse round-trip, filter logic (result/keyword/param name/value), file I/O, caching, max records config
- **Internationalization (i18n) support**: Full Chinese and English localization
  - Auto-detect browser language setting (`Accept-Language` header)
  - English: `Messages.properties`
  - Chinese: `Messages_zh_CN.properties`
  - All UI elements localized including:
    - Page title, filter labels, placeholders
    - Table headers, build result options (Success/Failure/Unstable/Aborted/Not Built)
    - Pagination buttons, action buttons, selection toolbar
    - Confirmation dialogs, empty state messages
- **Build result localization**: Filter dropdown and table display now show translated result text

### Fixed
- **Duplicate method issue**: Merged duplicate `getRecordsForJob()` methods by delegation pattern
- **Missing method closure**: Fixed syntax error in `getRecordsForJob(File, String)` method body
- **Code deduplication**: Refactored `filterRecords()` to delegate to `getFilteredRecordsForJob()`, reducing ~40 lines of duplicate code
- **i18n not rendering in browser**: Jelly `${%Key}` cross-package reference (`${%com.siruoren.buildparameterhistory.Messages.Key}`) does not work in Jenkins. Changed to direct Messages class method calls (`${com.siruoren.buildparameterhistory.Messages.Key_Name()}`) which correctly resolves locale at runtime
- **Chinese locale displaying blank content**: Converted `Messages_zh_CN.properties` from Unicode escape format to UTF-8 encoding to ensure proper rendering in Chinese browser environments

### Changed
- All hardcoded English text in UI replaced with internationalization via Messages class method calls
- Jelly template now uses generated `com.siruoren.buildparameterhistory.Messages` class for i18n resolution

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
