# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.2-beta] - 2026-11-07

### Added

- Bundled command reference for MikroTik's stable, long-term and development channels
  (RouterOS 7.25, 7.24, 7.26); *Automatic* picks the one matching the connected router
- Monthly workflow that regenerates the schemas and opens a pull request
- Release workflow can be started manually; security policy

### Changed

- Licence changed from MIT to the Apache License 2.0

- WinBox terminal colours: commands purple, menu paths blue, property names green, values in the
  default text colour, in every scheme based on the bundled light or dark schemes (Islands, Dark,
  Light, High contrast…) and in Monokai schemes (using Monokai's own purple, blue and green)

## [1.0.1] - 2026-11-07

### Changed

- Menu paths and commands use the function-name colour; values, properties and variables each have
  their own colour
- MikroTik symbol as plugin and file icon, with light and dark variants
- Router passwords are stored per project
- The formatter no longer changes whitespace around `=` or inside menu paths

### Fixed

- Quick fixes and intentions inside scripts embedded in strings escape their text
- Path style conversion keeps positional arguments separate
- Variables used before their `:global` declaration are reported
- Faster variable resolution and unused-variable checks on large files
- Router messages are escaped in notifications; stricter checks of router responses
- Debugger: breakpoints inside `:local` functions, unreachable breakpoints marked, exit codes,
  validation before debugging, no instrumentation of persisted or background code

## [1.0.0] - 2026-11-06

### Added

- RouterOS Script language and `.rsc` file type with icon
- Context-sensitive lexer and syntax highlighting with a colour settings page
- Brace matcher, line commenter, quote handler and TODO highlighting in comments
- Parser with error recovery (one error per broken line), tested against a script corpus
- Folding, structure view and breadcrumbs
- Language injection into script-valued string properties
- Bundled RouterOS 7.26 schema generated from the official CLI reference
- Completion, quick documentation, parameter info and schema-aware annotations
- Variable/function references with cross-file globals, find usages, rename, Go to Symbol
- 13 inspections with quick fixes and 3 intentions
- Formatter and code style settings, live templates, file template, surround with, spell checking
- Router connections (password safe, TLS pinning), run configurations, log tool window,
  validate on router, open/compare export, schema sync with version banner
- Runs execute as background jobs so log tail and Stop stay responsive
- Experimental pseudo-debugger behind an advanced setting

[Unreleased]: https://github.com/DyBuGroup/intellij-routeros/compare/v1.0.2-beta...HEAD
[1.0.2-beta]: https://github.com/DyBuGroup/intellij-routeros/compare/v1.0.1...v1.0.2-beta
[1.0.1]: https://github.com/DyBuGroup/intellij-routeros/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/DyBuGroup/intellij-routeros/commits/v1.0.0
