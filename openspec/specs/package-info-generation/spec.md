# package-info-generation Specification

## Purpose

The `NullMarkedProcessor` behaviour: which packages get a generated `package-info.java` carrying
`@NullMarked`, which are skipped, what the generated file contains, and how the processor
cooperates with javac rounds, other processors, Gradle incremental processing and NullAway.

## Requirements

### Requirement: Processor registration and claim
The processor SHALL extend `javax.annotation.processing.AbstractProcessor` (not
`com.google.auto.common.BasicAnnotationProcessor`), SHALL be registered via
`@AutoService(Processor.class)`, SHALL declare `@SupportedAnnotationTypes("*")`, and SHALL report
`SourceVersion.latestSupported()`.

`process()` SHALL always return `false` so that every annotation remains available to all other
processors in the compilation.

The processor SHALL declare no supported options.

#### Scenario: Claims all annotations without consuming them
- **WHEN** `getSupportedAnnotationTypes()` is invoked
- **THEN** it returns a set containing exactly `"*"`

#### Scenario: Never claims annotations
- **WHEN** `process()` is invoked with any round environment
- **THEN** it returns `false`

#### Scenario: Coexists with another processor
- **WHEN** a compilation runs this processor alongside a second processor that reacts to its own
  annotation
- **THEN** the second processor still observes its annotated elements and produces its output

### Requirement: Package discovery
For each round, the processor SHALL derive the set of candidate packages from
`RoundEnvironment.getRootElements()` by taking the enclosing `PackageElement` of every root
`TypeElement`.

A candidate package SHALL be skipped when any of the following holds:
- its enclosing `ModuleElement` is already annotated `@org.jspecify.annotations.NullMarked`;
- a `PackageElement` for it appears directly among the round's root elements (a `package-info.java`
  exists in this compilation);
- `Elements.getPackageElement(packageName)` resolves to an element that already carries annotation
  mirrors (a `package-info.class` reached this compilation from the classpath);
- the processor has already emitted a file for it in an earlier round;
- it is the unnamed (default) package.

#### Scenario: Package with no package-info is generated
- **WHEN** a compilation contains `com.example.Foo` and no `com/example/package-info.java`
- **THEN** the processor generates `com.example.package-info`

#### Scenario: Existing package-info in the same compilation wins
- **WHEN** a compilation contains `com.example.Foo` and a hand-written
  `com/example/package-info.java`
- **THEN** the processor generates nothing for `com.example`
- **AND** the hand-written file is left unmodified

#### Scenario: Existing package-info wins regardless of its content
- **WHEN** the hand-written `com/example/package-info.java` declares `@NullUnmarked`, or declares
  no annotation at all
- **THEN** the processor still generates nothing for `com.example`

#### Scenario: Existing package-info on the classpath wins
- **WHEN** a compilation contains `com.example.FooTest` and a `com.example.package-info` class file
  is present on the compile classpath
- **THEN** the resolved `PackageElement` for `com.example` reports non-empty annotation mirrors
- **AND** the processor generates nothing for `com.example`

#### Scenario: Multiple types in one package produce one file
- **WHEN** a compilation contains `com.example.Foo`, `com.example.Bar` and `com.example.Baz`
- **THEN** exactly one `com.example.package-info` is generated

#### Scenario: Nested packages are each generated
- **WHEN** a compilation contains `com.example.Foo` and `com.example.inner.Bar`
- **THEN** both `com.example.package-info` and `com.example.inner.package-info` are generated

### Requirement: Module-level null marking wins
The processor SHALL generate nothing for any package whose enclosing module is already annotated
`@org.jspecify.annotations.NullMarked`, because NullAway resolves module-level marking for the
module's types and per-package files would be redundant.

Detection SHALL read the module's annotation mirrors by qualified name rather than calling
`getAnnotation(NullMarked.class)`, so that JSpecify is not required on the processor's own runtime
classpath.

#### Scenario: Null-marked module suppresses all generation
- **WHEN** a compilation's `module-info.java` carries `@NullMarked` and its packages have no
  `package-info.java`
- **THEN** no `package-info` source file is generated for any package in that module

#### Scenario: Unmarked module generates normally
- **WHEN** a compilation's `module-info.java` carries no `@NullMarked`
- **THEN** packages with no `package-info.java` are generated as usual

#### Scenario: Unnamed module generates normally
- **WHEN** a compilation has no `module-info.java` at all
- **THEN** packages with no `package-info.java` are generated as usual

### Requirement: Unnamed package diagnostic
The processor SHALL skip any root `TypeElement` residing in the unnamed (default) package, for
which no `package-info.java` can exist, and SHALL emit a `WARNING` diagnostic stating that types
in the default package cannot be null-marked.

#### Scenario: Type in the default package warns
- **WHEN** a compilation contains a type declared with no `package` statement
- **THEN** no file is generated for it
- **AND** a `WARNING` diagnostic is emitted naming the default package as unmarkable
- **AND** the compilation still succeeds

### Requirement: Generated file content
Each generated file SHALL consist of a `@Generated` annotation naming the processor, the
fully-qualified `@org.jspecify.annotations.NullMarked` annotation, and the `package` declaration.
The `@Generated` annotation SHALL be `@javax.annotation.Generated` when the consuming compilation's
source version is `RELEASE_8` or lower, and `@javax.annotation.processing.Generated` otherwise. The
file SHALL NOT contain an import statement.

#### Scenario: Content for source 9 and later is the fully-qualified three-line form
- **WHEN** `com.example.package-info` is generated in a compilation whose source version is 11
- **THEN** the source contains
  `@javax.annotation.processing.Generated("io.github.joke.jspecify.processor.NullMarkedProcessor")`
  followed by `@org.jspecify.annotations.NullMarked` followed by `package com.example;`
- **AND** it contains no `import` statement

#### Scenario: Content for source 8 uses the Java 8 Generated annotation
- **WHEN** `com.example.package-info` is generated in a compilation whose source version is 8
- **THEN** the source contains
  `@javax.annotation.Generated("io.github.joke.jspecify.processor.NullMarkedProcessor")`
  followed by `@org.jspecify.annotations.NullMarked` followed by `package com.example;`
- **AND** it contains no `import` statement

#### Scenario: Marking reaches the class file
- **WHEN** a compilation with a generated `com.example.package-info` completes
- **THEN** `com/example/package-info.class` is present in the compilation output
- **AND** it carries the `org.jspecify.annotations.NullMarked` annotation

### Requirement: Round handling
The processor SHALL emit files eagerly in the round in which a package is discovered, and SHALL
emit nothing when `RoundEnvironment.processingOver()` is true, so that javac never reports
"File for type X created in the last round will not be subject to annotation processing".

Packages first introduced by another processor's generated types in a later round SHALL be
covered.

#### Scenario: No file is created in the final round
- **WHEN** `process()` is invoked with `processingOver()` returning `true`
- **THEN** no source file is created

#### Scenario: Compilation under -Werror is clean
- **WHEN** a compilation using this processor runs with `-Werror`
- **THEN** it succeeds with no warning about files created in the last round

#### Scenario: Packages introduced by another processor are covered
- **WHEN** a second processor generates `com.generated.Thing` in round 1 and `com.generated` has
  no `package-info`
- **THEN** `com.generated.package-info` is generated

#### Scenario: Own output does not retrigger
- **WHEN** the processor generated `com.example.package-info` in round 0
- **THEN** it does not attempt to generate `com.example.package-info` again in any later round

### Requirement: JSpecify availability check
Before generating any file the processor SHALL verify that
`org.jspecify.annotations.NullMarked` is resolvable via `Elements.getTypeElement`. If it is not,
the processor SHALL emit a single `ERROR` diagnostic naming the missing `org.jspecify:jspecify`
compile dependency and SHALL generate nothing.

#### Scenario: Missing JSpecify is diagnosed clearly
- **WHEN** a compilation runs the processor without `org.jspecify:jspecify` on the compile
  classpath
- **THEN** exactly one `ERROR` diagnostic is emitted naming the missing dependency
- **AND** no `package-info` source file is generated
- **AND** no `cannot find symbol` error is reported against a generated file

### Requirement: Gradle incremental annotation processing
The published jar SHALL contain
`META-INF/gradle/incremental.annotation.processors` declaring the processor as `AGGREGATING`, and
the processor SHALL pass the originating `TypeElement`s of a package to
`Filer.createSourceFile`.

#### Scenario: Incremental declaration ships in the jar
- **WHEN** the published jar is inspected
- **THEN** it contains `META-INF/gradle/incremental.annotation.processors`
- **AND** the entry names the processor class followed by `AGGREGATING`

#### Scenario: Originating elements are declared
- **WHEN** `com.example.package-info` is generated for a package containing `Foo` and `Bar`
- **THEN** `createSourceFile` is called with the `TypeElement`s for `Foo` and `Bar` as originating
  elements

### Requirement: Interoperation with NullAway
A `package-info.java` generated during an annotation processing round SHALL cause NullAway, in
jspecify mode with `onlyNullMarked` enabled, to treat the package's types as null-marked, and
SHALL satisfy Error Prone's `RequireExplicitNullMarking` for those types.

#### Scenario: NullAway reports a violation in a generated-marked package
- **WHEN** a compilation with NullAway enabled contains a package with no hand-written
  `package-info.java` and a definite null-safety violation
- **THEN** NullAway reports the violation

#### Scenario: RequireExplicitNullMarking is satisfied
- **WHEN** a compilation with `RequireExplicitNullMarking` set to error contains a package with no
  hand-written `package-info.java`
- **THEN** the check does not fire for types in that package
