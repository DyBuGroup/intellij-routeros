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
| `structure` | Folding, structure view, breadcrumbs |
| `schema` | Schema model, loader, application service, value types, PSI ↔ schema context |
| `completion` | Completion contributor, parameter info |
| `documentation` | Quick documentation provider |
| `references` | Variable references, `:set` targets, global index, find usages, rename, Go to Symbol |
| `inspections` | Local inspections and quick fixes |
| `intentions` | Intention actions |
| `injection` | RouterOS injection into script-valued strings, string escaper |

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

## Parser and PSI

`src/main/grammar/RouterOs.bnf` is deliberately permissive: a statement is a script command
(`:local`, `:global`, `:set`, other `:cmd`), a function call (`$f …`) or a command
(`path argument*`, or a bare argument list such as an array element). `{ … }` is always a
`BLOCK`; whether it is an array literal is decided semantically, not by the parser.
Statements recover at newline, `;` and closing brackets, so one broken line yields one error.
Unmatched closing brackets become `STRAY_TOKEN` nodes.

`RosPsiUtil` holds the semantic helpers: argument access, declared names, and menu context.
A bare absolute path at top level (`/ip address`) is a *menu context line*; relative commands
after it resolve against it (`RosPsiUtil.resolvedPath`). Context lines are cached per file.

Strings are `PsiLanguageInjectionHost`s. `RosScriptInjector` injects RouterOS into
script-valued properties when the string has no interpolation; `RosStringEscaper` decodes
escapes so the injected fragment is what the router would store.

## References and scopes

`RosScopes.visibleAt` implements the scoping rules: `:local` after its declaration in the
enclosing block and nested blocks; loop variables (`:foreach k,v`, `:for i`, `:onerror e`)
inside the command's blocks; `:global` anywhere in the file. `$name` (`RosVariableReference`)
and `:set name` (`RosSetTargetReference`) resolve locally first, then to `:global`
declarations in other files via `RosGlobalIndex` (a file-based index of global names).
References are soft: inspections report unresolved names as weak warnings, because hook scripts
and callers can provide variables at runtime.

## Testing

- `src/test/testData/lexer/*.rsc` + `.txt` — golden token dumps (`RosLexerTest`).
- `RosLexerRobustnessTest` — CRLF/CR, restartability, no bad characters on test data,
  termination on unterminated constructs. `-Pros.extraCorpus=/dir` sweeps private files.
- `src/test/testData/parser/` — golden PSI trees (`RosParserTest`).
- `src/test/testData/corpus/` — must parse without errors (`RosCorpusParsingTest`);
  `allow-errors.txt` lists intentionally broken files. `tikoci/` is MIT-licensed test data from
  tikoci/lsp-routeros-ts (see `LICENSE-tikoci`). Use `-Pros.extraCorpus=/dir` to sweep
  non-redistributable files; results go to `build/reports/extra-corpus*.txt`.
- `RosPerformanceTest` — a 1 MB synthetic export must parse within the budget.
- `src/test/testData/oracle/*.json` — RouterOS's own highlighter output for the lexer test
  files. `RosHighlightOracleTest` compares coarse token classes and fails only when a file's
  mismatch count grows beyond its recorded baseline. Refresh with
  `ROS_URL=https://router ROS_USER=… ROS_PASSWORD=… ./gradlew captureHighlightOracle`
  (plain HTTP only with `ROS_ALLOW_HTTP=true`, for disposable lab routers).
