# Configuration

The config file is `config/golemarmor-common.toml`, created on first launch. You can also edit it in game from **Mods → Iron Golem Armor → Config**, where options are grouped into menus with mouse-over help. On a dedicated server only the server's copy counts.

Saving the file while the game is running updates every loaded golem right away; golems in unloaded chunks update when they next load.

## Iron golems (`[ironGolems]`)

| Option | Default | Range | Description |
|---|---|---|---|
| `playerCreatedOnly` | `false` | true/false | Only buff player-built golems; golems spawned by villages and villagers stay vanilla. Turning it on also strips buffs and immunities from golems that no longer qualify. |

### Stats (`[ironGolems.stats]`)

| Option | Default | Range | Description |
|---|---|---|---|
| `armor` | `15.0` | 0 – 30 | Armor points added. 15 = full iron set. |
| `toughness` | `8.0` | 0 – 20 | Armor toughness added. 8 = full diamond set, 12 = full netherite. |
| `bonusHealth` | `0.0` | 0 – 924 | Extra max health on top of the vanilla 100 (vanilla caps max health at 1024). Raising it heals golems by the increase. |

### Immunities (`[ironGolems.immunities]`)

| Option | Default | Range | Description |
|---|---|---|---|
| `fire` | `NO_AFTERBURN` | `OFF` / `NO_AFTERBURN` / `FULL` | `NO_AFTERBURN`: stop burning as soon as they leave fire or lava, but still take damage while in it. `FULL`: no fire, lava, burning, magma or fireball damage, like the Fire Resistance potion (magma included even with `magma` off). |
| `poison` | `true` | true/false | Golems can't be poisoned. Turning it on cures golems that already are. |
| `magma` | `true` | true/false | No damage from standing on magma blocks. |
| `plants` | `true` | true/false | No damage from cactus or sweet berry bushes. |
| `freezing` | `true` | true/false | Golems don't freeze in powder snow. |

Golems already ignore drowning and fall damage in vanilla.

### Creeper hunting (`[ironGolems.creepers]`)

| Option | Default | Range | Description |
|---|---|---|---|
| `attack` | `false` | true/false | Golems attack creepers, which vanilla golems ignore. |
| `villagerSafetyRadius` | `8.0` | 0 – 32 | Leave a creeper alone while a villager or wandering trader is this many blocks from it, and back off if one walks into range mid-fight. Doubled for charged creepers. `0` = always attack. A creeper blast reaches 6 blocks, 12 when charged. |

## Snow golems (`[snowGolems]`)

See [Snow Golems](Snow-Golems) for how these play out.

| Option | Default | Range | Description |
|---|---|---|---|
| `snowTrails` | `true` | true/false | Snow golems leave snow layers as they walk, like vanilla (still needs the `mobGriefing` gamerule). Off keeps paths and builds clear. |

### Stats (`[snowGolems.stats]`)

| Option | Default | Range | Description |
|---|---|---|---|
| `armor` | `1.0` | 0 – 30 | Armor points added. `0` = vanilla. |
| `toughness` | `0.0` | 0 – 20 | Armor toughness added. |

### Immunities (`[snowGolems.immunities]`)

| Option | Default | Range | Description |
|---|---|---|---|
| `water` | `true` | true/false | No damage from rain or water. |
| `fire` | `NO_AFTERBURN` | `OFF` / `NO_AFTERBURN` / `FULL` | Same modes as the iron golem option. `FULL` also stops snow golems melting in hot biomes like deserts and the Nether. |
| `poison` | `true` | true/false | Snow golems can't be poisoned. Turning it on cures golems that already are. |
| `magma` | `true` | true/false | No damage from standing on magma blocks. |
| `plants` | `true` | true/false | No damage from cactus or sweet berry bushes. |

### Snowballs (`[snowGolems.snowballs]`)

| Option | Default | Range | Description |
|---|---|---|---|
| `yellowChance` | `0.1` | 0 – 1 | Chance per throw of a yellow snowball: poison and nausea for 5 seconds. |
| `rockChance` | `0.1` | 0 – 1 | Chance per throw of a rock-filled snowball: 1 damage and extra knockback. Yellow is rolled first, so if the two add up to more than 1, rock only gets the leftover share. |

## Upgrading

- **From 1.7.0 or older:** options used to sit flat at the top of the file (`armor`, `poisonImmunity`, `snowArmor`, ...). They're moved into the sections above on first launch, keeping your values.
- **From 1.4.0 or older:** `noLingeringFire` was replaced by the fire option. The old key is dropped and the new one starts at `NO_AFTERBURN`, so if you had set `noLingeringFire = false`, set `ironGolems.immunities.fire` to `"OFF"`.
