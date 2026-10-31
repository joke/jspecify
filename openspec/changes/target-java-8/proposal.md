## Why

Consumers on Java 8 cannot use the processor today. The build targets Java 11 (`options.release = 11`),
so the processor's bytecode is Java 11, and the `@javax.annotation.processing.Generated` marker it writes
into every generated `package-info.java` does not exist before Java 9. A Java 8 consumer compiling at
`--release 8` gets an unresolved-symbol error on every generated file.

The library side is already Java 8: `org.jspecify:jspecify` 1.0.1, `org.jetbrains:annotations` 26.1.0 and
`com.google.auto.service:auto-service(-annotations)` 1.1.1 all ship class files at major version 52 (Java 8),
with only `module-info.class` at 53. Nothing the processor depends on stops it from targeting Java 8.

## What Changes

- The processor compiles with `options.release = 8` instead of `11`. Its own bytecode, and therefore the
  published jar, becomes Java 8.
- The processor emits the `@Generated` annotation that matches the consumer's source level, detected from
  `processingEnv.getSourceVersion()`: `javax.annotation.Generated` for source 8, and
  `javax.annotation.processing.Generated` for source 9 and later. Consumers on 8 receive a file that compiles
  under `--release 8`; consumers on 9 and later see no change.
- Processor source is rewritten to Java 8 language level and API: `var` is replaced with explicit types, and
  `Predicate.not` (Java 11) is replaced with an equivalent lambda. No behavior changes.
- The build JDK stays Liberica 25 (`.mise.toml` unchanged). Javac 21 and later report
  `source value 8 is obsolete` when `--release 8` is set, which `-Werror` turns into a failure, so
  `-Xlint:-options` is added to the compiler arguments for the processor's `JavaCompile` tasks.
- `build-foundation`: the "Language level and toolchain" requirement changes from Java 11 to Java 8.
- `package-info-generation`: the "Generated file content" requirement is split by consumer source level,
  and the test for the three-line form is updated.

**BREAKING** for nobody who consumes the processor from Java 9 or later: the generated content is unchanged
for them. Projects that compile the processor itself on a JDK older than 9 are not affected either, since
the build JDK is unchanged.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `build-foundation`: the required `options.release` moves from 11 to 8, and the compiler arguments gain
  `-Xlint:-options`.
- `package-info-generation`: the generated `@Generated` annotation depends on the consumer's source level.

## Impact

- `buildSrc/src/main/groovy/conventions.gradle`: `options.release` 11 → 8; `-Xlint:-options` added to the
  compiler arguments for the processor.
- `processor/src/main/java/io/github/joke/jspecify/processor/NullMarkedProcessor.java`: `content()` branches on
  source version; `var` and `Predicate.not` removed.
- `processor/src/test/groovy/.../NullMarkedProcessorSpec.groovy` and `NullMarkedProcessorCompilationSpec.groovy`:
  new cases for source 8 and source 9+, and the expectation for the default case.
- No change to the published coordinates, `.mise.toml`, the release workflow or the snapshot workflow.
