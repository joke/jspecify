## 1. Remove the verification task

- [x] 1.1 Delete the `verifyPomHasNoDependencies` task registration and its preceding block comment
      from `processor/build.gradle`
- [x] 1.2 Delete the `tasks.named('check') { dependsOn verifyPomHasNoDependencies }` block from
      `processor/build.gradle`
- [x] 1.3 Confirm the `// compileOnly, never api: ...` comment on the JSpecify dependency is left
      unchanged, and that nothing else in the file is touched

## 2. Verify

- [x] 2.1 Run `./gradlew :processor:check` and confirm it passes and no longer runs
      `generatePomFileForMavenPublication`
- [x] 2.2 Run `./gradlew :processor:generatePomFileForMavenPublication` and confirm the generated
      `build/publications/maven/pom-default.xml` still contains no `<dependency>` element

## 3. Sync specs

- [x] 3.1 Apply the `build-foundation` delta to `openspec/specs/build-foundation/spec.md`
