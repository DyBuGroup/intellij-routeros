# Security policy

## Supported versions

Only the latest release (including beta releases) receives security fixes.

## Reporting a vulnerability

Please **do not open a public issue**. Report privately, either:

- through GitHub: *Security → Report a vulnerability* on
  <https://github.com/DyBuGroup/intellij-routeros/security/advisories/new>, or
- by e-mail to <maheshmatkhah.prg@gmail.com> with the subject `[intellij-routeros security]`.

Include the plugin and IDE versions, what an attacker can achieve, and the steps or a sample file to
reproduce it. Do not include real router passwords or configurations.

You will get an answer within 7 days. Once a fix is released, the advisory is published with credit
to the reporter unless you prefer otherwise.

## Scope

In scope, for example:

- leaking router credentials (password safe, logs, error messages, project files);
- TLS validation problems (certificate pinning, the INSECURE mode boundary, redirects);
- commands sent to a router that differ from what the user asked for, or names/text from files
  injected into router commands;
- crashes, freezes or runaway memory/CPU caused by a crafted `.rsc` file, synced schema or router
  response;
- problems in the release or CI workflows (secrets, tag handling, supply chain).

Out of scope: vulnerabilities in RouterOS itself (report them to MikroTik), and the behaviour of a
script you deliberately run on your own router.

## How the plugin handles secrets

See [docs/device-integration.md](docs/device-integration.md#security-notes): passwords are kept in
the IDE password safe per project, never in project files or logs; plain HTTP is accepted only in
the explicit INSECURE mode; router responses are size-limited.
