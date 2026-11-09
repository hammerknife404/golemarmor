# Iron Golem Armor: notes for the next agent

NeoForge 1.21.1 mod (Java 21, ModDevGradle) that makes vanilla iron and snow golems tougher: armor, immunities, creeper hunting, snow trails and special snowballs. Server-side; clients may join without it. All code is in `IronGolemArmor.java` (config spec, flat-to-sectioned config migration, event handlers). Player docs are in `docs/wiki/`, synced to the GitHub wiki; start at [Home](docs/wiki/Home.md).

## Working here

- **Build:** `./gradlew build`. CI (`build.yml`) runs the same on every PR and push to `main` and uploads the jar, versioned `<mod_version>-dev.<commit>` (`-dev` locally); only the Release workflow builds a plain `<mod_version>` jar.
- **PRs:** into `main`. Bump `mod_version` in `gradle.properties` with user-visible changes; merging the bump releases it. Keep `docs/wiki/` (the config tables in particular) and the README in step with behavior changes.
- **Release:** every push to `main` runs `release.yml`. If `v<mod_version>` isn't released yet, it builds with `RELEASE=true`, creates the tag on that commit and publishes the jar, with notes GitHub generates from the merged PRs (`.github/release-notes.md`, if present, goes above them); otherwise it stops after the check. To retry a failed release, run it by hand on `main` (GitHub MCP `actions_run_trigger`, `release.yml`, ref `main`). It never replaces an existing release. Sessions here can't push tags.
- **Shared CI:** `.github/workflows/` (`build.yml`, `release.yml`, `wiki.yml` where there's a wiki), `.github/dependabot.yml` and `.gitattributes` are identical in all the owner's mod repos (phantom_storage, golemarmor, friendlybeacons, catspells-nosumagro, gvs-noatk-players); phantom_storage's `CLAUDE.md` has the fullest notes. Change them in every repo at once, and keep repo-specific text out of them.
- **Modrinth:** the owner's rules: never publish anything to Modrinth (a version, the description, gallery images) without their consent, and no emoji in Modrinth text.
- **Wiki:** edit `docs/wiki/*.md` (page = file name, `_Sidebar.md` is the sidebar); `wiki.yml` mirrors it to the wiki on push to `main`. Never edit the wiki on github.com: the next sync overwrites it.
