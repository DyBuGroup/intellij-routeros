# Architecture

The plugin is a native IntelliJ Platform language plugin written in Kotlin. The lexer
(JFlex) and parser (Grammar-Kit) are generated at build time from `src/main/grammar/` into
`build/generated/sources/`; generated code is never committed.

| Package (`dev.dybug.routeros`) | Responsibility |
|--------------------------------|----------------|
| *(root)* | `RosLanguage`, `RosFileType`, `RosIcons`, `RosBundle` |
| `lexer` | `RosLexerAdapter` around the generated `_RouterOsLexer` |
| `parser` | `RosParserDefinition`, parser utilities |
| `psi` | Token/element types, `RosFile`, token sets |
| `highlighting` | Syntax highlighter, colour settings page, attribute keys |
| `editor` | Brace matcher, commenter, quote handler, TODO support |

All user-visible strings live in `src/main/resources/messages/RosBundle.properties`.

## Lexer

RouterOS cannot be lexed with one flat token set (`/` is a path separator, a division
operator or part of `192.168.0.0/24` depending on context), so the lexer uses JFlex states
plus a stack for nested `( ) [ ] { }` and strings.

| State | Entered | Notes |
|-------|---------|-------|
| `YYINITIAL` | start of a statement (after newline, `;`, `{`, `[`) | `:cmd` → script command; `/` → path; anything else → `PATH` |
| `PATH` | after `/` or a leading word | `PATH_WORD` and `SLASH`; a word followed by `=` becomes `ARG_NAME` and switches to `ARGS`; numeric words, item ids (`*1A`) and `!flag` switch to `ARGS` |
| `ARGS` / `SARGS` | arguments of menu commands / of `:` script commands | `ARG_NAME`, `WORD`, variables; `SARGS` additionally marks `from= to= in=…` as `CONTROL_ARG` |
| `VALUE` / `SVALUE` | right after `=` | the value word may contain `=` and `/` (`url=https://x/?a=1`) |
| `WHERE` | after the `where` keyword | comparison and logical operators, bare property names |
| `EXPR` | inside `( … )` | typed literals (numbers, IPs, times, MACs, IPv6), operators; newlines are whitespace |
| `STRING` | inside `"…"` | escapes, `$var`, `$( … )` and `$[ … ]` interpolation |
| `ESC_STRING` | `\"…\"` inside an interpolation | |

Argument values are lexed as generic words; their type (IP, time, enum…) is checked later
against the schema, never in the lexer.

`RosLexerAdapter` reports state `0` only for tokens that start at top level with an empty
stack, so the editor restarts incremental lexing only at such points. Other tokens report a
fingerprint of the state and stack.

## Testing

- `src/test/testData/lexer/*.rsc` + `.txt` — golden token dumps (`RosLexerTest`).
- `RosLexerRobustnessTest` — CRLF/CR, restartability, no bad characters on test data,
  termination on unterminated constructs. `-Pros.extraCorpus=/dir` sweeps private files.
- `src/test/testData/oracle/*.json` — RouterOS's own highlighter output for the lexer test
  files. `RosHighlightOracleTest` compares coarse token classes and fails only when a file's
  mismatch count grows beyond its recorded baseline. Refresh with
  `ROS_URL=https://router ROS_USER=… ROS_PASSWORD=… ./gradlew captureHighlightOracle`
  (plain HTTP only with `ROS_ALLOW_HTTP=true`, for disposable lab routers).
