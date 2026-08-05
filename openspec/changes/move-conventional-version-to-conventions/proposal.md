## Why

`io.github.joke.conventional-version` 2.0.0 could only be applied in `settings.gradle`, which forced
its version to live there instead of in `buildSrc/build.gradle` with every other plugin version — the
exact split the build otherwise works hard to avoid. Version 2.1.0 dispatches on what it is applied
to and can be applied to a project, so it can now live in the `conventions` plugin and the exception
can go away.

## What Changes

- The `conventions` plugin applies `io.github.joke.conventional-version` in its `plugins` block, so
  every module applying `conventions` is versioned by it.
- `io.github.joke.conventional-version` 2.1.0 is declared as a plugin-marker dependency in
  `buildSrc/build.gradle`, alongside every other project plugin version.
- `settings.gradle` drops the plugin and its version, leaving `com.gradleup.nmcp.settings` as the
  only settings plugin and the only version declared outside `buildSrc/build.gradle`.
- The root project is no longer versioned and keeps Gradle's default `unspecified`. This is
  deliberate and inert: it has no `build.gradle`, no publication, and nmcp never reads
  `Project.getVersion()` — the Central deployment name is derived from the coordinates inside the
  aggregation bundle, which come from `:processor` and `:dependencies`.
- The comments that justify the current arrangement are rewritten rather than deleted: the ordering
  constraint they describe is still real, but is now satisfied because `conventions` is what creates
  the `MavenPublication`, and a `plugins` block runs before the script body that creates it.

Not a breaking change — nothing published changes coordinates or content.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `build-foundation`: three requirements change.
  - **Convention plugin**: versioning becomes the convention plugin's concern. It still SHALL NOT
    invoke `git describe` or assign `version` itself, but it SHALL apply the plugin that does.
  - **Version derivation**: the plugin is applied by the `conventions` plugin at project level
    rather than in `settings.gradle`, and the guarantee weakens from "every project in the build" to
    "every project applying `conventions`".
  - **Plugin version declaration**: the settings-plugin exemption narrows from two plugins to
    `com.gradleup.nmcp.settings` alone.

## Impact

- `buildSrc/build.gradle` — new plugin-marker dependency, revised comment on the exemption.
- `buildSrc/src/main/groovy/conventions.gradle` — new `plugins` block, revised comment where it
  currently states that the version comes from `settings.gradle`.
- `settings.gradle` — plugin removed from the `plugins` block, its justifying comment rewritten.
- No change to `processor`, `dependencies`, the workflows, `release-please-config.json` or
  `.release-please-manifest.json`. Published coordinates are unchanged.
- Risk accepted: a future module that forgets `id 'conventions'` publishes `unspecified` instead of
  failing, where the settings plugin would have caught it. Such a module would also be missing
  signing, publishing metadata and NullAway, so it fails louder elsewhere first.
