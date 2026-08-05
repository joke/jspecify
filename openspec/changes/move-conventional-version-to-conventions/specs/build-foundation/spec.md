## MODIFIED Requirements

### Requirement: Convention plugin
The convention plugin SHALL be named `conventions` (applied as `id 'conventions'`) and SHALL be
adapted from `percolate.conventions`, retaining: Spotless with Palantir Java Format, PMD, CodeNarc,
Error Prone with NullAway in jspecify mode, the `test` / `integrationTest` split by JUnit tag,
Pitest, and the publishing, signing and metadata wiring.

The convention plugin SHALL NOT compute a version itself: it SHALL neither invoke `git describe` nor
assign `project.version`. It SHALL instead apply `io.github.joke.conventional-version`, which does
(see Version derivation). The application SHALL be in the convention plugin's `plugins` block, so
that the version is assigned before the script body creates the `MavenPublication` that reads it.

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

#### Scenario: The convention plugin applies the versioning plugin
- **WHEN** the convention plugin's `plugins` block is inspected
- **THEN** it applies `io.github.joke.conventional-version` without a version

#### Scenario: Configuration cache survives a version query
- **WHEN** `./gradlew help` runs twice with `org.gradle.configuration-cache=true`
- **THEN** the second run reuses the configuration cache

### Requirement: Version derivation
The project version SHALL be calculated by the `io.github.joke.conventional-version` plugin, which
predicts the version `release-please` will cut next from the conventional commits since the released
version recorded in the manifest. The plugin SHALL be applied at project level by the `conventions`
plugin and SHALL be the only thing in the build that assigns a version. No workflow, task or script
SHALL pass a flag, property or stage to select between a release and a snapshot version.

Every project that applies `conventions` SHALL therefore be versioned. The root project applies no
plugin and SHALL keep Gradle's default `unspecified`; this is permitted because the root project has
no `build.gradle`, declares no publication, and nothing in the build or in nmcp reads its version.

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

#### Scenario: Every publishable module is versioned
- **WHEN** the version of `:processor` and of `:dependencies` is queried
- **THEN** neither is `unspecified`

#### Scenario: Version resolves before the publication is created
- **WHEN** the publication is configured
- **THEN** the version is already resolved and is not `unspecified`

#### Scenario: The root project is unversioned and unpublished
- **WHEN** the root project's version is queried
- **THEN** it is `unspecified`
- **AND** the root project declares no publication

#### Scenario: The aggregated deployment carries the resolved version
- **WHEN** the bundle produced for Maven Central is inspected
- **THEN** every coordinate in it carries the derived version and none carries `unspecified`

#### Scenario: A missing release-please configuration fails the build
- **WHEN** `release-please-config.json` or `.release-please-manifest.json` is absent
- **THEN** the build fails rather than inventing a version

### Requirement: Plugin version declaration
Every Gradle project plugin version SHALL be declared exactly once, as a dependency in
`buildSrc/build.gradle`. `settings.gradle` SHALL NOT contain a `pluginManagement` block. A plugin
applied by the convention plugin rather than by a module SHALL be declared as its plugin-marker
coordinate, `<id>:<id>.gradle.plugin:<version>`.

Settings plugins are exempt and SHALL be declared with their versions in the `settings.gradle`
`plugins` block. `com.gradleup.nmcp.settings` SHALL be the only such plugin, because it is the only
one with no project-level equivalent. `settings.gradle` SHALL otherwise contain only the
`nmcpSettings` configuration, the `dependencyResolutionManagement` repositories, `rootProject.name`
and the project includes.

#### Scenario: No project plugin versions live in settings
- **WHEN** `settings.gradle` is inspected
- **THEN** it declares no `pluginManagement` block
- **AND** it applies only settings plugins

#### Scenario: Only nmcp is exempt
- **WHEN** the `plugins` block of `settings.gradle` is inspected
- **THEN** `com.gradleup.nmcp.settings` is the only plugin it declares

#### Scenario: The versioning plugin version lives in buildSrc
- **WHEN** `buildSrc/build.gradle` is inspected
- **THEN** it declares the `io.github.joke.conventional-version` plugin-marker dependency at 2.1.0

#### Scenario: No plugin version is declared twice
- **WHEN** `settings.gradle` and `buildSrc/build.gradle` are compared
- **THEN** no plugin appears in both with a version

#### Scenario: Pitest is applied by the convention plugin
- **WHEN** a module applying the convention plugin has the `java` plugin
- **THEN** Pitest is applied to it without the module declaring it
- **AND** it is not applied to the `java-platform` module
