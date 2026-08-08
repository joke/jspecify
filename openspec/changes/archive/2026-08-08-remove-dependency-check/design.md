## Context

`processor/build.gradle` registers `verifyPomHasNoDependencies`, which depends on
`generatePomFileForMavenPublication`, parses `build/publications/maven/pom-default.xml` with
`groovy.xml.XmlSlurper`, and throws if any `<dependency>` element is present. `check` depends on it,
so every local `./gradlew build` generates a POM in order to re-assert a property that the module's
dependency configurations already guarantee.

The guarded property is real: the consuming project must supply its own `org.jspecify:jspecify`, so a
leaked `api`/`implementation` dependency would silently impose a version on every consumer. The
decision recorded here is that this property does not need a runtime guard.

## Goals / Non-Goals

**Goals:**

- Delete the task and its `check` wiring from `processor/build.gradle`.
- Leave the published artifact byte-identical.

**Non-Goals:**

- Any replacement enforcement. Not a Spock test over the generated POM, not a `withDependencies`
  assertion on the published configurations, not a CI step. This change is purely subtractive.
- Touching any other module, the convention plugin, or any CI workflow.
- Changing which dependencies the module declares or their configurations.

## Decisions

**Remove outright, with no replacement.** The alternatives were a Spock spec asserting over the
generated POM, or a cheap `withDependencies` guard on `api`/`implementation`/`runtimeOnly` that
avoids POM generation entirely. Both were rejected for the same reason the task itself is being
removed: they keep paying a cost — build logic to maintain, a failure mode to understand — to catch a
mistake that is a one-line configuration change, plainly visible in review, and visible again in the
published POM on Maven Central at the next release. The project's stated posture is a deliberately
tiny build. Subtracting the guard and adding nothing back is the point of the change, not a
regrettable side effect of it.

**Keep the surviving `compileOnly` comment as-is.** The `// compileOnly, never api: the consuming
project supplies its own JSpecify at a version it chooses. The published POM declares no
dependencies.` comment already sits on the JSpecify line, which is exactly where a configuration
change would be made. It needs no strengthening to carry the intent once the task is gone. The block
comment above the task is removed with the task.

## Risks / Trade-offs

- **A future edit changes `compileOnly` to `implementation` and the POM silently gains a dependency**
  → Accepted, knowingly. The surviving comment sits on the line such an edit would touch, and the
  leak surfaces in the published POM at the next release. This is the trade-off the change is making,
  not a gap to be patched.
- **`check` gets faster in a way that hides a POM-generation failure until publish time** → POM
  generation is exercised by the publish path in CI on every push to `main` (snapshot publishing), so
  a broken `generatePomFileForMavenPublication` still surfaces within one commit.

## Migration Plan

Not applicable — a build-internal task with no consumers. Rollback is `git revert` of a single
commit touching one file.

## Open Questions

None.
