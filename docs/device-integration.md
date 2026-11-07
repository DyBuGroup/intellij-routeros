# Device integration

All router features use the RouterOS 7 REST API (`/rest/…` on the `www-ssl` or `www`
service) with HTTP Basic authentication. Nothing is sent to a router unless you run an action.

## Connections

*Settings | Languages & Frameworks | RouterOS* holds connection profiles (stored in
`.idea/routeros.xml`); passwords go to the IDE password safe. *Test Connection* reads
`/system/resource` and remembers the RouterOS version.

TLS modes:

| Mode | Use |
|------|-----|
| VERIFY | Normal certificate and host name validation |
| PINNED | Trust exactly one certificate by SHA-256 (*Fetch Certificate…* shows it; compare with `/certificate print detail`). Typical for self-signed router certificates |
| INSECURE | Accept any certificate and allow plain `http://` — disposable lab routers only |

Plain `http://` sends the password unencrypted, so it is accepted only in INSECURE mode.

Proxy: *Automatic* (default) connects directly to routers on the local network — private,
loopback and link-local addresses, `.local`/`.lan`/`.home.arpa` names and single-label host
names — and uses the IDE's HTTP proxy settings for anything else. *IDE proxy settings* always uses
the proxy, *No proxy* never does. An `HTTP 503` page from Squid or another proxy when running a
script means the request went through a proxy that cannot reach the router.

Create a dedicated user. Validation and schema sync need `read,test,api,rest-api`; running
scripts also needs `write` plus whatever policies the scripts themselves use:

```
/user group add name=ide policy=read,write,test,api,rest-api
/user add name=ide group=ide password=…
```

## Run configuration

*Run | Edit Configurations | + | RouterOS Script*, or the gutter icon / context menu on a
`.rsc` file. Modes:

| Mode | What happens |
|------|--------------|
| AUTO | Export-style files (starting with a menu path such as `/ip address`) are imported, other files executed |
| EXECUTE | Stored as a temporary `/system script ide-run-<random>`, run in the background and removed afterwards (output appears when the script ends). If the IDE dies mid-run, the temporary script and `ide-run-*-out.txt` can remain on the router; delete them by hand |
| UPLOAD_AND_RUN | Stored as `/system script ide-<name>` and run with `/system script run`; removed afterwards unless *keep* is set; *Stop* removes the job |
| IMPORT | Uploaded as a file and applied with `/import verbose=yes`; the file is deleted afterwards |

Every mode runs the script as a background job (`:execute … file=`) and collects its output from
a router file afterwards. RouterOS serves one REST request at a time per user while a blocking
`/execute` runs, so a background job is what keeps log polling and *Stop* responsive.

With *Validate before running*, scripts are checked with `/console/inspect request=highlight`
and export-style files with `/import … dry-run` first; nothing runs if the router's parser
reports a problem. Errors such as `bad command name foo (line 2 column 1)` are links to the
script. With *Show router log*, new `/log` entries are printed while the run is active.

## Other actions (Tools | RouterOS, editor context menu)

- **Validate on Router** — marks the lines the router's parser rejects.
- **Open Router Export** — `/export` of the default connection in a new editor.
- **Compare with Router Export** — diff between the current file and `/export`.
- **Sync Schema from Router** — reads the command tree with `/console/inspect request=child`
  (one request per menu plus one per menu-specific command) and stores it in
  `.idea/routeros-schema/<version>.json.gz`. It is merged over the bundled schema: menus from
  extra packages and newer arguments become known; documented types and descriptions are kept.
  When the router's version differs from the schema's, RouterOS files show a banner offering a sync.
- **RouterOS Log** tool window — follows `/log` of the default connection with a text filter.

## Experimental debugger

RouterOS has no debug protocol. With *Settings | Advanced Settings | RouterOS | Experimental
RouterOS script debugger* enabled, *Debug* on a RouterOS run configuration:

1. rewrites the script: before every statement that starts a line it inserts, on the same line,
   a pause point that checks the breakpoint list and step mode held in session globals
   (`RosDbg<token>…`), stores the visible variables with `:serialize to=json`, and waits;
2. runs the rewritten copy as a background job;
3. polls `/system/script/environment` (about 4× per second) and shows the pause as a stack frame
   with the variables; Resume/Step set globals over REST; Stop removes the job.

Line numbers are unchanged, so errors still point at your lines. Limitations: the script that runs
is not byte-for-byte yours (timing changes, one extra global per session), only statements that
start a line can pause, arrays and scripts inside strings are not instrumented, there is no
expression evaluation, and *Step Into/Out* behave like *Step Over*.

## Security notes

- Passwords are never written to project files or logs; error messages never include them.
- Redirects are not followed, so credentials are not sent to another host.
- Responses are limited to 32 MB; synced schemas are size-limited and stored only inside
  `.idea/routeros-schema` (symlinks are refused).
- Script and file names created on the router are generated (`ide-run-<random>`), never taken
  from user text.
- The client uses the IDE's proxy settings.
