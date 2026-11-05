# RouterOS Script for JetBrains IDEs

Language support for MikroTik RouterOS scripts and `/export` configuration
files (`.rsc`) in IntelliJ IDEA, PyCharm and every other JetBrains IDE based on
IntelliJ Platform 2025.1 or newer.

<!-- Plugin description -->
Language support for **MikroTik RouterOS** scripts and `/export` configuration
files (`.rsc`).

- Syntax highlighting for scripts and exports (RouterOS v7, v6 best-effort): menu paths,
  properties, values, script commands, variables, string escapes and interpolation
- Configurable colours (*Settings → Editor → Color Scheme → RouterOS Script*)
- Brace matching, `#` line comments (Ctrl+/), quote handling, TODO highlighting
- Works fully offline; no router required for editing features
<!-- Plugin description end -->

## Installation

Until the first Marketplace release, build the plugin from source
(see [CONTRIBUTING.md](CONTRIBUTING.md)) and install
`build/distributions/*.zip` via *Settings → Plugins → ⚙ → Install Plugin from
Disk…*.

## License

[MIT](LICENSE)
