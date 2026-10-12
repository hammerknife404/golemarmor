package com.livingworld.golemarmor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point: adds a Config button to the mods list. Never loaded on dedicated servers. */
@Mod(value = IronGolemArmor.MOD_ID, dist = Dist.CLIENT)
public class IronGolemArmorClient {
    public IronGolemArmorClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
