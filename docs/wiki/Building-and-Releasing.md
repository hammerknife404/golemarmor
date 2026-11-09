# Building and Releasing

## Building

Requires JDK 21. If it isn't installed, Gradle downloads it automatically.

```sh
./gradlew build      # jar is written to build/libs/
./gradlew runClient  # launch a dev client with the mod
```

Builds are versioned `X.Y.Z-dev` locally and `X.Y.Z-dev.<commit>` in CI. Only the Release workflow builds a plain `X.Y.Z` jar, so a test build can't be mistaken for a release.

## CI

The Build workflow builds every push to `main` and every pull request, and uploads the jar as a workflow artifact.

## Releasing

Bump `mod_version` in `gradle.properties` and merge to `main`. The Release workflow then builds the jar and publishes a GitHub release tagged `v<version>`, with notes generated from the merged PRs. Pushes that don't change the version don't create a release, and an existing release is never replaced.

To retry a failed release, run the Release workflow by hand on `main` (Actions tab → Release → Run workflow). Pushing a `v<mod_version>` tag also releases.

## Wiki

These pages live in `docs/wiki/` in the repository and are copied to the wiki on every push to `main`. Edit them there: changes made on github.com are overwritten by the next sync.
