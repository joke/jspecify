## 1. Settle the versioning mechanism

- [x] 1.1 Evaluate `com.javiersc.semver` 0.9.0's `mapVersion` hook: signature, on-tag signal and
      Groovy callability (resolved — `GradleVersion.commits` is null exactly on a tag)
- [x] 1.2 Superseded: `io.github.joke.conventional-version` 2.0.0 has been released and replaces the
      semver plugin. Record the switch and the reasoning in `proposal.md`, `design.md` and the delta
      spec, keeping the semver approach as the documented fallback

## 2. Remove Antora

- [x] 2.1 Delete the `./gradlew antora` step from `.github/workflows/build.yml`
- [x] 2.2 Delete the `docs` and `deploy` jobs from `.github/workflows/release.yml`, along with the
      `pages: write` / `id-token: write` permissions, the `github-pages` environment and the
      `concurrency` block that existed only for the Pages deployment
- [x] 2.3 Change the `publish` job's `needs` from `[release-please, deploy]` to `release-please`
- [x] 2.4 Remove `pages()` from the `github` block in `conventions.gradle`

## 3. Consolidate plugin declarations

- [x] 3.1 Add `info.solidsoft.pitest` to `buildSrc/build.gradle`
- [x] 3.2 Bump the NullAway plugin dependency in `buildSrc/build.gradle` from 3.0.0 to 3.1.0,
      settling the disagreement with `settings.gradle`
- [x] 3.3 Delete the `pluginManagement` block from `settings.gradle`, leaving only the settings
      plugin applications, `nmcpSettings`, `rootProject.name` and the includes
- [x] 3.4 Apply Pitest from `conventions.gradle` under `pluginManager.withPlugin('java')` and remove
      `id 'info.solidsoft.pitest'` from `processor/build.gradle`
- [x] 3.5 Verify `:dependencies` does not get Pitest (it is `java-platform`, so `withPlugin('java')`
      must not fire for it)

## 4. Switch versioning to conventional-version

- [x] 4.1 Delete the `isCleanTag` / `describeAlways` / `version = ...` block from `conventions.gradle`
- [x] 4.2 Remove the `com.javiersc.semver` application and its `semver { }` block from
      `conventions.gradle`, and drop `com.javiersc.semver:semver-gradle-plugin` from
      `buildSrc/build.gradle`
- [x] 4.3 Remove `semver.project.tagPrefix` from `gradle.properties`; confirm no version, stage,
      scope or tag-prefix property is declared anywhere
- [x] 4.4 Add `release-please-config.json` (single root package, `release-type: simple`) and an empty
      `.release-please-manifest.json` at the repository root — a recorded version must have a
      matching tag, and nothing has been released yet
- [x] 4.5 Drop the `release-type: simple` input from the release-please step in `release.yml` so the
      action reads the manifest configuration instead
- [x] 4.6 Apply `io.github.joke.conventional-version` 2.0.0 in the `settings.gradle` `plugins` block
- [x] 4.7 Verify the version resolves to `1.0.0-SNAPSHOT` on this untagged checkout and that
      `./gradlew :processor:properties` shows no `unspecified`
- [x] 4.8 Verify the configuration cache is reused across two consecutive `./gradlew help` runs

## 5. Fix the signing boundary

- [x] 5.1 Narrow the `signing.required` predicate in `conventions.gradle` so it matches
      `publishAggregationToCentralPortal` and not `publishAggregationToCentralSnapshots`
- [x] 5.2 Verify with `--dry-run` that the release task graph contains `publishAggregationToCentralPortal`
      (so signing is required) and the snapshot graph does not (so it is not) — the `Sign` task is
      present in both graphs either way, and is skipped when signing is not required

## 6. Wire snapshot publishing

- [x] 6.1 Add a snapshot publishing job to `.github/workflows/release.yml`, running
      `./gradlew publishAggregationToCentralSnapshots` with the existing
      `ORG_GRADLE_PROJECT_mavenCentralUsername` / `Password` secrets and no GPG step
- [x] 6.2 Gate it on `release-please` *not* having cut a release, so a release commit publishes only
      the release version
- [x] 6.3 Add `fetch-depth: 0` to the checkout in the snapshot job, the `publish` job and
      `build.yml`, since all three resolve the version
- [x] 6.4 Give the root project repositories via `dependencyResolutionManagement` in
      `settings.gradle`: nmcp resolves `com.gradleup.nmcp:nmcp-tasks` into a root-project
      configuration, and the root has no `build.gradle` and therefore no repositories, so both
      aggregation tasks failed to configure before this (pre-existing, not caused by the version
      switch)

## 7. Documentation

- [x] 7.1 Document snapshot consumption in `README.md`: the
      `https://central.sonatype.com/repository/maven-snapshots/` repository and the coordinate shape
      consumers should expect

## 8. Verify

- [x] 8.1 Run `./gradlew check` and confirm it passes with zero violations — do not continue if there
      are any
- [x] 8.2 Run `./gradlew publishAggregationToCentralSnapshots --dry-run` and confirm the task graph
      resolves without a GPG key
- [ ] 8.3 Sync the delta into `openspec/specs/build-foundation/spec.md` with `/opsx:sync`
- [ ] 8.4 Commit with `/commit-commands:commit`
