# Pong privacy

This statement covers Pong 0.1.0 and later.

Pong collects nothing. It has no accounts and asks for no name, email or phone number.

- Pong never uses the network. It has no network code and no server.
- Pong declares no Android permissions and no capabilities (`tool/lighttool.toml`). Light's SDK, which
  every Tool is built on, adds some permissions to the package's manifest; Pong uses none of them.
- Pong keeps one file, `settings.txt`, in the Tool's own files directory. It holds three lines: the
  difficulty you chose last (Easy, Normal or Hard), whether sound is on, and the wheel setting (normal
  or fast). Nothing about your matches
  is saved: no scores, no history.
- A match in progress lives only in the phone's memory and ends when the phone closes Pong's process.
- Like other Android apps, Pong writes diagnostic lines to the phone's own system log: each match's
  random seed, screen changes, paddle hits, points, wheel spins and frame times. They name no person and stay on the phone.

Uninstalling Pong deletes `settings.txt`.

Nothing is sold, shared, or used for ads, analytics or tracking.

Questions: open an issue at https://github.com/yarosz/light-pong/issues.
