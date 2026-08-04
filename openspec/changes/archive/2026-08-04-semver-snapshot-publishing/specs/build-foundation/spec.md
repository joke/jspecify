## MODIFIED Requirements

### Requirement: Convention plugin
The convention plugin SHALL be named `conventions` (applied as `id 'conventions'`) and SHALL be
adapted from `percolate.conventions`, retaining: Spotless with Palantir Java Format, PMD, CodeNarc,
Error Prone with NullAway in jspecify mode, the `test` / `integrationTest` split by JUnit tag,
Pitest, and the publishing, signing and metadata wiring.

Versioning SHALL NOT be the convention plugin's concern: it SHALL neither invoke `git describe` nor
set `project.version`. The version is assigned by a settings plugin (see Version derivation).

The Lombok configuration SHALL NOT be carried over.

#### Scenario: Plugin id is unprefixed
- **WHEN** a module applies the convention plugin
- **THEN** it uses `id 'conventions'`

#### Scenario: NullAway is configured as in percolate
- **WHEN** the convention plugin's NullAway block is inspected
- **THEN** `jspecifyMode`, `onlyNullMarked`, `treatGeneratedAsUnannotated`, `checkContracts` and
  `acknowledgeRestrictiveAnnotations` are enabled and `RequireExplicitNullMarking` is an error

#### Scenario: The convention plugin does not compute the version
- **WHEN** the convention plugin is inspected
- **THEN** it contains no `git describe` invocation
- **AND** it does not assign `version`

#### Scenario: Configuration cache survives a version query
- **WHEN** `./gradlew help` runs twice with `org.gradle.configuration-cache=true`
- **THEN** the second run reuses the configuration cache

### Requirement: Publishing
The `processor` module SHALL be published to Maven Central via the Central Portal, signed, with
sources and javadoc jars, at the coordinates `io.github.joke.jspecify:processor`.

Release publishing SHALL run only on a commit for which `release-please` created a release, and
SHALL use `publishAggregationToCentralPortal`.

#### Scenario: Artifact is publishable
- **WHEN** the publication is configured
- **THEN** its group is `io.github.joke.jspecify` and its artifactId is `processor`
- **AND** it carries a sources jar, a javadoc jar, license, developer and SCM metadata, and a
  signature

#### Scenario: An ordinary push to main publishes no release
- **WHEN** a commit is pushed to `main` and `release-please` does not cut a release
- **THEN** `publishAggregationToCentralPortal` does not run

## ADDED Requirements

### Requirement: Version derivation
The project version SHALL be calculated by the `io.github.joke.conventional-version` settings plugin,
which predicts the version `release-please` will cut next from the conventional commits since the
released version recorded in the manifest. The plugin SHALL be applied in `settings.gradle` and SHALL
be the only thing in the build that assigns a version. No workflow, task or script SHALL pass a flag,
property or stage to select between a release and a snapshot version.

The version policy SHALL live entirely in `release-please-config.json` and
`.release-please-manifest.json`, which the build reads and never writes. `release-please` SHALL
remain the sole author of tags, of the CHANGELOG and of the manifest; the build SHALL NOT create or
push tags.

#### Scenario: The release commit yields the release version
- **WHEN** `HEAD` is the commit `release-please` released as `1.2.3`
- **THEN** the project version is `1.2.3`
- **AND** it does not end in `-SNAPSHOT`

#### Scenario: A commit past a release yields the next planned version as a snapshot
- **WHEN** `HEAD` is past the released version `1.2.3` and a `feat:` commit is in the range
- **THEN** the project version is `1.3.0-SNAPSHOT`
- **AND** it does not contain the commit hash

#### Scenario: Commits between the same pair of releases share one coordinate
- **WHEN** the version is calculated at two different commits, both past the same released version
  and before the next release, with the same set of commit types in range
- **THEN** both calculations produce the same version string

#### Scenario: No stage or version property is set by the build
- **WHEN** `gradle.properties`, `settings.gradle` and both workflow files are inspected
- **THEN** no version, stage, scope or tag-prefix property is declared

#### Scenario: Version resolves before any project is configured
- **WHEN** the publication is configured
- **THEN** the version is already resolved and is not `unspecified`

#### Scenario: A missing release-please configuration fails the build
- **WHEN** `release-please-config.json` or `.release-please-manifest.json` is absent
- **THEN** the build fails rather than inventing a version

### Requirement: release-please manifest mode
`release-please` SHALL be configured in manifest mode: `release-please-config.json` SHALL declare a
single root package with `release-type: simple`, and `.release-please-manifest.json` SHALL record the
released version. The workflow SHALL NOT pass `release-type` inline, since the action reads both
files itself.

#### Scenario: Configuration lives in the repository
- **WHEN** the repository root is inspected
- **THEN** both `release-please-config.json` and `.release-please-manifest.json` exist

#### Scenario: The workflow carries no release configuration
- **WHEN** the `release-please` job in `release.yml` is inspected
- **THEN** the action step declares no `release-type` input

### Requirement: Snapshot publishing
Snapshots SHALL be published to the Central Portal snapshot repository via
`publishAggregationToCentralSnapshots`, on every push to `main` for which `release-please` did not
cut a release. Snapshot publishing SHALL NOT require a GPG key, and SHALL NOT run for pull requests.

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

### Requirement: Signing boundary
Signing SHALL be required for release publishing and SHALL NOT be required for snapshot publishing.
The predicate that decides this SHALL match the release publishing task specifically, and SHALL NOT
match `publishAggregationToCentralSnapshots`.

#### Scenario: Release publishing signs
- **WHEN** the task graph contains `publishAggregationToCentralPortal`
- **THEN** signing is required

#### Scenario: Snapshot publishing does not sign
- **WHEN** the task graph contains `publishAggregationToCentralSnapshots` and no GPG key is available
- **THEN** signing is not required
- **AND** the build succeeds

### Requirement: Root project repository declaration
The root project SHALL have repositories available even though it applies no build script, because
the nmcp aggregation tasks resolve their own dependencies into a root-project configuration. This
SHALL be declared once via `dependencyResolutionManagement` in `settings.gradle`.

#### Scenario: The aggregation tasks configure
- **WHEN** `publishAggregationToCentralPortal` or `publishAggregationToCentralSnapshots` is
  configured
- **THEN** the build does not fail with "no repositories are defined"

### Requirement: Plugin version declaration
Every Gradle project plugin version SHALL be declared exactly once, as a dependency in
`buildSrc/build.gradle`. `settings.gradle` SHALL NOT contain a `pluginManagement` block.

Settings plugins are exempt and SHALL be declared with their versions in the `settings.gradle`
`plugins` block: `com.gradleup.nmcp.settings` and `io.github.joke.conventional-version`.
`settings.gradle` SHALL otherwise contain only the `nmcpSettings` configuration, `rootProject.name`
and the project includes.

#### Scenario: No project plugin versions live in settings
- **WHEN** `settings.gradle` is inspected
- **THEN** it declares no `pluginManagement` block
- **AND** it applies only settings plugins

#### Scenario: No plugin version is declared twice
- **WHEN** `settings.gradle` and `buildSrc/build.gradle` are compared
- **THEN** no plugin appears in both with a version

#### Scenario: Pitest is applied by the convention plugin
- **WHEN** a module applying the convention plugin has the `java` plugin
- **THEN** Pitest is applied to it without the module declaring it
- **AND** it is not applied to the `java-platform` module

### Requirement: Version-resolving jobs use full git history
Every CI job that resolves the project version SHALL check out with `fetch-depth: 0`, because the
version is computed from the commits since the released version and the plugin fails the build on a
shallow clone rather than guessing.

#### Scenario: All checkouts are unshallow
- **WHEN** the workflow files are inspected
- **THEN** every `actions/checkout` step that is followed by a Gradle invocation sets
  `fetch-depth: 0`

### Requirement: No documentation site
The build SHALL define no `antora` task and the workflows SHALL invoke none. No workflow SHALL
declare the `pages: write` or `id-token: write` permission, or a `github-pages` environment, and the
project metadata SHALL NOT advertise a GitHub Pages URL.

#### Scenario: No workflow invokes antora
- **WHEN** the workflow files are inspected
- **THEN** no step runs `./gradlew antora`
- **AND** no `docs` or `deploy` job is defined

#### Scenario: Release publishing does not wait on a docs deploy
- **WHEN** the `publish` job in `release.yml` is inspected
- **THEN** it depends only on the `release-please` job

#### Scenario: Metadata advertises no site
- **WHEN** the convention plugin's `metadata` block is inspected
- **THEN** its `github` configuration does not call `pages()`
