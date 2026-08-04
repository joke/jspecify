## Why

The project has never published anything — there are no tags — and it currently cannot publish a
snapshot even if it wanted to. The hand-rolled `git describe` versioning in the convention plugin
produces a distinct version string for every commit (`5f74f00-SNAPSHOT` today), so there is no
stable moving coordinate a consumer could depend on, and the Central Portal snapshot endpoint
rejects versions that do not end in `-SNAPSHOT`. Every change therefore has to wait for a full
release-please release before anyone can try it.

At the same time the build is wired for an Antora user manual that does not exist: `./gradlew antora`
is invoked from two workflows, there is no `antora` task, no `docs` module and no Antora
configuration anywhere in the repository. Both CI workflows fail on that step.

## What Changes

- Remove the Antora wiring entirely: the `antora` step in `build.yml`, the `docs` and `deploy` jobs
  in `release.yml`, the `pages: write` / `id-token: write` permissions, the `github-pages`
  environment, and the `pages()` entry in the `metadata` block that advertises a site URL which will
  never exist.
- **BREAKING (build-internal)**: replace the `git describe` version block in the convention plugin
  with `io.github.joke.conventional-version` 2.0.0, a settings plugin that predicts release-please's
  next version from the conventional commits since the last release and assigns it to every project.
  On a release commit the version is bare (`1.3.0`); between releases it is the *planned* next
  version with `-SNAPSHOT` (`1.4.0-SNAPSHOT` after a `feat:`). There is no stage flag, no version
  mapper and no tag prefix to configure — the policy comes from the same release-please
  configuration that cuts the releases, so the build and release-please cannot disagree.
- Migrate release-please from inline `release-type: simple` to **manifest mode**, adding
  `release-please-config.json` and `.release-please-manifest.json`. The version plugin reads both and
  fails the build if either is missing; manifest mode is also what makes the release configuration a
  file in the repository rather than an argument in a workflow.
- Publish snapshots to the Central Portal snapshot repository on every push to `main`, skipping
  commits where release-please cut a release. This uses `publishAggregationToCentralSnapshots`,
  already provided by the `com.gradleup.nmcp.settings` plugin the build applies today — no new
  plugin and no new credentials.
- Narrow the signing predicate so it no longer demands a GPG key for snapshot publishing. It
  currently matches any task name containing `Aggregation`, which includes
  `publishAggregationToCentralSnapshots`; Central does not require signatures on snapshots and the
  snapshot job has no key.
- Move the `pluginManagement { plugins { ... } }` version declarations out of `settings.gradle` into
  `buildSrc` dependencies applied from the convention plugin. Settings plugins are exempt and stay in
  `settings.gradle`: `com.gradleup.nmcp.settings`, whose job is applying nmcp across projects, and
  `io.github.joke.conventional-version`, which can only be applied there.
- Settle the NullAway plugin version, which `settings.gradle` and `buildSrc/build.gradle` currently
  disagree about (3.1.0 versus 3.0.0, silently resolved in buildSrc's favour).
- Add `fetch-depth: 0` to every checkout that computes a version. History-derived versioning cannot
  work from the default shallow clone, which `git describe --always` tolerated by falling back to a
  hash; the new plugin fails the build instead of guessing.

## Capabilities

### New Capabilities

None. Versioning and publishing are already within the stated purpose of `build-foundation`.

### Modified Capabilities

- `build-foundation`: the convention plugin requirement currently mandates "git-describe
  versioning"; versioning leaves the convention plugin entirely and becomes a settings-plugin
  concern with a defined release-commit/between-releases contract. The publishing requirement
  currently covers only releases to Maven Central; it gains snapshot publishing, the signing
  boundary between the two, and the requirement that plugin versions are declared in `buildSrc`
  rather than `settings.gradle`.

## Impact

- `buildSrc/src/main/groovy/conventions.gradle` — version block removed outright, signing predicate
  narrowed, `pages()` removed, pitest applied under `withPlugin('java')`.
- `buildSrc/build.gradle` — gains the pitest plugin dependency; NullAway version settled.
- `settings.gradle` — `pluginManagement` block removed; applies
  `io.github.joke.conventional-version` alongside `com.gradleup.nmcp.settings`, and otherwise retains
  only `nmcpSettings`, `rootProject.name` and the includes.
- `gradle.properties` — no versioning properties at all; the version needs no configuration.
- `release-please-config.json` and `.release-please-manifest.json` — new, the single source of both
  the release policy and the calculated version.
- `processor/build.gradle` — the explicit `id 'info.solidsoft.pitest'` application, if pitest moves
  into the convention plugin.
- `.github/workflows/build.yml` and `.github/workflows/release.yml` — Antora removal, snapshot job,
  checkout depth, release-please switched to manifest mode.
- No production code changes. The processor itself is untouched.
- Release flow is unchanged in ownership: release-please remains the sole author of tags, the
  CHANGELOG and the manifest, and the version plugin only reads what it produces.
