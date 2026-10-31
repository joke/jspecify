## Context

The build compiles every `JavaCompile` task with `options.release = 11` (`buildSrc/src/main/groovy/conventions.gradle`).
Only `processor` contains production Java; `dependencies` is a platform and has no classes. The processor is
`NullMarkedProcessor` (251 lines) plus a one-line `package-info.java`.

Its one Java 9+ dependency in the output is the `@javax.annotation.processing.Generated` marker that
`content()` writes into each generated file. The JDK's `--release 8` symbol set does not contain it, so a consumer
on Java 8 fails to compile the generated file. The earlier design (`bootstrap-nullmarked-processor`) chose
Java 11 for that reason and recorded the trade-off.

Verified before drafting:

- Class-file major versions: `jspecify-1.0.1.jar` classes are 52 (Java 8), with only `module-info.class` at 53.
  `annotations-26.1.0.jar` and `auto-service(-annotations)-1.1.1.jar` are also 52. The compile-only dependencies
  do not constrain the target.
- `javac --release 8 -Werror` on JDK 11 and JDK 17 compiles cleanly. On JDK 21, 24 and 25 it fails with
  `source value 8 is obsolete` (a `[options]` lint warning) because `-Werror` is set.
- Language level 8 rejects `var` (Java 10) and `Predicate.not` (Java 11 API) on any JDK. The processor uses
  `var` in 7 places and `Predicate.not` in one import and two calls.

## Goals / Non-Goals

**Goals:**
- The published processor jar is Java 8 bytecode.
- Consumers compiling at `--release 8` get generated `package-info.java` files that compile.
- Consumers at source 9 and later see exactly the output they get today.
- The build keeps running on the pinned JDK 25 with no toolchain change.

**Non-Goals:**
- Java 8 *runtime* testing of the Gradle build. Tests run on JDK 25 as now.
- Changing the generated content for source 9+, the round handling, or the diagnostics.
- Changing `dependencies`, the published coordinates, `.mise.toml` or the release/snapshot workflows.
- Making the Groovy test sources Java 8 bytecode. `options.release` applies only to `JavaCompile`.

## Decisions

### D1: Pick the `@Generated` annotation by the consumer's source level

The processor reads `processingEnv.getSourceVersion()`, which reflects the consumer's `-source`/`--release`.
Source `RELEASE_8` (or lower) gets `javax.annotation.Generated`, which exists in the Java 8 API. Source 9 and
later gets `javax.annotation.processing.Generated`, unchanged from today.

```mermaid
flowchart TD
    A[content for package pkg] --> B{processingEnv.getSourceVersion()}
    B -->|RELEASE_8 or lower| C["@javax.annotation.Generated(...)"]
    B -->|RELEASE_9 or higher| D["@javax.annotation.processing.Generated(...)"]
    C --> E[@org.jspecify.annotations.NullMarked]
    D --> E
    E --> F[package pkg;]
```

**Alternatives considered:**
- *Drop `@Generated` entirely.* Simplest, and it removes the only Java 9 symbol. Rejected because the design
  chose provenance the tooling can read, and a comment is not read by IDEs or coverage tools.
- *Always emit `javax.annotation.Generated`.* It is absent from JDK 11 and later as a supported platform
  class (`--release 11` and up do not include `javax.annotation`), so it breaks everyone else. Rejected.
- *Keep `javax.annotation.processing.Generated` and document the limitation.* Leaves Java 8 consumers
  broken. Rejected.

Under D1 the processor's `content()` method has one more branch. Its tests need cases for source 8 and source 9
and later, in addition to the existing expectation.

### D2: Keep the build JDK at 25 and suppress the obsolete-option lint

The alternative is to pin JDK 17 in `.mise.toml`, which also avoids the warning. Rejected for now: it changes
the JDK for the whole build (Gradle 9.6.1, Error Prone 2.49, Pitest, Spotless/Palantir, PMD), and none of those
are verified on 17 here. A single compiler argument, `-Xlint:-options`, applied to the processor's `JavaCompile`
task, suppresses only the option-obsolescence lint and keeps `-Werror` for everything else.

If the JDK pin is preferred later, the `-Xlint:-options` line can be removed with no other change to the
processor.

### D3: Rewrite `var` and `Predicate.not` by hand

The 7 `var` declarations get their inferred types written out, and the `not(...)` predicates become explicit
lambdas (`x -> !isUnnamed(x)` style). These are the only language-level changes, and they carry no behavior
change, so the existing tests cover them.

### D4: Keep the `latestSupported()` source version

`getSupportedSourceVersion()` returns `SourceVersion.latestSupported()` and is unchanged. The processor's
bytecode level does not affect which source levels it claims to support.

## Risks / Trade-offs

- [`processingEnv.getSourceVersion()` returns a value the consumer did not expect (e.g. `RELEASE_8` under
  `-source 8` on a JDK 11 build)] → Both `-source` and `--release` set it; a test with `RELEASE_8` mocked in
  `NullMarkedProcessorSpec` pins the branch.
- [A Java 8 consumer's `javax.annotation.Generated` lookup depends on the consumer's `ct.sym` for release 8] →
  This is the behavior JDK 8 itself exposes, and it is what the JDK 8 toolchain expects. Verify with a
  compile-testing case at source 8 on JDK 11 and JDK 17, the two JDKs installed locally that accept `--release 8`.
- [A future change re-introduces `var` or a Java 9+ API without noticing] → `--release 8` in the build rejects
  them at compile time; no extra guard is needed.
- [`-Xlint:-options` hides a real option problem later] → The suppression is narrow (options lint only) and is
  recorded in the build config next to `-Werror`.

## Migration Plan

Single release. Consumers on Java 9+ see no output change. Consumers on Java 8 get a working processor from the
release that contains this change; they need no configuration change. Rollback is reverting the build and
processor edits in one commit.

## Open Questions

- Do we have a concrete Java 8 consumer to validate against? If not, the source-8 path is tested only by
  compile-testing, not by an end-to-end build of a Java 8 project.
- Should the JDK pin move to 17 later (D2), so the suppression can go away?
