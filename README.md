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
- Structure view with export sections, functions and globals; breadcrumbs; code folding
- RouterOS highlighting inside script strings (`/system script add source="…"`, scheduler
  `on-event`, netwatch/DHCP/PPP script properties)
- Completion for menu paths, commands, properties (`name=`), values (enums, yes/no,
  interface names from the file), `:` commands and variables in scope
- Quick documentation (Ctrl+Q) from the official CLI reference, parameter info (Ctrl+P)
- Go to declaration, find usages and rename for variables and functions (`:global` across files),
  Go to Symbol, gutter icons on functions
- Inspections with quick fixes: undefined/undeclared variables, unused locals, unknown properties
  (did you mean), wrong values, v6 syntax, missing mandatory properties, whitespace before `=`,
  invalid names, `{}` arrays, missing `do=`, unreachable code, misplaced comments
- Intentions: space ⇄ slash menu paths, quote/unquote values, wrap in `:do { } on-error={ }`
- Formatter (flat export style by default, optional section indent), code style settings,
  56 live templates, *New → RouterOS Script*, surround with, spell checking in comments/strings
- Works fully offline; no router required for editing features
<!-- Plugin description end -->

## Installation

Until the first Marketplace release, build the plugin from source
(see [CONTRIBUTING.md](CONTRIBUTING.md)) and install
`build/distributions/*.zip` via *Settings → Plugins → ⚙ → Install Plugin from
Disk…*.

## License

[MIT](LICENSE)
