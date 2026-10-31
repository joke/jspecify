## 1. Build configuration

- [ ] 1.1 Set `options.release = 8` in `buildSrc/src/main/groovy/conventions.gradle`
- [ ] 1.2 Add `-Xlint:-options` to the compiler arguments for the `processor` module's `JavaCompile` task only, leaving the other modules' arguments unchanged
- [ ] 1.3 Confirm `./gradlew :processor:compileJava` succeeds on JDK 25 with `-Werror`, and that `javap -v` reports major version 52 for the compiled `NullMarkedProcessor.class`

## 2. Language level in the processor

- [ ] 2.1 Replace the 7 `var` declarations in `NullMarkedProcessor.java` with explicit types
- [ ] 2.2 Replace `Predicate.not` (import and both call sites) with equivalent explicit lambdas
- [ ] 2.3 Confirm `./gradlew :processor:compileJava` still succeeds with `options.release = 8`

## 3. Generated annotation by consumer source level

- [ ] 3.1 Add a failing spec in `NullMarkedProcessorSpec.groovy` for `content()` at source `RELEASE_8` expecting `@javax.annotation.Generated(...)`
- [ ] 3.2 Add a failing spec for `content()` at source `RELEASE_9` (and `RELEASE_11`) expecting `@javax.annotation.processing.Generated(...)`, and keep the existing expectation
- [ ] 3.3 Implement the source-level branch in `NullMarkedProcessor.content()` using `processingEnv.getSourceVersion()`, keeping the method's Javadoc accurate
- [ ] 3.4 Make the new specs pass and keep the existing `content is the fully qualified form` spec green

## 4. End-to-end compile checks

- [ ] 4.1 Extend `NullMarkedProcessorCompilationSpec.groovy` with a compilation at `-source 8 --release 8` that generates a package and asserts it compiles without error
- [ ] 4.2 Extend the same spec with a compilation at the default source level (JDK 25) that asserts the `javax.annotation.processing.Generated` form still compiles
- [ ] 4.3 Run the Java 8 compile case under JDK 17 as well, to show the output compiles on a second JDK

## 5. Coverage and mutation

- [ ] 5.1 Confirm the new branch in `content()` is fully covered and that Pitest's 100% mutation, coverage and test-strength thresholds still hold for `processor`

## 6. Verification

- [ ] 6.1 Run `./gradlew check` and fix every violation (PMD, CodeNarc, Spotless, NullAway, Error Prone, Pitest). NEVER continue if there are violations
- [ ] 6.2 Commit the completed change with /commit-commands:commit
