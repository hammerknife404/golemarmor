# Iron Golem Armor

A small NeoForge mod for Minecraft 1.21.1 that gives vanilla Iron Golems configurable armor, armor toughness, bonus max health and immunities to environmental hazards that shouldn't hurt a block of iron.

## Features

- Adds armor and armor toughness to every Iron Golem (defaults: 15 armor, like a full iron set, and 8 toughness, like a full diamond set).
- Optional bonus max health on top of the vanilla 100.
- Optional mode that only buffs player-built golems and leaves village golems vanilla.
- Toggleable immunities: poison, lingering fire (golems stop burning once they step out of fire or lava), magma blocks, plants (cactus and sweet berry bushes) and freezing in powder snow. Golems already ignore drowning and fall damage in vanilla.
- Golems that already exist get updated when their chunk loads, so the mod works on existing worlds.
- Config changes apply live: saving the config file while a world is running updates every loaded golem.
- Runs on the server only. In multiplayer, players don't need the mod installed.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.257 or newer

## Installation

Download `golemarmor-<version>.jar` from the [Releases](https://github.com/hammerknife404/golemarmor/releases) page and put it in the `mods/` folder:

- **Singleplayer:** your client's `mods/` folder.
- **Multiplayer:** the server's `mods/` folder. Players can join with or without the mod.

## Configuration

The config file is `config/golemarmor-common.toml` and is created on first launch. In singleplayer you can also edit it in game from **Mods → Iron Golem Armor → Config**.

| Option              | Default | Range      | Description                                                        |
|---------------------|---------|------------|--------------------------------------------------------------------|
| `armor`             | `15.0`  | 0 – 30     | Armor points added. 15 = full iron set.                            |
| `toughness`         | `8.0`   | 0 – 20     | Armor toughness added. 8 = full diamond set, 12 = full netherite.  |
| `bonusHealth`       | `0.0`   | 0 – 924    | Extra max health added to the vanilla 100 (vanilla caps at 1024).  |
| `playerCreatedOnly` | `false` | true/false | If true, only player-built golems are buffed.                      |
| `poisonImmunity`    | `true`  | true/false | Golems can't be poisoned.                                          |
| `noLingeringFire`   | `true`  | true/false | Golems stop burning once out of fire/lava (still hurt while in it). |
| `magmaImmunity`     | `true`  | true/false | No damage from standing on magma blocks.                           |
| `plantImmunity`     | `true`  | true/false | No damage from cactus or sweet berry bushes.                       |
| `freezeImmunity`    | `true`  | true/false | Golems don't freeze in powder snow.                                |

Saving the file while the game is running applies changes right away to every loaded golem. Golems in unloaded chunks pick them up the next time they load. Turning `playerCreatedOnly` on also removes the buffs from golems that no longer qualify. When `bonusHealth` goes up, golems are healed by the amount it increased. Turning `poisonImmunity` on also cures golems that are already poisoned. The immunities follow `playerCreatedOnly` like the other buffs.

On a dedicated server, edit the server's copy of the file. The in-game config screen only changes your own local copy, which has no effect on a server you join.

## Server notes

Every iron golem is buffed by default, including golems that villagers summon, so iron farms are affected too. Armor reduces lava and fire damage, so golems in lava-based kill chambers take roughly twice as long to die with the default values, and more of them can be alive at once. Farms that kill with cramming are only slowed by `bonusHealth`, because cramming ignores armor. Keep `bonusHealth` low on servers with many iron farms.

`noLingeringFire` can break farms that dip golems into lava or fire briefly and let the afterburn kill them: the burn stops the moment they leave. Farms where golems stay in contact with lava (such as lava blades at head height) are unaffected. `magmaImmunity` breaks magma-floor kill chambers. Turn these off if your farm relies on them.

## How it works

**Armor, toughness and health:** when an Iron Golem joins the world, the mod adds permanent attribute modifiers with fixed IDs. Because the IDs are fixed, applying them again does nothing, and a changed config value replaces the old modifier. It doesn't change the golem's base attribute values. Base values are saved with each entity, so changing them would not reach golems that already exist.

**Immunities:** these are checked live, whenever a golem would take damage, get poisoned, keep burning or freeze. Nothing is saved on the golem.

## Uninstalling

The immunities stop as soon as the mod is removed. The armor, toughness and health modifiers are saved on each golem, though, and will keep applying after you uninstall. To remove them first, set `armor`, `toughness` and `bonusHealth` to `0` (or `playerCreatedOnly` to `true` for village golems only) and load the affected areas once before uninstalling.

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
