## 1. Declare the plugin in buildSrc

- [x] 1.1 Add `implementation 'io.github.joke.conventional-version:io.github.joke.conventional-version.gradle.plugin:2.1.0'` to the `dependencies` block of `buildSrc/build.gradle`
- [x] 1.2 Rewrite the comment above that block: the rule is unchanged, but the settings-plugin exemption now names `com.gradleup.nmcp.settings` alone, and says why that one cannot move (it applies nmcp to every project and the aggregation plugin to the root)
- [x] 1.3 Run `./gradlew help` to confirm `buildSrc` still resolves

## 2. Apply the plugin from the convention plugin

- [x] 2.1 Add a `plugins { id 'io.github.joke.conventional-version' }` block at the top of `buildSrc/src/main/groovy/conventions.gradle`, with no version
- [x] 2.2 Replace the "No version is set here" comment with the ordering argument from `design.md`: the `plugins` block runs before the script body, and the script body is what creates the `MavenPublication` that captures `project.version`

## 3. Remove the plugin from settings

- [x] 3.1 Remove `id 'io.github.joke.conventional-version' version '2.0.0'` from the `plugins` block of `settings.gradle`
- [x] 3.2 Rewrite the file's opening comment: it currently justifies two settings plugins and argues the version must resolve before any project is configured. Keep the `nmcp.settings` justification, drop the plural framing, and point at the convention plugin for versioning

## 4. Verify

- [x] 4.1 Confirm `./gradlew :processor:properties :dependencies:properties` reports a derived version for both, not `unspecified`
- [x] 4.2 Confirm the root project reports `unspecified` and that this is the expected outcome, not a regression
- [x] 4.3 Run `./gradlew publishAggregationToCentralSnapshots --dry-run` and confirm it configures without error
- [x] 4.4 Confirm no plugin id appears with a version in both `settings.gradle` and `buildSrc/build.gradle`
- [x] 4.5 Run `./gradlew help` twice with `org.gradle.configuration-cache=true` and confirm the second run reuses the cache
- [x] 4.6 Run `./gradlew check` and fix every violation before continuing

## 5. Land

- [ ] 5.1 Commit with `/commit-commands:commit`
