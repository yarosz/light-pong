# Releasing

Light builds and signs community Tools from a public commit (see
[light-sdk/builder](https://github.com/lightphone/light-sdk/tree/main/builder)). A Pong release is a
signed tag on a merge commit on `main`. The GitHub Release carries notes only, no APK.

The maintainer drives every outward step: the merge, the tag, the release, any post, and anything sent
to Light. Agents prepare, check and draft.

## Versioning

- `tool/lighttool.toml` carries both numbers. Bump them together in the release PR, with the README's
  status line.
  - `versionName`: semver `X.Y.Z`, no pre-release suffix (Light rejects them).
  - `versionCode`: +1 every release, never reused, never lower. Light's build server rejects a
    `versionCode` that is not greater than the last published one, and Android refuses to install a
    lower one over a higher one. 0.1.0 is `versionCode = 1`.
- The Tool id `com.yarosz.pong` is permanent from the first published build. Never change it.
- Tag the merge commit `vX.Y.Z`, signed.
- Release notes live in `docs/release-notes/X.Y.Z.md`. The first three lines are the changelog for
  Light's dashboard, in the listing's voice. The rest is for developers.
- Bump the `light-sdk` submodule to Light's newest SDK tag before the release, in its own PR, so the
  release is built against the SDK Light builds with.

## Why we never publish our own APKs

Local builds are signed with the SDK's shared development key, which is public. Anyone could sign an
"update" with it that Android would accept over ours. Only Light-signed builds are distributed; until
then, people who sideload build Pong themselves (README, "Install").

Anyone who has a dev-signed build installed must uninstall it before installing a Light-signed one,
because the signatures differ. Uninstalling deletes `settings.txt`, which holds only the Difficulty
and the sound choice.

## Rollback

Light rejects a lower `versionCode`, so there is no going back. A rollback is a new release, with a
higher `versionCode`, carrying the old code. `settings.txt` ignores lines it does not know, and a
value it does not know loads the defaults, so an older build reads a newer file without crashing.

## Checklist

### Before the release PR

- [ ] SDK check: run the lp3-new-tool skill's `scripts/check-tool.sh <repo>` with `gh` available. If
      `light-sdk` is not at Light's newest tag, bump it in its own PR first.
- [ ] Every item in `LEDGER.md` that blocks the release is done.

### The release PR

The PR bumps the version and adds the notes. Run every check through the repo's toolchain
(`mise exec -- ...`).

- [ ] `build` (GitHub Actions) and `signoff/emulator` green on the PR head, as for every PR.
- [ ] Assert the version with a script, not by eye. `<newest>` is the highest `versionCode` published
      so far (0 before the first release):

      ```sh
      grep -Eq '^versionName = "[0-9]+\.[0-9]+\.[0-9]+"$' tool/lighttool.toml
      code=$(sed -nE 's/^versionCode = ([0-9]+)$/\1/p' tool/lighttool.toml); [ "$code" -gt <newest> ]
      ```

- [ ] Minified release runs on the emulator:
      `scripts/emulator-build.sh ./gradlew :tool:assembleRelease`, install it, play a Point, pause,
      resume, and toggle sound on the title screen.
- [ ] Upgrade path (from the second release on): install the previous release, set Hard and sound
      off, install this build over it with `adb install -r`, and check that the title screen still
      shows Hard selected and "Sound off".
- [ ] `mise run light-build` on the committed release HEAD: Light's extractor on a clean clone, then
      an offline, unsigned, minified release against the pinned SDK. Uncommitted work is invisible to
      it.
- [ ] Release notes in `docs/release-notes/X.Y.Z.md`.
- [ ] Public-content check: nothing personal in the diff, the notes or the PR comments.
- [ ] While the repo is public, run the prepublish scan before every push (the lp3-prepublish
      skill). Keep the private patterns in a file outside the repo, never inline in a command:

      ```sh
      PREPUBLISH_PRIVATE_PATTERNS=<patterns-file> <lp3-prepublish>/scripts/prepublish-scan.sh .
      ```

      Read every hit. Exit 0 prints `clean`. Exit 64 means the scan could not search; fix it and rerun.
      Before the repo first goes public, follow the whole lp3-prepublish skill, including the scan of
      a fresh clone and two independent privacy reviews.

### Merge and tag

The PR branch must contain the current `main` before it merges, so the merge commit has the tree that
CI tested.

- [ ] Merge with a merge commit, pinned to the tested head:

      ```sh
      gh pr merge <n> --merge --match-head-commit <tested-sha>
      m=$(gh pr view <n> --json mergeCommit --jq .mergeCommit.oid)
      git fetch origin
      git rev-parse "$m^{tree}"             # must equal the next line
      git rev-parse "<tested-sha>^{tree}"
      ```

      If the two trees differ, stop: CI did not test what merged.
- [ ] Tag the merge commit, not whatever `origin/main` points at by then:

      ```sh
      git tag -s vX.Y.Z "$m" -m vX.Y.Z
      git push origin vX.Y.Z
      ```

- [ ] Create the GitHub Release on the existing tag only:

      ```sh
      gh release create vX.Y.Z --verify-tag --latest \
        --notes-file docs/release-notes/X.Y.Z.md
      ```

      Anything the README or a post links to (a screenshot) must exist at the tag.
- [ ] Release before any public post. A post with no release behind it leaves readers with no build.
- [ ] Update this file whenever the flow changes.

### Light's Tool Library

These follow "Submitting Your Tool" in
[Light's SDK README](https://github.com/lightphone/light-sdk#submitting-your-tool); Light may change
them. Under Light's AI policy, everything sent to Light comes from the maintainer personally, never
from an agent.

- [ ] Review the release against Light's
      [TOOL_GUIDELINES.md](https://github.com/lightphone/light-sdk/blob/main/TOOL_GUIDELINES.md).
- [ ] First submission only: on [Light's dashboard](https://dashboard.thelightphone.com/), register
      the Tool with a title, the GitHub repo, a one-line description and one tag. Light reads the
      package name from `tool/lighttool.toml` on the default branch, and it can never change. The
      description is limited to 255 characters; count it before saving.
- [ ] Images: one to three, at least one. The README's screenshots work; look at each for personal
      data first.
- [ ] "Submit build": the tag's full commit SHA (`git rev-parse vX.Y.Z^{commit}`) and the three
      changelog lines. The `versionCode` must be higher than the last Version listed.
- [ ] Wait for the build (6 to 8 minutes so far). "Pending approval" is Light's review queue, not a
      result.
- [ ] When Light's signed build is ready: download it, check the package id, `versionName` and
      `versionCode` with `aapt dump badging <apk>`, install it on a Light Phone III (after uninstalling
      any dev-signed build), play a Point, and only then share it.
