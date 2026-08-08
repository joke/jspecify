## Context

The build has three layers, and the change is really about pushing things down into the right one:

```
settings.gradle          nmcp aggregation, dependencyResolutionManagement, includes
      │
buildSrc/conventions     ── policy every module inherits ──▶ Spotless, PMD, Error Prone,
      │                                                      NullAway, Pitest, CodeNarc,
      │                                                      publishing, signing, metadata
dependencies (platform)  ── every external version ──▶ constraints + imported BOMs
      │
processor                ── what *this* module needs ──▶ AutoService, JSpecify, Spock
```

Three of the four things in this change were sitting one layer too low. The PMD ruleset was a file
in the repository root when it is really a house standard shared across `io.github.joke` projects.
Lombok was wired into `processor/build.gradle` when it is a convention. The test-stack requirement
described a stack the project never actually had.

The fourth is a documentation move: two source comments carried rationale that belongs in the spec.

## Goals / Non-Goals

**Goals:**

- Make `conventions` the single place that decides Lombok, PMD rules and the PMD engine version.
- Make the *Test stack* requirement describe the stack that exists.
- Version Mockito and JUnit through BOMs rather than by hand or by transitive accident.
- Preserve the empty published POM and the 100/100/100 Pitest thresholds exactly.

**Non-Goals:**

- Introducing any Lombok usage. No production or test source is rewritten to use `@Value`,
  `@RequiredArgsConstructor` or anything else. Lombok becomes *available*; whether the processor ever
  uses it is a separate decision.
- Migrating any test off Spock. The JUnit and Mockito BOMs are version management for what Spock
  already runs on, not a second test framework.
- Changing any PMD rule. Rule changes, if any, are a separate change against the rules project. This
  change adapts the code to the rules, never the reverse.
- Reviving `verifyPomHasNoDependencies` or any replacement for it.

## Decisions

**Lombok goes in `conventions`, not in `processor`.** Lombok is a house standard for every
`io.github.joke` project, in the same category as Spotless and Error Prone. Wiring it per-module
would mean every future module repeats two dependency lines and can silently forget one. It is
declared under `pluginManager.withPlugin('java')`, which already guards against applying it to the
`java-platform` module.

The consequence worth stating plainly: `processor` gains a Lombok dependency it does not use.
`NullMarkedProcessor` is one class with two fields, no constructor and no accessors — there is
nothing for Lombok to generate. That is not an argument against the convention, it is an argument
that this module is simply the first place the convention lands. The cost is one `compileOnly`
artifact on a classpath that is never published.

**`lombok.config` lives at the repository root with `config.stopBubbling = true`.** Lombok resolves
configuration by walking up the directory tree; stopping at the root keeps the build hermetic against
whatever sits above the checkout. The other three settings are chosen deliberately:

- `lombok.addLombokGeneratedAnnotation = true` puts `@lombok.Generated` on generated members. This is
  what makes the generated code invisible to both NullAway (`treatGeneratedAsUnannotated = true`,
  already set) and Pitest, which filters members carrying a `Generated` annotation with CLASS or
  RUNTIME retention. Without it, the 100% mutation and coverage thresholds would be measuring
  Lombok's output.
- `lombok.addNullAnnotations = jspecify` makes generated members carry JSpecify annotations rather
  than Lombok's own or none. This is the only setting that reaches the *meaning* of the code rather
  than its visibility, and it is the obviously right one for this project. It requires JSpecify on
  the compile classpath of any module that uses a Lombok feature emitting null annotations;
  `processor` already declares it `compileOnly`.
- `lombok.experimental.flagUsage = ALLOW` — experimental features are permitted rather than warned
  on. Under `-Werror` a warning is a build failure, so the alternative would be a hard prohibition.

**The snapshot PMD ruleset is accepted, temporarily and narrowly.** `io.github.joke.pmd:rules` is at
`0.1.0-SNAPSHOT` while the rule selection is still being evaluated against real code. A snapshot in
the lint gate means a green build can turn red with an unchanged tree, which is exactly the property
this project otherwise works hard to avoid.

Two things bound the exposure. The snapshot repository is declared with
`content { includeGroupAndSubgroups('io.github.joke') }`, so no other coordinate in the build can
resolve a snapshot from it — the failure mode is confined to the ruleset. And the spec carries an
explicit constraint that no release may be cut while the ruleset is a snapshot, so the exposure
cannot silently reach a published artifact.

The alternative — keeping `.pmd.xml` until the rules project has a release — was rejected because the
whole point of the evaluation period is to run the shared rules against a real codebase. Keeping the
local copy means evaluating nothing.

**The shared ruleset is a superset, and the code moves to meet it.** `joke-strict.xml` is
`.pmd.xml`'s stock-category composition plus every rule the artifact defines. `.pmd.xml` carried one
custom rule, `UseVarForLocalVariables`, written as an XPath expression matching `Type/@Image != 'var'`
— an attribute PMD 7 does not expose on that node, so the rule silently never fired. The shared
artifact reimplements it in Java, along with seven more. Adopting the artifact therefore turns on
eight rules, not zero, and `pmdMain` reports 29 violations in `NullMarkedProcessor`:

| Rule | Hits | Fix |
| --- | ---: | --- |
| `AvoidPrivateAndProtectedMethods` | 19 | `protected` → package-private |
| `UseVarForLocalVariables` | 7 | explicit type → `final var` |
| `UseStaticImports` | 3 | static-import `ERROR`, `WARNING`, `latestSupported` |

The 19 decide the shape of the change. Every one is a `@VisibleForTesting` method that exists as a
seam for `NullMarkedProcessorSpec` to `Spy()` and stub. The ruleset also ships
`UseVisibleForTestingAnnotation`, which fires zero times here — so the house idiom the rules encode is
*package-private plus `@VisibleForTesting`*, and this class is already annotated correctly and merely
over-visible. `protected` on a class nothing subclasses buys nothing that package-private does not,
and the Spock specs share the package, so the seam is unaffected.

The alternative — layering a project-local ruleset that references `joke-strict.xml` and excludes the
three rules — was rejected. The artifact's own documentation anticipates that route, but taking it
would reintroduce the local ruleset file this change exists to delete, and would do so to preserve a
visibility choice with no defender. The code is the outlier, not the rules.

This is the evaluation the snapshot dependency exists for, and the result is that the rules were
right. Had the verdict gone the other way, the correct response would have been a change against the
rules project, not a suppression here.

**PMD engine as an explicit `pmd-dist` dependency rather than `toolVersion`.** The `pmd` configuration
now has to carry a second artifact (the rules), so it is being populated by hand regardless. Setting
`toolVersion` *and* adding dependencies to the same configuration invites two PMD versions on one
classpath. One mechanism, stated once.

**Spock exclusively, with Mockito as the mock maker.** This is not a change; it is the spec catching
up to `SpockConfig.groovy`, which has set `preferredMockMaker spock.mock.MockMakers.mockito` from the
start. Spock 2.4 supports delegating mock creation to Mockito, which is why `mockito-junit-jupiter`
is `testRuntimeOnly` — no test source references the Mockito API, so it must not be
`testImplementation`.

`org.junit.jupiter:junit-jupiter` on `testImplementation` is therefore wrong and is removed. Spock is
the engine; the JUnit *API* has no consumer. `junit-platform-launcher` stays `testRuntimeOnly`, now
versioned by `junit-bom` rather than resolving to whatever Spock happens to drag in.

**AssertJ and CodeNarc constraints are deleted as corrections, not as changes.** AssertJ has zero
references in `processor/src/test`; the *Test stack* requirement asserting otherwise was wrong from
the day it was written. The CodeNarc constraint was dead because `conventions.gradle` declares
`org.codenarc:CodeNarc:3.7.0-groovy-4.0` literally on the `codenarc` configuration, and a constraint
cannot influence a coordinate that already carries a version.

That literal version is left alone here, though it is worth noting for a future change that it is a
`-groovy-4.0` build running against Groovy 5, and that it sits outside the platform in tension with
the *All external versions live in the platform* scenario. Same for `pmd-dist`, `error_prone_core`,
`nullaway`, `palantirJavaFormat` and `pitestVersion` — the convention plugin has always declared tool
versions literally, because tool configurations are created by plugins the platform cannot see.

**Self-consumption was tried and abandoned; the substitution opt-out goes with it.**
`processor/build.gradle` carries a `configurations { annotationProcessor { resolutionStrategy {
useGlobalDependencySubstitutionRules = false } } }` block. It exists because of an experiment: put
the processor's own *released* version on its own `annotationProcessor` path, so that the module's
`package-info.java` would be generated by the previous release rather than hand-written, and the
project would eat its own output.

It failed, and the reason is structural rather than a matter of configuration. `:processor` publishes
exactly `io.github.joke.jspecify:processor`, so when that coordinate appears anywhere in the same
build, Gradle's project-vs-module conflict resolution prefers the local project and substitutes the
release away:

```
annotationProcessor 'io.github.joke.jspecify:processor:<released>'
        |
        v   Gradle: "this build produces those coordinates"
project(':processor')      <- the module being compiled, not the release
```

`useGlobalDependencySubstitutionRules = false` cannot prevent this. That flag governs
*included-build* substitution in a composite, and there is no included build here. It was the wrong
knob for the right problem, and it is removed.

The attempt SHALL NOT be repeated, and not only because the mechanism blocks it. Even if the
substitution could be defeated, the trade is bad for this project:

- the payoff is deleting one three-line file that is trivially correct and never changes;
- `:processor`'s own compilation would start depending on Maven Central resolving a previous release,
  turning a self-contained build into a networked one;
- a new major version has no predecessor to bootstrap from;
- the file would be generated by the *previous* release, so the project would permanently be one
  version behind its own behaviour — self-consumption that exercises the wrong artifact.

Dogfooding, which was the real motivation, is already served better by
`NullMarkedProcessorCompilationSpec`: it runs the freshly built processor over fixture sources through
`compile-testing`, exercising the code under compilation rather than the last release of it.

The consequence is that the *The processor's own package-info* requirement is not a temporary
accommodation awaiting a fix. Hand-writing the file is the settled answer.

**Both source comments move into the spec.** The `// compileOnly, never api:` comment is removed even
though `remove-dependency-check`'s design explicitly decided to keep it. That decision was made in a
context where the comment was the *only* surviving record of the constraint; it is now written into
the *Dependencies of the processor module* requirement, which a reviewer of a scope change is
expected to consult. This change supersedes that decision knowingly rather than by accident.

The `package-info.java` bootstrap comment is removed on the same reasoning, but it costs more: the
fact it recorded — that the processor cannot generate its own `package-info` because it is the thing
being compiled — appears in *neither* spec today. So the delta adds a requirement for it before the
comment goes. The `@NullMarked` annotation moves to an imported form, which is legal in a
`package-info.java` (imports follow the package declaration) and matches how the rest of the codebase
is written.

## Risks / Trade-offs

- **A snapshot ruleset makes the build non-reproducible** → Bounded by the repository content filter
  and by the release constraint in the spec. Exit condition: publish `io.github.joke.pmd:rules` as a
  release and pin it. Until then, a surprise PMD failure on an unchanged tree is a known and accepted
  outcome.
- **Spock spies on package-private methods across source sets** → Groovy's dispatch to
  package-private Java members is historically less reliable than to `protected` ones, and
  `NullMarkedProcessorSpec` stubs all 19. The specs share the package, so access is legal; if Groovy
  cannot reach them the test run says so immediately, and Pitest at 100/100/100 catches any seam that
  silently stops being exercised.
- **Lombok's generated code is invisible to NullAway** → This is the deliberate effect of
  `treatGeneratedAsUnannotated = true` combined with `addLombokGeneratedAnnotation = true`. Null
  errors inside Lombok-generated members will not be caught. Accepted: Lombok's generators are not
  where this project's null bugs will come from, and the alternative — turning off
  `treatGeneratedAsUnannotated` — would also expose the processor's *own* generated output to
  NullAway, which is precisely what that flag exists to prevent.
- **Lombok on a forked javac under a modern JDK may need `--add-opens`** → `conventions.gradle` sets
  `options.fork = true` with explicit `jvmArgs`. Lombok manipulates the compiler's internal AST and
  on recent JDKs can require `jdk.compiler` module opens. Nothing here uses Lombok yet, so the
  failure would not surface until the first `@Getter` is written. Verification task 4.2 forces the
  issue now rather than later.
- **Pitest thresholds are 100/100/100 and the test source set is being repaired, not rewritten** →
  Restoring `spock-core` and `groovy` returns the tree to a compiling state with the same tests. If
  `./gradlew check` passes, coverage is unchanged by construction.
- **Self-consumption is closed, not deferred.** Gradle substitutes the released coordinate back to
  `project(':processor')` and offers no configuration-level opt-out, and the trade would be poor even
  if it did. `package-info.java` stays hand-written permanently. Reopening this means changing the
  *The processor's own package-info* requirement first.

## Migration Plan

Not applicable — build-internal only. No consumer of the published artifact observes anything. The
`processor` module's test sources do not compile in the intermediate state (Spock removed but the
specs still present), so tasks 1 and 3 should land in the same commit.

Rollback is `git revert`, plus restoring `.pmd.xml`.

## Open Questions

- When `io.github.joke.pmd:rules` reaches a release version, does the snapshot repository declaration
  come out of `conventions.gradle` entirely, or stay for future rule iteration? Leaving it costs a
  repository lookup for `io.github.joke` coordinates on every resolution; removing it means adding it
  back for the next evaluation cycle.
- ~~Does the shared ruleset need a per-project suppression mechanism?~~ Answered by this change: no.
  The first real conflict — 29 violations — was resolved by changing the code, and the option to
  layer a local ruleset was considered and rejected. The precedent is that projects conform.
