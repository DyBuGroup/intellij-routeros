# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

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

[Unreleased]: https://github.com/dybug/routeros-intellij/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/dybug/routeros-intellij/commits/v1.0.0
