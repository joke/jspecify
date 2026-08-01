## Why

JSpecify has no way to say "this whole codebase is null-marked". `@NullMarked` targets a module,
a package, a type, a method or a constructor — but a non-modular project has no altitude above
`package` at which to state the rule once. The rule therefore has to be lowered by hand into one
`package-info.java` per package, which is boilerplate in the same sense that writing the Java
language version at the top of every source file would be boilerplate: a global build-level fact,
transcribed N times because the language offers nowhere else to put it.

The scale of that transcription is measurable. The sibling project `../percolate` carries 46
hand-written `package-info.java` files:

| Content | Count |
| --- | --- |
| `@org.jspecify.annotations.NullMarked` and nothing else | 42 |
| `@NullMarked` + import (stylistic variant, same meaning) | 3 |
| `@NullUnmarked` with a comment explaining the deliberate exception | 1 |

45 of 46 are pure lowering of a global rule. One carries genuine local intent. Every one of the 45
is a file a developer had to remember to create, and a package created without one is a silent
hole in the project's null-safety.

NullAway's `-XepOpt:NullAway:AnnotatedPackages` can express the global default as a build flag,
and for a closed application checked only by NullAway it is the better answer — no tool required.
It is not sufficient for a published library. The flag is a fact known only to one javac
invocation; it is not carried in the class files, so it is invisible to Error Prone's
`RequireExplicitNullMarking`, to IDE inspections, to Kotlin consumers deciding platform-type
versus definite-type, and to any downstream Java consumer running their own NullAway. A consumer
can restate `AnnotatedPackages=com.yourlib` in their own build, but that is an assertion about
code they do not control and cannot verify — if the library never actually added its `@Nullable`
annotations, NullAway will confidently conclude that nullable returns are non-null. Producer-side
markers in the class file are the only sound mechanism, and the only source of a
`package-info.class` is a `package-info.java`.

## What Changes

Create a new project containing one annotation processor that generates the `package-info.java`
files a JSpecify global default would otherwise require by hand.

- **A single policy, no configuration.** Every package discovered in the compilation gets
  `@org.jspecify.annotations.NullMarked` unless it already has a `package-info.java`. There is no
  include list, no exclude list, and no processor option. The escape hatch is writing the file
  yourself — which is local to the code, reviewable in the PR that needs it, survives renames,
  and can carry a justification comment, all of which a build-file exclusion list cannot. The
  percolate evidence (1 exception in 46) says exceptions are rare enough to deserve being
  explicit.
- **Output is generated, never committed.** Files land in the annotation processor output
  directory and are compiled into the jar, where the `@NullMarked` becomes part of the published
  API surface. Nothing enters git.
- **Deliberately tiny.** `AbstractProcessor` claiming `"*"`, AutoService for registration, and
  string concatenation for a two-line output file. No Dagger, no `BasicAnnotationProcessor` (its
  purpose is routing deferred elements by annotation type, meaningless when claiming `"*"`), no
  JavaPoet.
- **Gradle foundation copied from percolate**, reduced to three modules: `buildSrc` (convention
  plugin, renamed `conventions`), `dependencies` (version platform), and `processor`. Publishing
  to Maven Central as `io.github.joke.jspecify:processor` is in scope from the start. Tests stay
  on the house stack — Spock 2.4 on Groovy 5, with `compile-testing` as the harness underneath.
- **Zero published dependencies.** JSpecify is `compileOnly` here; the consuming project declares
  `org.jspecify:jspecify` itself, at a version it chooses. This project does not arbitrate that
  version by pushing one transitively.

## Capabilities

### New Capabilities

- `package-info-generation`: discovery of packages needing a `package-info.java`, the precedence
  rule that an existing file always wins, the content and placement of generated files, round
  handling, Gradle incremental-processing behaviour, and the diagnostics emitted for the cases
  the processor cannot handle.
- `build-foundation`: the Gradle module layout, convention plugin, versioning, static analysis
  and publishing configuration for the new repository.

### Modified Capabilities

None — greenfield project.

## Impact

- New repository at `jspecify-generator`; git and OpenSpec already initialised.
- Published artifact `io.github.joke.jspecify:processor`.
- Consumers wire the artifact into `annotationProcessor` and `testAnnotationProcessor`, and supply
  their own `org.jspecify:jspecify` compile dependency. The processor detects a missing JSpecify
  and diagnoses it rather than emitting a file that will not compile.
- Gating risk: the entire premise assumes a `package-info.java` generated during an annotation
  processing round is honoured by NullAway and `RequireExplicitNullMarking` when they analyse
  sources parsed in round 0. This is believed correct — all rounds complete before flow analysis
  begins — but is unverified. A spike settles it before any implementation work starts. If it
  fails, the project does not proceed in this form.
