## MODIFIED Requirements

### Requirement: Convention plugin
The convention plugin SHALL be named `conventions` (applied as `id 'conventions'`) and SHALL be
adapted from `percolate.conventions`, retaining: Spotless with Palantir Java Format, PMD, CodeNarc,
Lombok, Error Prone with NullAway in jspecify mode, the `test` / `integrationTest` split by JUnit
tag, Pitest, and the publishing, signing and metadata wiring.

The convention plugin SHALL NOT compute a version itself: it SHALL neither invoke `git describe` nor
assign `project.version`. It SHALL instead apply `io.github.joke.conventional-version`, which does
(see Version derivation). The application SHALL be in the convention plugin's `plugins` block, so
that the version is assigned before the script body creates the `MavenPublication` that reads it.

Tool versions for configurations the convention plugin creates or populates — Palantir Java Format,
PMD, CodeNarc, Error Prone, NullAway, Pitest — SHALL be declared literally in the convention plugin,
not in the `dependencies` platform, because those configurations are created by plugins the platform
cannot constrain. The platform SHALL NOT carry a constraint for a coordinate the convention plugin
already declares with a version, since such a constraint can never take effect.

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

#### Scenario: No dead constraint shadows a literal tool version
- **WHEN** `dependencies/build.gradle` and `conventions.gradle` are compared
- **THEN** no coordinate declared with a literal version in `conventions.gradle` also has a
  constraint in the platform

### Requirement: Dependencies of the processor module
The `processor` module SHALL declare `com.google.auto.service:auto-service` as an
`annotationProcessor` dependency, and `com.google.auto.service:auto-service-annotations`,
`org.jetbrains:annotations` (for `@VisibleForTesting`) and `org.jspecify:jspecify` as `compileOnly`.
It SHALL NOT declare Lombok itself: the convention plugin contributes Lombok to every `java` module
(see Lombok).

None of these SHALL appear in the published POM: the consuming project supplies its own
`org.jspecify:jspecify` at a version it chooses, and this project SHALL NOT impose one transitively.
It SHALL NOT depend on Dagger, JavaPoet, auto-common, Guava, or JGraphT, and SHALL NOT be shaded.

The empty POM follows from the dependency configurations themselves — `annotationProcessor`,
`compileOnly`, `testImplementation` and `testRuntimeOnly` are not published scopes — and is not
enforced by a build-time verification task. This requirement is the record of that constraint;
`processor/build.gradle` SHALL NOT carry a comment restating it, because a reviewer changing a
dependency's configuration is expected to consult the spec rather than rely on a comment surviving
an edit.

#### Scenario: The published artifact has no dependencies
- **WHEN** the published POM is inspected
- **THEN** it declares no `compile`- or `runtime`-scope dependency
- **AND** neither AutoService, JSpecify nor Lombok appears in it

#### Scenario: No shading is configured
- **WHEN** `processor/build.gradle` is inspected
- **THEN** no `com.gradleup.shadow` plugin is applied

#### Scenario: The module does not declare Lombok
- **WHEN** `processor/build.gradle` is inspected
- **THEN** it declares no `org.projectlombok:lombok` dependency on any configuration

### Requirement: Test stack
Tests SHALL be written as Spock 2.4 specifications on Groovy 5, and Spock SHALL be the only test
framework: no test SHALL be written against the JUnit Jupiter API, and `org.junit.jupiter:junit-jupiter`
SHALL NOT be a `testImplementation` dependency of any module.

`com.google.testing.compile:compile-testing` SHALL be the compilation harness, and its
`CompilationSubject` (a Truth subject) SHALL provide compilation assertions. AssertJ SHALL NOT be a
dependency of any module; it has no usages and never had.

Mocking SHALL be Spock's, with Mockito as the mock maker, selected by
`preferredMockMaker spock.mock.MockMakers.mockito` in `SpockConfig.groovy`. Because no test source
references the Mockito API, `org.mockito:mockito-junit-jupiter` SHALL be `testRuntimeOnly` and SHALL
NOT be `testImplementation`. `org.junit.platform:junit-platform-launcher` SHALL likewise be
`testRuntimeOnly`.

Mockito and JUnit versions SHALL come from `mockito-bom` and `junit-bom` imported by the
`dependencies` platform, rather than from hand-pinned constraints or transitive resolution.

The convention plugin SHALL apply CodeNarc to Groovy source with all three violation thresholds at
zero, configured by a `.codenarc.groovy` copied from percolate.

#### Scenario: CodeNarc gates Groovy source
- **WHEN** a module applying the convention plugin has the `groovy` plugin
- **THEN** CodeNarc is applied with `maxPriority1Violations`, `maxPriority2Violations` and
  `maxPriority3Violations` all zero

#### Scenario: No JUnit-API test exists
- **WHEN** `processor/src/test` is inspected
- **THEN** no source imports `org.junit.jupiter.api`
- **AND** every specification extends `spock.lang.Specification`

#### Scenario: Mockito is present only at runtime
- **WHEN** `processor/src/test` is inspected
- **THEN** no source imports `org.mockito`
- **AND** `org.mockito:mockito-junit-jupiter` is declared `testRuntimeOnly`

#### Scenario: Mocks are created by Mockito
- **WHEN** a Spock `Mock()` is created during a test run
- **THEN** `SpockConfig.groovy` has selected `spock.mock.MockMakers.mockito` as the preferred mock
  maker

#### Scenario: No AssertJ dependency
- **WHEN** `dependencies/build.gradle` and every module's `build.gradle` are inspected
- **THEN** `org.assertj:assertj-core` appears in none of them

## ADDED Requirements

### Requirement: Lombok
The convention plugin SHALL make Lombok available to every module with the `java` plugin, declaring
`org.projectlombok:lombok` on both `annotationProcessor` and `compileOnly` under
`pluginManager.withPlugin('java')`. No module SHALL declare Lombok itself, and it SHALL NOT reach the
`java-platform` module. Its version SHALL be constrained by the `dependencies` platform, because
these are ordinary dependency configurations that the platform can see.

Lombok being available SHALL NOT imply it is used. A module with no generated members is a valid
state; the convention establishes what every module *may* use.

A `lombok.config` SHALL exist at the repository root declaring:

- `config.stopBubbling = true`, so configuration resolution stops at the checkout root and the build
  is unaffected by any `lombok.config` above it;
- `lombok.addLombokGeneratedAnnotation = true`, so generated members carry `@lombok.Generated` and
  are therefore excluded both by NullAway's `treatGeneratedAsUnannotated` and by Pitest's filtering
  of `Generated`-annotated members. Without this the 100% mutation, coverage and test-strength
  thresholds would be measuring Lombok's output;
- `lombok.addNullAnnotations = jspecify`, so generated members are annotated with JSpecify rather
  than with Lombok's own annotations or none. A module using a Lombok feature that emits null
  annotations SHALL have `org.jspecify:jspecify` on its compile classpath;
- `lombok.experimental.flagUsage = ALLOW`, permitting experimental features. A warning would fail the
  build under `-Werror`, so the alternative is prohibition rather than a warning.

Lombok SHALL NOT appear in any published POM. `annotationProcessor` and `compileOnly` are not
published scopes, which is the same mechanism that keeps JSpecify out.

#### Scenario: Lombok is wired by the convention plugin
- **WHEN** `conventions.gradle` is inspected
- **THEN** `org.projectlombok:lombok` is declared on `annotationProcessor` and on `compileOnly`
  inside `pluginManager.withPlugin('java')`

#### Scenario: The platform module gets no Lombok
- **WHEN** the `dependencies` module's configurations are resolved
- **THEN** no `org.projectlombok:lombok` dependency is present

#### Scenario: The repository-root configuration is complete
- **WHEN** `lombok.config` is inspected
- **THEN** it declares `config.stopBubbling`, `lombok.addLombokGeneratedAnnotation`,
  `lombok.addNullAnnotations = jspecify` and `lombok.experimental.flagUsage`

#### Scenario: Lombok does not reach the published POM
- **WHEN** the published POM is inspected
- **THEN** `org.projectlombok:lombok` does not appear in it

#### Scenario: Generated members do not count toward mutation thresholds
- **WHEN** a Lombok-generated member exists and `./gradlew pitest` runs
- **THEN** the member is filtered by its `@lombok.Generated` annotation
- **AND** the 100% mutation, coverage and test-strength thresholds still apply to hand-written code

### Requirement: Static analysis ruleset
The PMD ruleset SHALL be resolved from the shared artifact `io.github.joke.pmd:rules`, selected as
`rulesets/java/joke-strict.xml`, rather than from a file in this repository. No `.pmd.xml` SHALL
exist. The ruleset is a house standard shared across `io.github.joke` projects and contains nothing
specific to this one.

The PMD engine SHALL be declared as an explicit `net.sourceforge.pmd:pmd-dist` dependency on the
`pmd` configuration, and `toolVersion` SHALL NOT also be set, so that exactly one mechanism
determines the engine version and two PMD versions cannot reach one classpath. `consoleOutput` SHALL
be enabled and `rulesMinimumPriority` SHALL remain 5.

The convention plugin SHALL declare the Central Portal snapshot repository, ahead of `mavenCentral()`
and restricted by `content { includeGroupAndSubgroups('io.github.joke') }`, so that no coordinate
outside that group can resolve a snapshot.

The shared ruleset is a superset of the local one it replaces, adding eight custom rules where
`.pmd.xml` carried a single XPath rule that never matched. Production code SHALL conform to it rather
than suppress it: the project SHALL NOT layer a local ruleset that excludes rules from
`joke-strict.xml`, and SHALL NOT carry `@SuppressWarnings("PMD.…")` to silence one. A rule judged
wrong SHALL be changed in the rules project instead.

In particular, a method that exists only as a testing seam SHALL be package-private and annotated
`@VisibleForTesting`, never `protected`, which is the idiom `AvoidPrivateAndProtectedMethods` and
`UseVisibleForTestingAnnotation` encode between them.

While `io.github.joke.pmd:rules` is a `-SNAPSHOT` version, the build is knowingly non-reproducible in
its lint gate: an unchanged tree may fail on a rule that changed upstream. This is accepted for the
period in which the shared rules are being evaluated against real code. No release SHALL be published
while the ruleset dependency is a snapshot.

#### Scenario: The local ruleset is gone
- **WHEN** the repository root is inspected
- **THEN** no `.pmd.xml` exists
- **AND** `conventions.gradle` sets `ruleSets = ['rulesets/java/joke-strict.xml']`

#### Scenario: The rules artifact is on the pmd configuration
- **WHEN** the `pmd` configuration is resolved
- **THEN** it contains `io.github.joke.pmd:rules`
- **AND** it contains exactly one `net.sourceforge.pmd:pmd-dist` version

#### Scenario: The snapshot repository is restricted
- **WHEN** the convention plugin's `repositories` block is inspected
- **THEN** the Central Portal snapshot repository declares
  `includeGroupAndSubgroups('io.github.joke')`
- **AND** resolving any coordinate outside that group never queries it

#### Scenario: Production code conforms with no suppressions
- **WHEN** `./gradlew :processor:pmdMain` runs
- **THEN** it reports zero violations
- **AND** no source carries a `@SuppressWarnings` naming a PMD rule
- **AND** no ruleset file exists in this repository

#### Scenario: Testing seams are package-private
- **WHEN** `NullMarkedProcessor` is inspected
- **THEN** every method annotated `@VisibleForTesting` is package-private
- **AND** no method is declared `protected`

#### Scenario: A release is blocked on a snapshot ruleset
- **WHEN** `publishAggregationToCentralPortal` would run and the `pmd` configuration resolves a
  `-SNAPSHOT` version of `io.github.joke.pmd:rules`
- **THEN** the release is not published until the ruleset is pinned to a release version

### Requirement: The processor's own package-info
`processor/src/main/java/io/github/joke/jspecify/processor/package-info.java` SHALL be hand-written
and SHALL carry `@NullMarked` via an imported `org.jspecify.annotations.NullMarked` rather than a
fully qualified annotation. Imports are legal in a `package-info.java` when they follow the package
declaration.

It is hand-written because this project cannot bootstrap itself: the processor that would generate
this file is the one being compiled here, so it is not on its own annotation-processor path. This
requirement is the record of that fact, and the file SHALL NOT restate it in a comment.

Self-consumption — placing the processor's own previously released version on its own
`annotationProcessor` configuration so that this file would be generated — has been attempted and
does not work. The build SHALL NOT carry residue from that attempt: `processor/build.gradle` SHALL
declare no `resolutionStrategy` on the `annotationProcessor` configuration, and in particular no
`useGlobalDependencySubstitutionRules = false`.

The convention plugin's `RequireExplicitNullMarking` Error Prone check runs at error level, so the
absence of this file is a build failure rather than a silent gap.

#### Scenario: The file is hand-written and imported
- **WHEN** `processor/src/main/java/io/github/joke/jspecify/processor/package-info.java` is inspected
- **THEN** it imports `org.jspecify.annotations.NullMarked` and annotates the package with
  `@NullMarked`
- **AND** it carries no `@javax.annotation.processing.Generated` annotation

#### Scenario: No self-consumption residue remains
- **WHEN** `processor/build.gradle` is inspected
- **THEN** it declares no `configurations { annotationProcessor { ... } }` block
- **AND** `useGlobalDependencySubstitutionRules` appears nowhere in the build

#### Scenario: Removing it fails the build
- **WHEN** the file is deleted and `./gradlew :processor:compileJava` runs
- **THEN** the build fails on `RequireExplicitNullMarking`
- **AND** the processor does not generate a replacement
