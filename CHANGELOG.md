# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).


## [1.0.2] - 2026-05-25

### Added
- **Job-level max records configuration**: Added per-job configuration option for maximum records
  - Created `BuildParameterHistoryJobProperty` class extending `JobProperty`
  - Added job configuration page (`config.jelly`) for UI
  - Each job can now have its own max records setting, independent of global configuration
  - When job setting is empty/null, falls back to global configuration value
  - Updated `BuildParameterHistoryService.getMaxRecords(Job)` to support job-level override
  - Updated `BuildParameterListener` to pass job-specific max records when saving/updating records
  - Added `BuildParameterHistoryAction` methods to expose job-level config info to UI
  - Added i18n support for job configuration UI elements
  - Display current effective max records and its source (Job setting/Global setting) on history page

### Changed
- `trimOldRecords()` now accepts maxRecords parameter instead of reading from global config
- Updated `saveRecord()` and `updateRecord()` overloads to accept maxRecords parameter

## [1.0.1] - 2026-05-20

### Fixed
- **选择计数显示异常**：修复在历史页面勾选多个条目时，选择栏始终显示"已选择 0 项"的问题
  - 根因：选择文本使用了硬编码的参数，无法根据实际选择动态更新
  - 解决方案：修改 `bphUpdateSelectBar()` JavaScript 函数，使用正则表达式动态更新选择数量
  - 支持中英文国际化显示
### Changed
- **权限调整**：删除记录操作现在需要 Jenkins 管理员权限
  - 将 `DELETE_RECORDS` 权限的父权限从 `CONFIGURE` 改为 `Jenkins.ADMINISTER`
  - 只有具有管理员权限的用户才能删除构建参数历史记录

## [1.0.1] - 2026-05-20

### Fixed
- **Record trimming not working**: Fixed critical bug where records were not being trimmed when exceeding the maximum limit
  - Root cause: `writeWithFileLock` method had incorrect resource closing order in try-with-resources
  - When `BufferedWriter` closed, it automatically closed the underlying `FileOutputStream`, causing `FileLock.release()` to fail with `ClosedChannelException`
  - Solution: Rewrote `writeWithFileLock` to manually manage resource closing in correct order
  - This fix ensures records are properly saved and old records are automatically removed when limit is exceeded
- **Global configuration not persisting**: Fixed issue where max records setting in system configuration was not being saved correctly
  - Root cause: `GlobalConfiguration` requires overriding `configure()` method for proper form data binding
  - Solution: Added `configure(StaplerRequest req, JSONObject formData)` method to properly handle form submission

### Added
- **Global configuration for max records**: Added system-wide configuration option in Jenkins System Settings
  - Created `BuildParameterHistoryGlobalConfiguration` class extending `GlobalConfiguration`
  - Added config Jelly page at `config.jelly` for UI
  - Users can now configure the default maximum number of records to keep per job
  - Configurable range: 1 - 10000 records (default: 200)
  - Added validation for input values
  - Updated `BuildParameterHistoryService.getMaxRecords()` to read from global configuration
  - Added localization for configuration UI elements in both English and Chinese

### Changed
- Maximum records per job is now configurable via Jenkins system settings instead of hardcoded value

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
