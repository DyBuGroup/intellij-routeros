# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- RouterOS Script language and `.rsc` file type with icon
- Context-sensitive lexer and syntax highlighting with a colour settings page
- Brace matcher, line commenter, quote handler and TODO highlighting in comments
- Parser with error recovery (one error per broken line), tested against a script corpus
- Folding, structure view and breadcrumbs
- Language injection into script-valued string properties
- Bundled RouterOS 7.26 schema generated from the official CLI reference
- Completion, quick documentation, parameter info and schema-aware annotations
