# build-foundation Specification

## Purpose

The Gradle build that produces the published annotation processor: module layout, the shared
`conventions` plugin, language level, the dependency and test stacks, mutation-testing thresholds,
and publishing to Maven Central.

## Requirements

### Requirement: Module layout
The build SHALL consist of exactly three Gradle projects: `buildSrc` (the convention plugin),
`dependencies` (a `java-platform` holding every external version), and `processor` (the published
artifact). No `bom` module is created — a single published artifact does not need a platform.

#### Scenario: Settings declare only the processor module
- **WHEN** `settings.gradle` is inspected
- **THEN** it includes `processor` and `dependencies` and nothing else

#### Scenario: All external versions live in the platform
- **WHEN** any module's `build.gradle` declares an external dependency
- **THEN** it declares no version, and the version is constrained by `project(':dependencies')`

### Requirement: Convention plugin
The convention plugin SHALL be named `conventions` (applied as `id 'conventions'`) and SHALL be
adapted from `percolate.conventions`, retaining: git-describe versioning, Spotless with
Palantir Java Format, PMD, CodeNarc, Error Prone with NullAway in jspecify mode, the `test` /
`integrationTest` split by JUnit tag, Pitest, and the publishing, signing and metadata wiring.

The Lombok configuration SHALL NOT be carried over.

#### Scenario: Plugin id is unprefixed
- **WHEN** a module applies the convention plugin
- **THEN** it uses `id 'conventions'`

#### Scenario: NullAway is configured as in percolate
- **WHEN** the convention plugin's NullAway block is inspected
- **THEN** `jspecifyMode`, `onlyNullMarked`, `treatGeneratedAsUnannotated`, `checkContracts` and
  `acknowledgeRestrictiveAnnotations` are enabled and `RequireExplicitNullMarking` is an error

### Requirement: Language level and toolchain
Production code SHALL target Java 11 (`options.release = 11`) and SHALL compile with `-Werror`.
The build SHALL run on the JDK pinned in `.mise.toml`.

#### Scenario: Release level is 11
- **WHEN** `JavaCompile` tasks are configured
- **THEN** `options.release` is 11 and `-Werror` is present in the compiler arguments

### Requirement: Dependencies of the processor module
The `processor` module SHALL declare `com.google.auto.service:auto-service` as an
`annotationProcessor` dependency, and `com.google.auto.service:auto-service-annotations`,
`org.jetbrains:annotations` (for `@VisibleForTesting`) and `org.jspecify:jspecify` as `compileOnly`.
None of these SHALL appear in the published POM: the
consuming project supplies its own `org.jspecify:jspecify` at a version it chooses, and this
project SHALL NOT impose one transitively. It SHALL NOT depend on Dagger, JavaPoet, auto-common,
Guava, or JGraphT, and SHALL NOT be shaded.

#### Scenario: The published artifact has no dependencies
- **WHEN** the published POM is inspected
- **THEN** it declares no `compile`- or `runtime`-scope dependency
- **AND** neither AutoService nor JSpecify appears in it

#### Scenario: No shading is configured
- **WHEN** `processor/build.gradle` is inspected
- **THEN** no `com.gradleup.shadow` plugin is applied

### Requirement: Test stack
Tests SHALL be written as Spock 2.4 specifications on Groovy 5, using
`com.google.testing.compile:compile-testing` as the compilation harness and AssertJ for value
assertions. The convention plugin SHALL apply CodeNarc to Groovy source with all three violation
thresholds at zero, configured by a `.codenarc.groovy` copied from percolate.

#### Scenario: CodeNarc gates Groovy source
- **WHEN** a module applying the convention plugin has the `groovy` plugin
- **THEN** CodeNarc is applied with `maxPriority1Violations`, `maxPriority2Violations` and
  `maxPriority3Violations` all zero

### Requirement: Mutation testing thresholds
Pitest SHALL be configured with `mutationThreshold`, `coverageThreshold` and
`testStrengthThreshold` all at 100, and `check` SHALL depend on `pitest`.

#### Scenario: Check runs mutation testing
- **WHEN** `./gradlew check` runs
- **THEN** the `pitest` task executes and fails the build below any 100% threshold

### Requirement: Publishing
The `processor` module SHALL be published to Maven Central via the Central Portal, signed, with
sources and javadoc jars, at the coordinates `io.github.joke.jspecify:processor`.

#### Scenario: Artifact is publishable
- **WHEN** the publication is configured
- **THEN** its group is `io.github.joke.jspecify` and its artifactId is `processor`
- **AND** it carries a sources jar, a javadoc jar, license, developer and SCM metadata, and a
  signature
