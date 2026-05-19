# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.0] - 2026-05-19

### Fixed
- **Page menu and filter text blank**: Fixed issue where all UI text (menu items, filter labels, buttons) appeared blank in the build parameter history page
  - Root cause: JEXL expression parser in Jenkins Jelly templates cannot resolve full-qualified class method calls like `${com.siruoren.buildparameterhistory.Messages.xxx()}`
  - Solution: Migrated to Jenkins standard `${%key}` syntax for message resolution in Jelly templates
  - Created localized properties files for each Jelly template:
    - `index.properties` / `index_zh_CN.properties` for main history page
    - `filterResults.properties` / `filterResults_zh_CN.properties` for filter results page
  - Updated all message references in `index.jelly` and `filterResults.jelly` from `${com.siruoren.buildparameterhistory.Messages.xxx()}` to `${%xxx}`
  - Properties files now use dot-separated keys (e.g., `Filter.Records`) matching the `${%key}` syntax

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

### Changed
- All hardcoded English text in UI replaced with internationalization via Messages class method calls
- Jelly template now uses Jenkins standard `${%key}` syntax for i18n resolution, which correctly loads messages from properties files in the same directory

## [1.0.0] - 2026-05-18

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
