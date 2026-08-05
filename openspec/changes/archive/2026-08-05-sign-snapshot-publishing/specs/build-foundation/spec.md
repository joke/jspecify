## MODIFIED Requirements

### Requirement: Snapshot publishing
Snapshots SHALL be published to the Central Portal snapshot repository via
`publishAggregationToCentralSnapshots`, on every push to `main` for which `release-please` did not
cut a release. Snapshot publishing SHALL NOT run for pull requests.

#### Scenario: A push to main publishes a snapshot
- **WHEN** a commit is pushed to `main` and `release-please` does not cut a release
- **THEN** `publishAggregationToCentralSnapshots` runs
- **AND** the published version ends in `-SNAPSHOT`

#### Scenario: A release commit publishes no snapshot
- **WHEN** `release-please` cuts a release on a push to `main`
- **THEN** `publishAggregationToCentralSnapshots` does not run
- **AND** only the release version is published

#### Scenario: Pull requests publish nothing
- **WHEN** a pull request is opened or updated
- **THEN** no publishing task runs

## RENAMED Requirements

- FROM: `### Requirement: Signing boundary`
- TO: `### Requirement: Publish signing`

## MODIFIED Requirements

### Requirement: Publish signing
Both publishing jobs SHALL sign through the same mechanism. Each SHALL import the GPG key with
`crazy-max/ghaction-import-gpg`, reading `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE` and `GPG_FINGERPRINT`,
and the build SHALL sign every publication it produces. Signing SHALL be required in both, which is
Gradle's default; the build SHALL NOT declare a `required` predicate that varies signing by task
graph.

The purpose is that snapshot publishing rehearses release publishing. Every merge to `main` exercises
the key, the passphrase, the fingerprint and `signMavenPublication`, so a signing fault surfaces on a
snapshot that can simply be republished rather than on a Central release that cannot be withdrawn.

Snapshots SHALL therefore carry `.asc` signatures. The Central Portal snapshot repository does not
require them and accepts them.

#### Scenario: Release publishing signs
- **WHEN** the `publish` job runs `publishAggregationToCentralPortal`
- **THEN** it has imported the GPG key beforehand
- **AND** the published artifacts carry `.asc` signatures

#### Scenario: Snapshot publishing signs
- **WHEN** the `snapshot` job runs `publishAggregationToCentralSnapshots`
- **THEN** it has imported the GPG key beforehand
- **AND** the published artifacts carry `.asc` signatures

#### Scenario: Both jobs import the key identically
- **WHEN** the `publish` and `snapshot` jobs in `release.yml` are compared
- **THEN** both use `crazy-max/ghaction-import-gpg` with the same three secrets

#### Scenario: No signing predicate varies by task graph
- **WHEN** the `signing` block in the convention plugin is inspected
- **THEN** it declares no `required` predicate
- **AND** it does not inspect `gradle.taskGraph`
