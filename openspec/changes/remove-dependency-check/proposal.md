## Why

`processor/build.gradle` carries a hand-rolled `verifyPomHasNoDependencies` task that parses the
generated POM with `XmlSlurper` and fails `check` if any dependency is declared. The property it
guards is already structural: every dependency in the module is `annotationProcessor`, `compileOnly`,
`testImplementation` or `testRuntimeOnly`, and the `java`/`maven-publish` plugins emit none of those
scopes into the POM. The task is therefore twenty lines of build logic, an extra `check` →
`generatePomFileForMavenPublication` edge, and a hand-written XML parse that can never fire without
someone first changing a configuration in a way the diff makes obvious.

## What Changes

- Remove the `verifyPomHasNoDependencies` task registration from `processor/build.gradle`.
- Remove the `tasks.named('check') { dependsOn verifyPomHasNoDependencies }` wiring, so `check` no
  longer pulls in POM generation.
- Add no replacement — no test, no configuration guard, no CI step. The check is removed, not moved.
- No change to the published artifact: the POM still declares no dependencies.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `build-foundation`: the *Dependencies of the processor module* requirement keeps its guarantee that
  the published POM declares no dependencies and keeps both existing scenarios unchanged; one
  sentence is added recording that the guarantee follows from the dependency configurations rather
  than from a build-time verification task.

## Impact

- `processor/build.gradle` — task removed, `check` wiring removed.
- `check` no longer depends on `generatePomFileForMavenPublication`; POM generation now happens only
  as part of publishing.
- No production code, no test code, no CI workflow, and no published output is affected.
