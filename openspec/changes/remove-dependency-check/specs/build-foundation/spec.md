## MODIFIED Requirements

### Requirement: Dependencies of the processor module
The `processor` module SHALL declare `com.google.auto.service:auto-service` as an
`annotationProcessor` dependency, and `com.google.auto.service:auto-service-annotations`,
`org.jetbrains:annotations` (for `@VisibleForTesting`) and `org.jspecify:jspecify` as `compileOnly`.
None of these SHALL appear in the published POM: the
consuming project supplies its own `org.jspecify:jspecify` at a version it chooses, and this
project SHALL NOT impose one transitively. It SHALL NOT depend on Dagger, JavaPoet, auto-common,
Guava, or JGraphT, and SHALL NOT be shaded.

The empty POM follows from the dependency configurations themselves — `annotationProcessor`,
`compileOnly`, `testImplementation` and `testRuntimeOnly` are not published scopes — and is not
enforced by a build-time verification task.

#### Scenario: The published artifact has no dependencies
- **WHEN** the published POM is inspected
- **THEN** it declares no `compile`- or `runtime`-scope dependency
- **AND** neither AutoService nor JSpecify appears in it

#### Scenario: No shading is configured
- **WHEN** `processor/build.gradle` is inspected
- **THEN** no `com.gradleup.shadow` plugin is applied
