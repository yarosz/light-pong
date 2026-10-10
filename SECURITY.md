# Security

Please report vulnerabilities privately through GitHub: **Security → Report a vulnerability** on this
repository. Don't open a public issue for security problems.

Pong has no network code, no accounts and no telemetry. It declares no permissions and no
capabilities in `tool/lighttool.toml`, and `ToolMetadataTest` keeps it that way. Light's SDK, which
every Tool is built on, adds some Android permissions to the package's manifest; Pong uses none of
them. The only file Pong writes is `settings.txt` in its own storage, holding the Difficulty and the
sound choice.

Reports of Pong sending or receiving anything over a network, or reading anything outside its own
storage, are especially welcome.
