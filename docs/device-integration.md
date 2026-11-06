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
| INSECURE | Accept any certificate — disposable lab routers only |

Plain `http://` works but sends the password unencrypted; prefer HTTPS.

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
| EXECUTE | `POST /rest/execute` with the file text; output appears when the script ends; nothing is stored |
| UPLOAD_AND_RUN | Stored as `/system script ide-<name>` and run with `/system script run`; removed afterwards unless *keep* is set; *Stop* removes the job |
| IMPORT | Uploaded as a file and applied with `/import verbose=yes`; the file is deleted afterwards |

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

## Security notes

- Passwords are never written to project files or logs; error messages never include them.
- Redirects are not followed, so credentials are not sent to another host.
- The client uses the IDE's proxy settings.
