## Context

Three `release` workflow runs exist and all three failed. The first (2026-08-01) failed in
`release-please` itself — GitHub Actions was not permitted to create pull requests, since fixed via
repository settings. The two on 2026-08-05 reached the `snapshot` job and failed there:

```
> Task :processor:signMavenPublication FAILED
gpg: no default secret key: No secret key
Execution failed for task ':processor:signMavenPublication' (registered by plugin 'conventions')
```

The `snapshot` job was written without a GPG import on purpose. The workflow comment that shipped
with it said: *"No GPG step - Central does not require signatures on snapshots, and the signing
predicate in conventions.gradle matches the release task alone."* The predicate is
`required { gradle.taskGraph.allTasks.any { it.name == 'publishAggregationToCentralPortal' } }`, and
it does match the release task alone. Matching correctly is not enough.

Gradle's `Sign` task carries:

```java
onlyIf("Signing is required, or signatory is set",
       task -> isRequired() || getSignatory() != null)
```

`required = false` therefore skips signing only when there is no signatory at all. `useGpgCmd()`
installs a `GnupgSignatoryProvider` unconditionally and never checks whether a key exists, so the
signatory is always non-null and the task always runs.

```
  snapshot job, no key
        │
        ▼
  Sign.onlyIf ──► isRequired()  = false  ─┐
              └─► getSignatory() ≠ null  ─┴─ OR ─► true ─► gpg ─► exit 2
```

This capability was adapted from percolate, which has no snapshot job — it publishes only on
release. There was no keyless publishing path upstream, so there was no upstream design for one.
percolate's own predicate is the looser `it.name.contains('Aggregation')`, unambiguous there because
percolate has exactly one aggregation task.

## Goals / Non-Goals

**Goals:**

- Make the snapshot job succeed.
- Make snapshot publishing and release publishing use the same mechanism, so the release path is
  exercised on every merge rather than first at tag time.
- Remove build logic that cannot affect an outcome, and the spec text that describes it as if it
  could.

**Non-Goals:**

- Protecting a keyless local `publishToMavenLocal`. Nothing provides that today; the removed
  predicate did not. Adding it would need `onlyIf`, and it is a separate want.
- Changing when snapshots publish, what they are named, or how the version is derived.
- Changing the release job, which is already correct and already imports the key.

## Decisions

### Sign snapshots rather than suppress signing

The alternative is to gate the `Sign` tasks with `onlyIf` so snapshots genuinely skip signing, which
is what the current spec describes. Rejected on the grounds that staging should exercise the same
path as production. A snapshot publish that signs is a full rehearsal of a release publish; a
snapshot publish that skips signing leaves `signMavenPublication`, the GPG import action, the
secrets and the passphrase all unexercised until the one run where a failure is expensive and
irreversible.

The cost is that the release key becomes readable by a job triggered on every push to `main` rather
than only on tagged releases. That is a real widening of exposure and is accepted deliberately: the
same key, the same three secrets and the same action already run in `publish`, and the threat model
for "can write to `main`" and "can cut a release" is the same person.

The `onlyIf` alternative also carries an unresolved risk this decision avoids entirely — a `Sign`
task skipped by `onlyIf` (rather than by having no signatory) still has its `.asc` outputs wired
into the `MavenPublication`, and whether publishing then fails on missing files was never
established. Nothing is skipped under this decision, so the question does not arise.

### Remove the `required` predicate rather than repair it

With both publishing jobs holding a key, the predicate cannot change an outcome in any graph:

| task graph | `required` | signatory | `Sign` runs | outcome |
|---|---|---|---|---|
| `publishAggregationToCentralPortal` | true | present | yes | signs |
| `publishAggregationToCentralSnapshots` | false | present | yes | signs |
| local publish, no key | false | present | yes | fails, as it does today |

Every row runs the task. Deleting the predicate restores Gradle's default (`required = true`), which
is what the build actually does. Keeping it would leave a line that reads as a safety boundary while
enforcing nothing — the precise failure this change exists to correct.

Alternative considered: replace it with `onlyIf` so that at least local publishing is protected.
Rejected as scope creep — it is a new capability, not a repair, and the goal here is that snapshot
and release behave identically.

## Risks / Trade-offs

**The release key is exposed to every default-branch merge** → Accepted, and recorded in the
proposal's Impact section rather than mitigated. The alternative buys a narrower blast radius at the
cost of never testing the signing path. If this is later judged too broad, the answer is a separate
signing key for snapshots, not a return to keyless publishing.

**Central may reject `.asc` files on the snapshot repository** → Believed to accept them —
signatures are optional there, not forbidden — but unverified from this side. The first snapshot run
after this change is the test, and a failed snapshot publish is cheap to diagnose and retry, unlike
a release. If it is rejected, the fallback is the `onlyIf` approach with its own risk re-opened.

**A spec requirement was archived as complete having never run** → `semver-snapshot-publishing`
asserted that snapshot publishing succeeds with no GPG key available and was archived on 2026-08-04
without that path executing once in CI. This change corrects the requirement, but the gap it exposes
— archive criteria that accept a green local build for a CI-only path — is not addressed here.
