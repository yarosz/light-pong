# Contributing

Issues and pull requests are welcome.

## Before you start

- Read `AGENTS.md` (the layout and how changes land), `CONTEXT.md` (the vocabulary), `DESIGN.md`
  (the spec) and the ADRs in `docs/adr/`. A proposal that reverses an ADR or a ruling in
  `docs/design/decision-log.md` should say what changed since.
- For anything larger than a fix, open an issue first so we can agree on the shape.

## Platform rules

Light's build plugin enforces these, and Light builds every release from source:

- Kotlin only, Compose UI, inside the SDK's `LightScreen` and `LightViewModel`.
- Only allowlisted dependencies and permissions (see `light-sdk/plugin/`). No reflection, no native
  code.

## What we won't accept

- Anything that needs a permission, the network, an account or tracking. Pong declares none and uses
  none.
- Android imports in `core/` or `screen/`. The game stays pure and deterministic (ADR 0002).
- An exponential or uncapped wheel curve. The step rises linearly with the Detent rate and caps at
  80 px (ADR 0001's amendment, decision log D31); tune it from the `spin` log lines.

## Pull requests

- Keep them small and focused. The title becomes the merge commit's subject.
- `build` (GitHub Actions) must be green. Maintainers run the local checks (`mise run ci`, on the
  emulator) before merging. For a pull request from a fork, a maintainer pushes your branch to this
  repository to run them, since the checks attest to commits on this repository.
- New or changed terms go in `CONTEXT.md`, and changed behaviour goes in `DESIGN.md` in the same PR.
  Hard-to-reverse decisions get an ADR.

By contributing, you agree your work is released under the MIT licence.
