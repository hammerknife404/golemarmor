# Iron Farms

All iron golems are buffed by default, including those villagers summon. Armor roughly doubles how long golems survive lava kill chambers with the default values, so more can be alive at once. Cramming ignores armor and is only slowed by `bonusHealth`, so keep that low on servers with many farms.

Some immunities break particular farm designs:

- `fire = "NO_AFTERBURN"` (the default) breaks farms that rely on afterburn after a brief dip in lava or fire. Farms where golems stay in contact with lava, such as head-height lava blades, still work.
- `fire = "FULL"` breaks every lava and fire farm, which is why it isn't the default.
- `magma = true` (the default) breaks magma-floor kill chambers.

These are under `[ironGolems.immunities]`; turn off the ones your farm needs. To leave farms completely vanilla, set `ironGolems.playerCreatedOnly = true`: golems spawned by villagers then get no buffs or immunities at all, while golems you build keep them.
