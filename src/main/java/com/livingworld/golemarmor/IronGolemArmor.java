package com.livingworld.golemarmor;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Gives vanilla Iron Golems armor, armor toughness and optional bonus health,
 * plus optional immunities to poison, lingering fire, magma blocks and cactus.
 *
 * Uses permanent attribute modifiers applied on EntityJoinLevelEvent instead of
 * EntityAttributeModificationEvent, because attribute base values are saved in each
 * entity's NBT: golems that already exist in a world would keep their old base values
 * and never pick up a changed default. A modifier with a fixed ID is applied
 * idempotently, so existing golems get patched the next time their chunk loads.
 * Editing the config file while a world is running re-applies to every loaded golem.
 *
 * Single-file layout for convenience: split Config out if you prefer.
 */
@Mod(IronGolemArmor.MOD_ID)
public class IronGolemArmor {
    public static final String MOD_ID = "golemarmor";

    public IronGolemArmor(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modBus.addListener(Events::onConfigReload);
    }

    public static final class Config {
        public static final ModConfigSpec SPEC;
        public static final ModConfigSpec.DoubleValue ARMOR;
        public static final ModConfigSpec.DoubleValue TOUGHNESS;
        public static final ModConfigSpec.DoubleValue BONUS_HEALTH;
        public static final ModConfigSpec.BooleanValue PLAYER_CREATED_ONLY;
        public static final ModConfigSpec.BooleanValue POISON_IMMUNITY;
        public static final ModConfigSpec.BooleanValue NO_LINGERING_FIRE;
        public static final ModConfigSpec.BooleanValue MAGMA_IMMUNITY;
        public static final ModConfigSpec.BooleanValue CACTUS_IMMUNITY;

        static {
            ModConfigSpec.Builder b = new ModConfigSpec.Builder();
            ARMOR = b.comment("Armor points added to iron golems. 15 = full vanilla iron set.")
                    .defineInRange("armor", 15.0, 0.0, 30.0);
            TOUGHNESS = b.comment("Armor toughness added. 8 = full diamond set, 12 = full netherite set.")
                    .defineInRange("toughness", 8.0, 0.0, 20.0);
            BONUS_HEALTH = b.comment("Extra max health added to the vanilla 100. 0 = unchanged. Max 924 (vanilla caps max health at 1024).")
                    .defineInRange("bonusHealth", 0.0, 0.0, 924.0);
            PLAYER_CREATED_ONLY = b.comment("If true, only player-built golems are buffed; village golems stay vanilla.")
                    .define("playerCreatedOnly", false);
            POISON_IMMUNITY = b.comment("If true, golems can't be poisoned.")
                    .define("poisonImmunity", true);
            NO_LINGERING_FIRE = b.comment("If true, golems stop burning as soon as they leave fire or lava.",
                            "They still take damage while standing in it.")
                    .define("noLingeringFire", true);
            MAGMA_IMMUNITY = b.comment("If true, golems take no damage from standing on magma blocks.")
                    .define("magmaImmunity", true);
            CACTUS_IMMUNITY = b.comment("If true, golems take no damage from cactus.")
                    .define("cactusImmunity", true);
            SPEC = b.build();
        }
    }

    @EventBusSubscriber(modid = MOD_ID)
    public static final class Events {
        private static final ResourceLocation ARMOR_ID =
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "golem_armor");
        private static final ResourceLocation TOUGHNESS_ID =
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "golem_toughness");
        private static final ResourceLocation HEALTH_ID =
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "golem_health");

        @SubscribeEvent
        public static void onJoin(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide()) return;
            if (!(event.getEntity() instanceof IronGolem golem)) return;
            update(golem);
        }

        @SubscribeEvent
        public static void onEffectApplicable(MobEffectEvent.Applicable event) {
            if (!(event.getEntity() instanceof IronGolem golem)) return;
            if (Config.POISON_IMMUNITY.get() && isEligible(golem)
                    && event.getEffectInstance().is(MobEffects.POISON)) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof IronGolem golem)) return;
            if (!isEligible(golem)) return;
            DamageSource source = event.getSource();
            if ((Config.MAGMA_IMMUNITY.get() && source.is(DamageTypes.HOT_FLOOR))
                    || (Config.CACTUS_IMMUNITY.get() && source.is(DamageTypes.CACTUS))) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onTick(EntityTickEvent.Post event) {
            if (!(event.getEntity() instanceof IronGolem golem)) return;
            // Cheap checks first: this runs for every entity every tick.
            if (golem.getRemainingFireTicks() <= 0 || golem.level().isClientSide()) return;
            if (!Config.NO_LINGERING_FIRE.get() || !isEligible(golem)) return;
            // Same "still in fire?" test vanilla uses in Entity.move before letting fire tick down.
            boolean inFire = golem.level()
                    .getBlockStatesIfLoaded(golem.getBoundingBox().deflate(1.0E-6))
                    .anyMatch(state -> state.is(BlockTags.FIRE) || state.is(Blocks.LAVA));
            if (!inFire) golem.clearFire();
        }

        /** Fired when the config file changes on disk, on FML's file-watcher thread. */
        static void onConfigReload(ModConfigEvent.Reloading event) {
            if (event.getConfig().getSpec() != Config.SPEC) return;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return; // no world running; golems update when they load
            // Entities may only be touched on the server thread.
            server.execute(() -> {
                for (ServerLevel level : server.getAllLevels()) {
                    for (IronGolem golem : level.getEntities(EntityType.IRON_GOLEM, g -> true)) {
                        update(golem);
                    }
                }
            });
        }

        private static void update(IronGolem golem) {
            // Ineligible golems still go through apply() with 0 so that turning on
            // playerCreatedOnly strips buffs from village golems buffed earlier.
            boolean eligible = isEligible(golem);

            apply(golem, Attributes.ARMOR, ARMOR_ID, eligible ? Config.ARMOR.get() : 0.0);
            apply(golem, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, eligible ? Config.TOUGHNESS.get() : 0.0);

            double added = apply(golem, Attributes.MAX_HEALTH, HEALTH_ID, eligible ? Config.BONUS_HEALTH.get() : 0.0);
            if (added > 0.0) {
                // Health bonus grew: top up by the increase so the golem isn't left at old-max HP.
                // A shrink needs nothing; health is clamped to the new max automatically.
                golem.heal((float) added);
            }

            // Cure golems already poisoned when poisonImmunity was off.
            if (eligible && Config.POISON_IMMUNITY.get()) golem.removeEffect(MobEffects.POISON);
        }

        private static boolean isEligible(IronGolem golem) {
            return !Config.PLAYER_CREATED_ONLY.get() || golem.isPlayerCreated();
        }

        /** Idempotent. Returns the change in modifier amount (new - old, 0 if unchanged). */
        private static double apply(IronGolem golem, Holder<Attribute> attr, ResourceLocation id, double amount) {
            AttributeInstance inst = golem.getAttribute(attr);
            if (inst == null) return 0.0;

            AttributeModifier existing = inst.getModifier(id);
            double old = existing == null ? 0.0 : existing.amount();
            if (old == amount) return 0.0;

            if (existing != null) inst.removeModifier(id);
            if (amount != 0.0) {
                inst.addPermanentModifier(
                        new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
            }
            return amount - old;
        }
    }
}
