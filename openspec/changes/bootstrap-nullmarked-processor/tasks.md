## 1. Gating spike — do NOT proceed past this section until both answers are recorded

- [x] 1.1 Build a throwaway javac harness: one source file in a package with no
      `package-info.java`, containing a definite null-safety violation; a trivial processor that
      generates `@NullMarked package-info` for it in round 0; Error Prone + NullAway on the
      processor path with `jspecifyMode`, `onlyNullMarked` and `RequireExplicitNullMarking=error`
- [x] 1.2 Record whether NullAway reports the violation (proves round-generated marking is honoured
      during flow analysis) and whether `RequireExplicitNullMarking` stays silent
      — **PREMISE HOLDS.** With the processor: NullAway reports the violation and
      `RequireExplicitNullMarking` is silent. Control arm (processor disabled):
      `RequireExplicitNullMarking` errors, NullAway silent. Clean variant builds green under
      `-Werror` (no last-round warning), and `package-info.class` carries
      `RuntimeVisibleAnnotations: org.jspecify.annotations.NullMarked`. Results recorded in design.md
- [x] 1.3 Second spike: a JPMS compilation whose `module-info.java` carries `@NullMarked` and whose
      packages have no `package-info.java` — record whether NullAway treats those types as marked
      — **YES**, the violation is reported with no `package-info.java` present
- [x] 1.4 If 1.2 fails, STOP and revise the proposal — the premise does not hold. If 1.3 shows
      NullAway honours MODULE-level marking, add a "module already marked → generate nothing" rule
      to the discovery spec before implementing; if not, record that no special case is added
      — 1.2 passed, so implementation proceeds; 1.3 passed, so the module rule was added to
      `specs/package-info-generation/spec.md` (Requirement: Module-level null marking wins) and
      task 3.5a/4.2a added below

## 2. Repository scaffolding

- [x] 2.1 Copy and adapt from `../percolate`: `gradlew`, `gradlew.bat`, `gradle/`,
      `gradle.properties`, `.gitignore`, `.pre-commit-config.yaml`, `.pmd.xml`,
      `.codenarc.groovy`, `LICENSE`, `.mise.toml`
- [x] 2.2 Write `settings.gradle`: `pluginManagement` version pins for spotless, errorprone,
      nullaway, pitest, nmcp settings and gradle-metadata; `nmcpSettings` Central Portal block;
      `rootProject.name`; `include 'dependencies'`; `include 'processor'`
- [x] 2.3 Write `buildSrc/build.gradle` with the spotless, errorprone, nullaway and
      gradle-metadata plugin dependencies (no lombok)
- [x] 2.4 Write `buildSrc/src/main/groovy/conventions.gradle` adapted from
      `percolate.conventions`: group `io.github.joke.jspecify`, git-describe versioning,
      `options.release = 11`, `-Werror`, spotless/palantir, PMD, CodeNarc, errorprone+nullaway,
      test/integrationTest split, pitest at 100/100/100, publishing/signing/metadata. Drop the
      Lombok block and the percolate-specific `description`
- [x] 2.5 Write `dependencies/build.gradle` as a `java-platform` importing the groovy and spock
      BOMs and constraining auto-service, auto-service-annotations, jspecify, compile-testing,
      assertj, CodeNarc
- [x] 2.6 Delete the `.claude/skills/percolate-testing-conventions` directory carried over from the
      copy — the Spock conventions it encodes are percolate-specific
- [x] 2.7 Set `rootProject.name` so the metadata plugin's `github.repo` resolves to the real
      repository name; publication coordinates are `io.github.joke.jspecify:processor`
- [ ] 2.8 Write `README.md` covering: what the tool does, the `annotationProcessor` +
      `testAnnotationProcessor` wiring, the consumer's own required `org.jspecify:jspecify` compile
      dependency, the "write the file yourself to override" escape hatch, and the recommendation to
      pair it with `RequireExplicitNullMarking`

## 3. The processor

- [x] 3.1 `processor/build.gradle`: `java`, `groovy`, `maven-publish`, `conventions`,
      `info.solidsoft.pitest`; auto-service as `annotationProcessor`, auto-service-annotations and
      jspecify as `compileOnly`; test deps from the platform
- [x] 3.1a Assert in a test that the generated POM declares no dependencies at all — neither
      AutoService nor JSpecify may leak into it
- [x] 3.2 Hand-write `processor/src/main/java/.../package-info.java` for the processor's own
      packages — this project cannot bootstrap itself
- [x] 3.3 Implement the processor: `AbstractProcessor`, `@AutoService(Processor.class)`,
      `@SupportedAnnotationTypes("*")`, `SourceVersion.latestSupported()`, `process()` returning
      `false`
- [x] 3.4 Implement the JSpecify availability check — single `ERROR`, generate nothing
- [x] 3.5 Implement three-probe discovery: root `PackageElement`s, classpath
      `getTypeElement(pkg + ".package-info")`, and the already-emitted `Set<String>`
- [x] 3.5a Implement the module check: skip every package whose enclosing `ModuleElement` carries
      `@NullMarked`, detected via annotation mirrors by qualified name (not
      `getAnnotation(NullMarked.class)`, which would need JSpecify at processor runtime)
- [x] 3.6 Implement the unnamed-package `WARNING` and skip
- [x] 3.7 Implement emission: eager per round, nothing when `processingOver()`, originating
      elements passed to `createSourceFile`, `FilerException` caught and treated as "already
      exists", content built by string concatenation
- [x] 3.8 Add `processor/src/main/resources/META-INF/gradle/incremental.annotation.processors`
      declaring the processor `AGGREGATING`

## 4. Tests (Spock specifications wrapping compile-testing)

- [x] 4.1 Compile-testing cases for discovery: generated for a bare package; skipped for a
      hand-written `package-info`; skipped when the hand-written file is `@NullUnmarked` or
      annotation-free; one file for many types; nested packages each generated
- [x] 4.2 Compile-testing case for the classpath probe (package-info supplied as a precompiled
      class rather than a source)
- [x] 4.2a Compile-testing cases for the module rule: a `@NullMarked module-info.java` suppresses
      all generation; an unmarked module and a compilation with no module generate as usual
- [x] 4.3 Compile-testing cases for content: exact generated text, no import, no `@Generated`
- [x] 4.4 Test that `process()` returns `false`, and an interop test with a second processor that
      still sees its annotations
- [x] 4.5 Test the unnamed-package warning and the missing-JSpecify error
- [x] 4.6 Round tests: nothing emitted when `processingOver()`; a package introduced by a second
      processor in round 1 is covered; no duplicate emission across rounds
- [x] 4.7 Compilation under `-Werror` is clean (guards the last-round warning regression)
- [x] 4.8 Integration test asserting `package-info.class` lands in the output carrying the
      `NullMarked` annotation, and that the jar contains the `incremental.annotation.processors`
      entry
- [x] 4.9 Integration test running NullAway over a generated-marked package and asserting the
      violation is reported (the automated form of spike 1.1)

## 5. Verify

- [x] 5.1 Run `./gradlew check` and confirm it is green including pitest at 100/100/100. NEVER
      continue if there are violations
- [x] 5.2 Confirm no generated `package-info.java` is tracked by git
- [x] 5.3 Dogfood check: point the built processor at a scratch copy of percolate with its 45
      boilerplate `package-info.java` files deleted, and confirm the build still passes and the
      one `@NullUnmarked` override is respected
      — **PASSED.** 42 files regenerated across the compiled modules; `annotations`, `spi`,
      `strategies-builtin`, `lib:javapoet`, `processor`, `reactor`, `reactor-blocking` main and
      test sources all compile green under NullAway with `RequireExplicitNullMarking` as an error;
      no file generated for the hand-written `@NullUnmarked` `docs.mapannotation` package. Control
      arm (files deleted, processor absent) fails as expected. Surfaced the vendored-module
      finding now recorded in design.md and the README

## 6. Spec sync

- [ ] 6.1 Run `openspec` sync for this change and confirm `package-info-generation` and
      `build-foundation` land in the main specs
      — NOTE: the installed CLI is 1.2.0, which has no `sync` command; spec sync is folded into
      `openspec archive`. `.mise.toml` pins 1.3.1, so run `mise install` first if the standalone
      sync step is wanted
- [ ] 6.2 Archive the change once `check` is green (left for review — `/opsx:archive`)
