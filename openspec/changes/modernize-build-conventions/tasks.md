## 1. Repair the test stack

- [x] 1.1 Restore `testImplementation 'org.spockframework:spock-core'` and
      `testImplementation 'org.apache.groovy:groovy'` in `processor/build.gradle`
- [x] 1.2 Remove `testImplementation 'org.junit.jupiter:junit-jupiter'` from `processor/build.gradle`
- [x] 1.3 Confirm `org.mockito:mockito-junit-jupiter` and `org.junit.platform:junit-platform-launcher`
      remain `testRuntimeOnly` and that no test source imports `org.mockito` or
      `org.junit.jupiter.api`
- [x] 1.4 Confirm `dependencies/build.gradle` imports `mockito-bom` and `junit-bom` as platforms and
      no longer constrains `org.assertj:assertj-core`, `org.codenarc:CodeNarc` or
      `org.mockito:mockito-junit-jupiter`
- [x] 1.5 Verify `./gradlew :processor:compileTestGroovy` succeeds

## 2. Move Lombok into the convention plugin

- [x] 2.1 Remove `annotationProcessor 'org.projectlombok:lombok'` and
      `compileOnly 'org.projectlombok:lombok'` from `processor/build.gradle`
- [x] 2.2 Declare both in `conventions.gradle` inside the existing
      `pluginManager.withPlugin('java')` block, alongside `platform(project(':dependencies'))`
- [x] 2.3 Confirm the `org.projectlombok:lombok:1.18.46` constraint is in `dependencies/build.gradle`
- [x] 2.4 Confirm `lombok.config` at the repository root declares `config.stopBubbling`,
      `lombok.addLombokGeneratedAnnotation`, `lombok.addNullAnnotations = jspecify` and
      `lombok.experimental.flagUsage`
- [x] 2.5 Verify `./gradlew :dependencies:dependencies` shows no Lombok on the `java-platform` module

## 3. Confirm the PMD ruleset wiring

- [x] 3.1 Confirm `.pmd.xml` is deleted and `ruleSets = ['rulesets/java/joke-strict.xml']`
- [x] 3.2 Confirm `toolVersion` is not set and `net.sourceforge.pmd:pmd-dist:7.26.0` is the only PMD
      engine on the `pmd` configuration — check with `./gradlew :processor:dependencies --configuration pmd`
- [x] 3.3 Confirm the Central Portal snapshot repository in `conventions.gradle` declares
      `includeGroupAndSubgroups('io.github.joke')`
- [x] 3.4 Verify `./gradlew :processor:pmdMain` runs the shared rules and reports to the console
- [x] 3.5 Conform `NullMarkedProcessor` to `AvoidPrivateAndProtectedMethods`: change all 19
      `@VisibleForTesting` methods from `protected` to package-private
- [x] 3.6 Conform to `UseVarForLocalVariables`: change the 7 explicitly typed locals to `final var`,
      and drop the `javax.tools.JavaFileObject` import this makes unused
- [x] 3.7 Conform to `UseStaticImports`: static-import `Diagnostic.Kind.ERROR`,
      `Diagnostic.Kind.WARNING` and `SourceVersion.latestSupported`, dropping the now-unused
      `javax.tools.Diagnostic` import
- [x] 3.8 Verify `./gradlew :processor:pmdMain` reports zero violations and `:processor:test` still
      passes — Spock must still reach the now package-private seams

## 4. Verify the Lombok / NullAway / Pitest interaction

- [x] 4.1 Confirm `treatGeneratedAsUnannotated = true` is still set in the NullAway block
- [x] 4.2 Add a throwaway Lombok-annotated member to a scratch class, run
      `./gradlew :processor:compileJava`, and confirm it compiles under `-Werror` on the forked
      javac — if it fails on module access, record the required `--add-opens` in `jvmArgs` — then
      remove the scratch member
- [x] 4.3 Confirm `./gradlew :processor:pitest` still reports 100% mutation, coverage and test
      strength

## 5. Move the comments into the spec

- [x] 5.0 Delete the `configurations { annotationProcessor { resolutionStrategy {
      useGlobalDependencySubstitutionRules = false } } }` block from `processor/build.gradle` — a
      failed self-consumption experiment with no remaining purpose
- [x] 5.1 Confirm `processor/build.gradle` no longer carries the `// compileOnly, never api:` comment
- [x] 5.2 Confirm `package-info.java` imports `org.jspecify.annotations.NullMarked`, annotates the
      package with `@NullMarked`, and carries no bootstrap comment
- [x] 5.3 Delete `package-info.java` temporarily, run `./gradlew :processor:compileJava`, confirm it
      fails on `RequireExplicitNullMarking`, and restore it

## 6. Update project context

- [x] 6.1 Edit `openspec/config.yaml`: remove `AssertJ` from the testing line, add Mockito as Spock's
      mock maker, and change `no Dagger, no JavaPoet, no Lombok` to reflect that Lombok is a
      convention-plugin dependency the processor does not currently use
- [x] 6.2 Archive the completed `remove-dependency-check` change, whose design decision about the
      `// compileOnly, never api:` comment this change supersedes

## 7. Sync specs

- [x] 7.1 Apply the `build-foundation` delta to `openspec/specs/build-foundation/spec.md`

## 8. Verify

- [x] 8.1 Run `./gradlew check` and confirm it passes with no violations. NEVER continue if there
      are violations.
- [ ] 8.2 Commit with `/commit-commands:commit`
