# Changelog

All notable changes specific to RuoYi-Vue-X are documented in this file.

## Unreleased

### Documentation

- Added a project-specific overview, quick-start entry point, upstream attribution, and documentation index to the README.
- Added this changelog so future releases can distinguish RuoYi-Vue-X changes from upstream synchronization.

## 3.9.2-x

### Added

- Business-development conventions built around `Response<T>`, DTOs, VOs, parameter validation, and MapStruct conversion.
- File-ID-based upload and business-file lifecycle handling.
- Code-generator support for `SaveDTO`, `EditDTO`, `QueryDTO`, VOs, MapStruct mappings, validation, and file-field rendering.
- `traceId` support, enhanced operation logging, persisted exception logs, and administration-page visibility.
- Project usage and development-example manuals covering setup, common development work, deployment, and troubleshooting.

### Changed

- Updated the backend baseline to Spring Boot 3 and JDK 17.

### Fixed

- Fixed generated templates so dictionary imports are included when required.

## Upstream

RuoYi-Vue-X is derived from [RuoYi-Vue](https://github.com/yangzongzhuan/RuoYi-Vue). Upstream synchronization should be recorded separately from changes introduced by this repository.
