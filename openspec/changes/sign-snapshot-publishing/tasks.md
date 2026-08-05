## 1. Import the key in the snapshot job

- [x] 1.1 Add a `crazy-max/ghaction-import-gpg@v7` step to the `snapshot` job in `.github/workflows/release.yml`, between `jdx/mise-action@v4` and `gradle/actions/setup-gradle@v6`, with the same `gpg_private_key`, `passphrase` and `fingerprint` secrets the `publish` job uses
- [x] 1.2 Replace the `snapshot` job's comment: it currently states "No GPG step - Central does not require signatures on snapshots, and the signing predicate in conventions.gradle matches the release task alone". State instead that both jobs sign identically so every merge rehearses the release path
- [x] 1.3 Diff the two jobs and confirm they now differ only in checkout ref, task name and the GPG-free steps that are genuinely job-specific

## 2. Remove the dead predicate

- [x] 2.1 Delete the `required { gradle.taskGraph.allTasks.any { ... } }` line from the `signing` block in `buildSrc/src/main/groovy/conventions.gradle`, leaving `useGpgCmd()`
- [x] 2.2 Confirm no other build file references `gradle.taskGraph`

## 3. Verify locally

- [x] 3.1 Confirm `./gradlew publishAggregationToCentralSnapshots --dry-run` still configures and includes `:processor:signMavenPublication`
- [x] 3.2 Run `./gradlew check` and fix every violation before continuing
- [x] 3.3 Delete the stray untracked `out.asc` from the repo root if still present, and confirm it is not committed

## 4. Verify in CI

- [ ] 4.1 Push and watch the `release` workflow; confirm the `snapshot` job reaches `publishAggregationToCentralSnapshots` and the run is green
- [ ] 4.2 Confirm the Central snapshot repository accepted the upload including the `.asc` files. If it rejected signatures, stop and reopen the design decision rather than working around it

## 5. Land

- [ ] 5.1 Commit with `/commit-commands:commit`
