# Iron Golem Armor

A small NeoForge mod for Minecraft 1.21.1 that gives vanilla Iron Golems configurable armor, armor toughness and bonus max health.

## Features

- Adds armor and armor toughness to every Iron Golem (defaults: 15 armor, like a full iron set, and 8 toughness, like a full diamond set).
- Optional bonus max health on top of the vanilla 100.
- Optional mode that only buffs player-built golems and leaves village golems vanilla.
- Golems that already exist get updated when their chunk loads, so the mod works on existing worlds and config changes apply after a world reload.
- Runs on the server only. In multiplayer, players don't need the mod installed.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.257 or newer

## Installation

Put `golemarmor-<version>.jar` in your instance's `mods/` folder.

## Configuration

The config file is `config/golemarmor-common.toml` and is created on first launch.

| Option              | Default | Range      | Description                                                        |
|---------------------|---------|------------|--------------------------------------------------------------------|
| `armor`             | `15.0`  | 0 – 30     | Armor points added. 15 = full iron set.                            |
| `toughness`         | `8.0`   | 0 – 20     | Armor toughness added. 8 = full diamond set, 12 = full netherite.  |
| `bonusHealth`       | `0.0`   | 0 – 1000   | Extra max health added to the vanilla 100.                         |
| `playerCreatedOnly` | `false` | true/false | If true, only player-built golems are buffed.                      |

Changes apply to each golem the next time it loads (for example, after you reload the world). Turning `playerCreatedOnly` on also removes the buffs from golems that no longer qualify. When `bonusHealth` goes up, golems are healed by the amount it increased.

## How it works

When an Iron Golem joins the world, the mod adds permanent attribute modifiers with fixed IDs. Because the IDs are fixed, applying them again does nothing, and a changed config value replaces the old modifier. It doesn't change the golem's base attribute values. Base values are saved with each entity, so changing them would not reach golems that already exist.

To remove the mod's effects, set every value to `0` (or `playerCreatedOnly` to `true` for village golems) and load the affected areas once before you uninstall. Modifiers left on golems after uninstalling will keep applying.

## Building

Requires JDK 21. If it isn't installed, Gradle downloads it automatically.

```sh
./gradlew build      # jar is written to build/libs/
./gradlew runClient  # launch a dev client with the mod
```

## License

MIT
