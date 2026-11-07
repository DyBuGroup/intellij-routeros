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
| Verify compatibility | `./gradlew verifyPlugin` (locally: IC 2025.1 + the local PyCharm; with `CI` set: the recommended IDE set) |
| Refresh the bundled schema | see [docs/schema.md](docs/schema.md) |
| Refresh router highlight captures | `ROS_URL=https://… ROS_USER=… ROS_PASSWORD=… ./gradlew captureHighlightOracle` |
| Sweep private files | `./gradlew test -Pros.extraCorpus=/path/to/rsc/files` (reports in `build/reports/`) |

The build is tuned to stay light (`org.gradle.workers.max=2`, limited heap,
serial test execution). Stop the daemon with `./gradlew --stop` when done.

## Licence of contributions

The project is licensed under the [Apache License 2.0](LICENSE). By submitting a contribution you
agree that it is licensed under the same terms (section 5 of the licence).

## Commit conventions

[Conventional Commits](https://www.conventionalcommits.org/):
`feat(lexer): …`, `fix(parser): …`, `docs: …`, `build: …`, `test: …`,
`chore(ci): …`. Trunk-based development on `main`.

## Project layout

See [docs/architecture.md](docs/architecture.md).

## Releasing

1. Move the *Unreleased* entries in `CHANGELOG.md` to a section named exactly like the version.
2. Set `pluginVersion` in `gradle.properties`. A suffix selects the Marketplace channel:
   `1.1.0-beta` → beta channel (and a GitHub pre-release), `1.1.0` → stable.
3. Commit and push to `main`, then either
   - run **Actions → Release → Run workflow** on `main` (it creates the tag `v<pluginVersion>`
     on the built commit after publishing), or
   - push the tag yourself: `git tag v1.1.0-beta && git push origin v1.1.0-beta`.

   The workflow checks that tag and version match, runs all tests and the plugin verifier, signs
   the plugin, uploads it to the Marketplace and creates the GitHub release with the changelog.

## RouterOS schemas

`./gradlew :tools:schema-extractor:run --args="--channels"` regenerates the schemas of all release
channels (see [docs/schema.md](docs/schema.md)). The **Update RouterOS schemas** workflow does the
same every Monday and opens a pull request when something changed.
