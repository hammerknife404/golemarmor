# Iron Golem Armor

A small NeoForge mod for Minecraft 1.21.1 that gives vanilla Iron Golems configurable armor, armor toughness, bonus max health and immunities to environmental hazards that shouldn't hurt a block of iron.

## Features

- Armor, armor toughness and optional bonus health for every Iron Golem, or only player-built ones.
- Toggleable immunities to poison, lingering fire, magma blocks, plants and powder snow. Golems already ignore drowning and fall damage in vanilla.
- Works on existing worlds, and config changes apply live.
- Server-side only: players can join with or without the mod.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.257 or newer

## Installation

Download `golemarmor-<version>.jar` from the [Releases](https://github.com/hammerknife404/golemarmor/releases) page and put it in your client's `mods/` folder for singleplayer, or the server's for multiplayer.

## Configuration

The config file is `config/golemarmor-common.toml` and is created on first launch. You can also edit it in game from **Mods → Iron Golem Armor → Config**, but on a dedicated server only the server's copy counts.

| Option              | Default | Range      | Description                                                         |
|---------------------|---------|------------|---------------------------------------------------------------------|
| `armor`             | `15.0`  | 0 – 30     | Armor points added. 15 = full iron set.                             |
| `toughness`         | `8.0`   | 0 – 20     | Armor toughness added. 8 = full diamond set, 12 = full netherite.   |
| `bonusHealth`       | `0.0`   | 0 – 924    | Extra max health added to the vanilla 100 (vanilla caps at 1024).   |
| `playerCreatedOnly` | `false` | true/false | Only buff player-built golems; village golems stay vanilla.         |
| `poisonImmunity`    | `true`  | true/false | Golems can't be poisoned.                                           |
| `noLingeringFire`   | `true`  | true/false | Golems stop burning once out of fire/lava (still hurt while in it). |
| `magmaImmunity`     | `true`  | true/false | No damage from standing on magma blocks.                            |
| `plantImmunity`     | `true`  | true/false | No damage from cactus or sweet berry bushes.                        |
| `freezeImmunity`    | `true`  | true/false | Golems don't freeze in powder snow.                                 |

Saving the file while the game is running updates every loaded golem right away; golems in unloaded chunks update when they next load. Turning `playerCreatedOnly` on also strips buffs and immunities from golems that no longer qualify. Raising `bonusHealth` heals golems by the increase, and turning `poisonImmunity` on cures poisoned golems.

## Iron farms

All golems are buffed by default, including those villagers summon. Armor roughly doubles how long golems survive lava kill chambers with the default values, so more can be alive at once. Cramming ignores armor and is only slowed by `bonusHealth`, so keep that low on servers with many farms.

`noLingeringFire` breaks farms that rely on afterburn after a brief dip in lava or fire; farms where golems stay in contact with lava (such as head-height lava blades) still work. `magmaImmunity` breaks magma-floor kill chambers. Turn these off if your farm needs them.

## How it works

Armor, toughness and health are permanent attribute modifiers with fixed IDs, added when a golem joins the world. Reapplying one does nothing and a changed value replaces it. Base values aren't touched, because they're saved per entity and changes wouldn't reach existing golems.

The immunities are checked live through game events, and nothing is saved on the golem.

## Uninstalling

The immunities stop when the mod is removed, but the saved modifiers keep applying. To remove them first, set `armor`, `toughness` and `bonusHealth` to `0` (or `playerCreatedOnly` to `true` for village golems only) and load the affected areas once.

## Building

Requires JDK 21. If it isn't installed, Gradle downloads it automatically.

```sh
./gradlew build      # jar is written to build/libs/
./gradlew runClient  # launch a dev client with the mod
```

## Releasing

Bump `mod_version` in `gradle.properties` and merge to `main`. CI builds the jar and publishes a GitHub release tagged `v<version>` with notes generated from the merged PRs. Pushes that don't change the version don't create a release.

## License

[MIT](LICENSE)
