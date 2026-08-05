## Why

The snapshot job has never succeeded. It publishes without importing a GPG key, on the theory that a
`required` predicate matching only the release task would keep signing out of the snapshot task
graph. It does not: Gradle's `Sign` task runs when `isRequired() || getSignatory() != null`, and
`useGpgCmd()` installs a signatory unconditionally, so `signMavenPublication` executes on the
keyless runner and dies with `gpg: No secret key`.

Rather than suppress signing on snapshots, sign them the same way releases are signed. Snapshot
publishing then rehearses release publishing on every merge to `main`, so a bad key, passphrase or
fingerprint surfaces on a reversible snapshot instead of at the irreversible Central release.

## What Changes

- The `snapshot` job imports the GPG key with `crazy-max/ghaction-import-gpg@v7`, using the same
  three secrets as the `publish` job, making the two publishing paths mechanically identical.
- The `required` predicate is removed from the `signing` block in the convention plugin. Signing
  reverts to Gradle's default of always required. The predicate never changed an outcome in any
  task graph — it could not, for the reason above — and keeping it would assert a boundary that no
  longer exists.
- Snapshots are published with `.asc` signatures. Central does not require signatures on snapshots
  but accepts them.

Not a breaking change for consumers: published coordinates and artifact contents are unchanged, with
signatures added alongside.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `build-foundation`: two requirements change.
  - **Snapshot publishing**: drops the clause stating snapshot publishing SHALL NOT require a GPG
    key. Everything else about when snapshots publish is unchanged.
  - **Signing boundary** → renamed to **Publish signing**: there is no longer a boundary. Both
    publishing jobs import the same key and sign through the same mechanism, and the requirement
    states that sameness and why it is wanted. The scenario asserting that snapshot publishing
    succeeds with no GPG key available is removed — it describes behaviour that never worked and is
    no longer wanted.

## Impact

- `.github/workflows/release.yml` — one step added to the `snapshot` job.
- `buildSrc/src/main/groovy/conventions.gradle` — `required { ... }` removed from `signing`.
- No change to `settings.gradle`, `buildSrc/build.gradle`, the processor, or the release job.
- Secrets: `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE` and `GPG_FINGERPRINT` become readable by a job that
  runs on every push to `main`, not only on releases. This widens the key's exposure from tagged
  releases to all default-branch merges, and is the accepted cost of exercising the signing path
  continuously.
- Nothing anywhere prevents a keyless local `publishToMavenLocal` from invoking gpg, and nothing
  ever did — the removed predicate did not provide that. Out of scope here.
