# Snow Golems

Snow golems are buffed the same way as iron golems, with their own options under `[snowGolems]` (see [Configuration](Configuration)). They're always player-built, so there's no player-built-only option for them.

## Armor and immunities

By default snow golems get 1 armor point and take no damage from rain or water, magma blocks, cactus or sweet berry bushes, and can't be poisoned. Fire works like it does for iron golems: `NO_AFTERBURN` puts them out once they leave the fire, and `FULL` makes them fireproof, which also stops them melting in hot biomes like deserts and the Nether.

## Snow trails

Snow golems leave snow layers as they walk, as in vanilla. Set `snowTrails = false` to stop that and keep paths and builds clear. Trails also still need the `mobGriefing` gamerule, as in vanilla; turning trails off here doesn't change the gamerule or affect any other mob.

## Special snowballs

Each snowball a snow golem throws is rolled:

| Snowball | Chance (default) | On hit |
|---|---|---|
| Yellow | `yellowChance` (10%) | Poison and nausea for 5 seconds |
| Rock-filled | `rockChance` (10%) | 1 damage and extra knockback, the same push as a Punch I arrow, less for mobs with knockback resistance |
| Plain | the rest | As in vanilla |

Yellow is rolled first, so if the two chances add up to more than 1, rock only gets what's left. Blazes take vanilla's 3 snowball damage plus the rock's 1.

Snow golems' snowballs pass through other snow golems instead of hitting them, plain ones included, so a group of golems doesn't knock each other around.

Players with the mod see the yellow and rock snowball textures in flight. Players without it can still join; to them the special snowballs look like yellow dye and flint.
