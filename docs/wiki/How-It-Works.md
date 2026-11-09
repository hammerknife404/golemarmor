# How It Works

## What's saved on golems

Armor, toughness and health are permanent attribute modifiers with fixed IDs, added when a golem joins the world. Reapplying one does nothing and a changed value replaces it. Base values aren't touched, because they're saved per entity and changes wouldn't reach existing golems. That's why the mod works on existing worlds: a golem picks up the current values the next time its chunk loads, or right away when the config file is saved.

The immunities, creeper hunting and snow trail toggle are checked live through game events, and nothing about them is saved on the golem.

Special snowballs are vanilla snowballs carrying a yellow dye or flint item marked with custom model data. No new items are registered, which is why players without the mod can still join.

## Uninstalling

The immunities stop when the mod is removed, but the saved modifiers keep applying. To remove them first, set the iron golem `armor`, `toughness` and `bonusHealth` and the snow golem `armor` and `toughness` to `0` (or `ironGolems.playerCreatedOnly` to `true` for village golems only) and load the affected areas once.

## Upgrading

Old config files are carried over; see [Configuration](Configuration#upgrading).
