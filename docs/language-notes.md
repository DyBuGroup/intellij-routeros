# RouterOS language notes

Facts about RouterOS script syntax the plugin relies on. "Verified" means checked against
a RouterOS 7.25 router via `/console/inspect request=highlight`.

## Lexical

- Statements end at a newline (LF, CRLF or CR) or `;`.
- `\` immediately before the end of line continues the line.
- Comments start with `#` at the start of a statement or after `;` and run to the end of
  the line. **Verified:** `set name=x # comment` (a `#` directly after a value without `;`)
  is an error on the router. The lexer still produces a comment token; an inspection flags it.
- Strings use double quotes only. Valid escapes: `\" \\ \n \r \t \$ \_ \a \b \f \v`, `\XX`
  with uppercase hex. **Verified:** `\4a` (lowercase hex) is an error.
- Interpolation inside strings: `$name`, `$( expr )`, `$[ command ]`.
- Variable names consist of letters and digits; other names are quoted: `$"my-var"`.

## Structure

- `{ … }` is a block or an array literal depending on position; the parser decides.
- `[ … ]` is command substitution; `( … )` is an expression.
- In export files, a bare menu path (`/ip address`) sets the menu for following commands.
