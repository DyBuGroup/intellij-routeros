# Contributing

## Requirements

- JDK 21
- No system Gradle is needed; use the wrapper (`./gradlew`).

## Common tasks

| Task | Command |
|------|---------|
| Build and test | `./gradlew check` |
| Build the plugin ZIP | `./gradlew buildPlugin` → `build/distributions/` |
| Run in a sandbox IntelliJ IDEA | `./gradlew runIde` |
| Run in a locally installed PyCharm | `./gradlew runPyCharmLocal` (path from `localPyCharmPath` in `gradle.properties`, override with `-PlocalPyCharmPath=…`) |
| Verify compatibility | `./gradlew verifyPlugin` |

The build is tuned to stay light (`org.gradle.workers.max=2`, limited heap,
serial test execution). Stop the daemon with `./gradlew --stop` when done.

## Commit conventions

[Conventional Commits](https://www.conventionalcommits.org/):
`feat(lexer): …`, `fix(parser): …`, `docs: …`, `build: …`, `test: …`,
`chore(ci): …`. Trunk-based development on `main`.

## Project layout

See [docs/architecture.md](docs/architecture.md).
