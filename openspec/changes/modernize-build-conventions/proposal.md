## Why

Four unrelated pressures landed on the build at the same time, and all four are about the
*convention plugin* rather than about the processor:

1. **The PMD ruleset is not project-specific.** `.pmd.xml` is 79 lines of rule selection and one
   custom XPath rule (`UseVarForLocalVariables`) that says nothing about this project. Every
   `io.github.joke` project wants the same rules, and today each would carry its own copy that
   drifts. The ruleset has been extracted into a shared artifact, `io.github.joke.pmd:rules`, that
   ships `rulesets/java/joke-strict.xml`.
2. **Lombok is a house convention, not a module choice.** It belongs in the convention plugin so
   that every module that applies `conventions` gets it, the same way Spotless, Error Prone and
   NullAway already arrive. The `processor` module happens to have nothing for Lombok to generate
   today — one class, two fields, no constructors or accessors — and that is fine: the convention is
   about what every module *may* use, not what this one does.
3. **The test-stack requirement was never true.** It names AssertJ "for value assertions", but there
   is not a single AssertJ reference in `processor/src/test`. The assertions are Truth's, reached
   through `com.google.testing.compile.CompilationSubject.assertThat`. Mocking is Spock's, backed by
   Mockito — `SpockConfig.groovy` has set `preferredMockMaker spock.mock.MockMakers.mockito` since
   the project was bootstrapped, which is why `mockito-junit-jupiter` is `testRuntimeOnly`. The
   platform never versioned Mockito or JUnit properly; it pinned one Mockito artifact by hand and
   left `junit-platform-launcher` unconstrained.
4. **The processor's own `package-info.java` carried its rationale in a comment.** The comment is
   the only record anywhere that this project cannot bootstrap itself. It has to move into the spec
   before it moves out of the file.

## What Changes

**Shared PMD ruleset**

- Delete `.pmd.xml`.
- Resolve the ruleset from `io.github.joke.pmd:rules` on the `pmd` configuration, selected as
  `rulesets/java/joke-strict.xml`.
- Declare the PMD engine explicitly as `net.sourceforge.pmd:pmd-dist:7.26.0` on the `pmd`
  configuration instead of via `toolVersion`.
- Add the Central Portal snapshot repository to the convention plugin's `repositories`, restricted
  to `io.github.joke` and its subgroups, so nothing else in the build can resolve a snapshot.
- Set `consoleOutput = true` so violations are readable without opening a report.
- Conform `NullMarkedProcessor` to the shared rules. `joke-strict.xml` is a superset of `.pmd.xml`:
  it adds eight Java-implemented custom rules where the local file had one XPath rule that never
  fired. Three of them report 29 violations — 19 `AvoidPrivateAndProtectedMethods`, 7
  `UseVarForLocalVariables`, 3 `UseStaticImports` — fixed by making the `@VisibleForTesting` methods
  package-private rather than `protected`, using `final var` for local variables, and static-importing
  `ERROR`, `WARNING` and `latestSupported`.

**Lombok as a convention**

- Add `lombok.config` at the repository root: `config.stopBubbling`,
  `lombok.addLombokGeneratedAnnotation`, `lombok.addNullAnnotations = jspecify`,
  `lombok.experimental.flagUsage = ALLOW`.
- Wire Lombok in `conventions.gradle` under `pluginManager.withPlugin('java')` as
  `annotationProcessor` + `compileOnly`, **not** in `processor/build.gradle`.
- Constrain `org.projectlombok:lombok:1.18.46` in the `dependencies` platform.

**Test stack corrected**

- Keep Spock 2.4 on Groovy 5 as the *only* test framework. Restore `org.spockframework:spock-core`
  and `org.apache.groovy:groovy` to `processor`'s `testImplementation`; they were removed in error.
- Remove `org.junit.jupiter:junit-jupiter` from `testImplementation`. No test is written against the
  JUnit API; Spock supplies the engine and `junit-platform-launcher` stays `testRuntimeOnly`.
- Drop the `org.assertj:assertj-core` constraint. It is unused and always was.
- Replace the hand-pinned `org.mockito:mockito-junit-jupiter` constraint with `mockito-bom`, and add
  `junit-bom`, so the mock maker and the platform launcher are versioned by a BOM rather than by
  accident.
- Drop the `org.codenarc:CodeNarc` constraint from the platform. It was dead — `conventions.gradle`
  declares the CodeNarc version literally on the `codenarc` configuration.

**The processor's own package-info**

- Remove the `configurations { annotationProcessor { resolutionStrategy {
  useGlobalDependencySubstitutionRules = false } } }` block from `processor/build.gradle`. It was an
  experiment in self-consumption — putting the processor's own released version on its own
  annotation-processor path so that `package-info.java` would be generated rather than hand-written —
  and it did not work. Nothing depends on it.
- Import `org.jspecify.annotations.NullMarked` rather than annotating with the fully qualified name.
- Move the "cannot bootstrap itself" rationale from a source comment into `build-foundation`.
- Likewise, remove the `// compileOnly, never api:` comment from `processor/build.gradle`,
  deliberately superseding the decision recorded in `remove-dependency-check`'s design: the
  rationale now lives in the *Dependencies of the processor module* requirement, which is where a
  reviewer of a scope change should be looking.

## Capabilities

### New Capabilities

None. Every change is to `build-foundation`.

### Modified Capabilities

- `build-foundation`:
  - *Convention plugin* — the sentence "The Lombok configuration SHALL NOT be carried over" is
    removed; Lombok joins the list of what the plugin carries.
  - *Test stack* — rewritten: Spock exclusively, Mockito as Spock's mock maker, Truth via
    compile-testing, no AssertJ, no JUnit-API tests.
  - *Dependencies of the processor module* — records that the convention plugin contributes Lombok
    on unpublished scopes, and that the empty-POM rationale lives in the spec rather than in a source
    comment.
  - *Static analysis ruleset* (new requirement) — the shared `io.github.joke.pmd:rules` artifact, the
    restricted snapshot repository, and the constraint that no release may be cut against a snapshot
    ruleset.
  - *Lombok* (new requirement) — repository-wide `lombok.config`, convention-plugin wiring, and the
    interaction with NullAway's `treatGeneratedAsUnannotated` and with Pitest's 100% thresholds.
  - *The processor's own package-info* (new requirement) — why it is hand-written.

## Impact

- `.pmd.xml` — deleted.
- `lombok.config` — added.
- `buildSrc/src/main/groovy/conventions.gradle` — snapshot repository, PMD configuration, Lombok
  wiring.
- `dependencies/build.gradle` — `mockito-bom` and `junit-bom` added; AssertJ, CodeNarc and
  `mockito-junit-jupiter` constraints removed; Lombok constraint added.
- `processor/build.gradle` — Lombok removed (it comes from `conventions`), Spock and Groovy restored,
  `junit-jupiter` removed, comment removed, `configurations { annotationProcessor { ... } }` block
  removed.
- `processor/src/main/java/.../package-info.java` — import form, comment removed.
- `openspec/config.yaml` — the `context` block still says "AssertJ" and "no Lombok"; both are now
  wrong.
- `processor/src/main/java/.../NullMarkedProcessor.java` — 19 methods change from `protected` to
  package-private, 7 locals become `final var`, 3 constants become static imports.
- No production behaviour changes. Every edit to `NullMarkedProcessor` is a visibility, inference or
  import change; no logic is touched, and the Spock specs share the package so the testing seam is
  intact. The published artifact and its POM are unaffected: Lombok arrives on `compileOnly` and
  `annotationProcessor`, neither of which is a published scope.
- **Release is blocked** until `io.github.joke.pmd:rules` is published as a release version. The
  build currently depends on `0.1.0-SNAPSHOT`.
