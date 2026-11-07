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

1. Move the *Unreleased* entries in `CHANGELOG.md` to a new version section.
2. Set `pluginVersion` in `gradle.properties` (`1.2.0`, or `1.2.0-beta` for the beta channel:
   the part after `-` up to the first `.` selects the Marketplace channel).
3. `./gradlew check verifyPlugin buildPlugin`, then a manual smoke test in
   `./gradlew runPyCharmLocal`: open an export, complete a property, run a script on a lab router.
4. Commit, tag `vX.Y.Z` and push the tag. `.github/workflows/release.yml` checks that the tag
   matches `pluginVersion`, signs and publishes the plugin and creates a GitHub release with the
   changelog section.

Repository secrets (environment `marketplace`): `CERTIFICATE_CHAIN`, `PRIVATE_KEY`,
`PRIVATE_KEY_PASSWORD` (see *Plugin Signing* in the IntelliJ Platform docs) and `PUBLISH_TOKEN`
(a JetBrains Marketplace token). The very first upload to the Marketplace must be done by hand
on plugins.jetbrains.com; `publishPlugin` works only for later versions. Publishing refuses to run
without signing credentials.
