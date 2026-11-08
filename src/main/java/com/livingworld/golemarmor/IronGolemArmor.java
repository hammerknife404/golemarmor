package com.livingworld.golemarmor;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Gives vanilla Iron Golems armor, armor toughness and optional bonus health,
 * plus optional resistance to fire and immunities to poison, magma blocks, plants and freezing,
 * and can make them attack creepers. Snow Golems get their own armor, immunities and
 * a chance to throw poisoning or rock-filled snowballs.
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

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    /** Snowballs thrown by snow golems. Rendered with their own item model, so they look yellow or rocky in flight. */
    public static final DeferredItem<Item> YELLOW_SNOWBALL = ITEMS.registerSimpleItem("yellow_snowball");
    public static final DeferredItem<Item> ROCK_SNOWBALL = ITEMS.registerSimpleItem("rock_snowball");

    public IronGolemArmor(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ITEMS.register(modBus);
        modBus.addListener(Events::onConfigReload);
    }

    public static final class Config {
        public static final ModConfigSpec SPEC;
        public static final ModConfigSpec.DoubleValue ARMOR;
        public static final ModConfigSpec.DoubleValue TOUGHNESS;
        public static final ModConfigSpec.DoubleValue BONUS_HEALTH;
        public static final ModConfigSpec.BooleanValue PLAYER_CREATED_ONLY;
        public static final ModConfigSpec.BooleanValue POISON_IMMUNITY;
        public static final ModConfigSpec.EnumValue<FireResistance> FIRE_RESISTANCE;
        public static final ModConfigSpec.BooleanValue MAGMA_IMMUNITY;
        public static final ModConfigSpec.BooleanValue PLANT_IMMUNITY;
        public static final ModConfigSpec.BooleanValue FREEZE_IMMUNITY;
        public static final ModConfigSpec.BooleanValue ATTACK_CREEPERS;
        public static final ModConfigSpec.DoubleValue CREEPER_VILLAGER_RADIUS;
        public static final ModConfigSpec.DoubleValue SNOW_ARMOR;
        public static final ModConfigSpec.DoubleValue SNOW_TOUGHNESS;
        public static final ModConfigSpec.BooleanValue SNOW_WATER_IMMUNITY;
        public static final ModConfigSpec.EnumValue<FireResistance> SNOW_FIRE_RESISTANCE;
        public static final ModConfigSpec.BooleanValue SNOW_MAGMA_IMMUNITY;
        public static final ModConfigSpec.BooleanValue SNOW_PLANT_IMMUNITY;
        public static final ModConfigSpec.BooleanValue SNOW_POISON_IMMUNITY;
        public static final ModConfigSpec.DoubleValue YELLOW_SNOWBALL_CHANCE;
        public static final ModConfigSpec.DoubleValue ROCK_SNOWBALL_CHANCE;

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
            FIRE_RESISTANCE = b.comment("How well golems resist fire.",
                            "OFF: vanilla.",
                            "NO_AFTERBURN: golems stop burning as soon as they leave fire or lava, but still take damage while in it.",
                            "FULL: like the Fire Resistance potion, no damage from fire, lava, burning, magma or fireballs.",
                            "FULL stops lava-based iron farms from killing golems.")
                    .defineEnum("fireResistance", FireResistance.NO_AFTERBURN);
            MAGMA_IMMUNITY = b.comment("If true, golems take no damage from standing on magma blocks.")
                    .define("magmaImmunity", true);
            PLANT_IMMUNITY = b.comment("If true, golems take no damage from cactus or sweet berry bushes.")
                    .define("plantImmunity", true);
            FREEZE_IMMUNITY = b.comment("If true, golems don't freeze in powder snow.")
                    .define("freezeImmunity", true);
            ATTACK_CREEPERS = b.comment("If true, golems attack creepers. Vanilla golems ignore them.")
                    .define("attackCreepers", false);
            CREEPER_VILLAGER_RADIUS = b.comment("Golems leave a creeper alone while a villager or wandering trader is within this many blocks of it,",
                            "and back off if one walks into range mid-fight. Doubled for charged creepers. 0 = always attack.",
                            "A creeper explosion hurts out to 6 blocks, 12 when charged.")
                    .defineInRange("creeperVillagerRadius", 8.0, 0.0, 32.0);
            SNOW_ARMOR = b.comment("Armor points added to snow golems. 0 = vanilla.")
                    .defineInRange("snowArmor", 1.0, 0.0, 30.0);
            SNOW_TOUGHNESS = b.comment("Armor toughness added to snow golems.")
                    .defineInRange("snowToughness", 0.0, 0.0, 20.0);
            SNOW_WATER_IMMUNITY = b.comment("If true, snow golems take no damage from rain or water.")
                    .define("snowWaterImmunity", true);
            SNOW_FIRE_RESISTANCE = b.comment("How well snow golems resist fire. Same modes as fireResistance.")
                    .defineEnum("snowFireResistance", FireResistance.NO_AFTERBURN);
            SNOW_MAGMA_IMMUNITY = b.comment("If true, snow golems take no damage from standing on magma blocks.")
                    .define("snowMagmaImmunity", true);
            SNOW_PLANT_IMMUNITY = b.comment("If true, snow golems take no damage from cactus or sweet berry bushes.")
                    .define("snowPlantImmunity", true);
            SNOW_POISON_IMMUNITY = b.comment("If true, snow golems can't be poisoned.")
                    .define("snowPoisonImmunity", true);
            YELLOW_SNOWBALL_CHANCE = b.comment("Chance per throw that a snow golem throws a yellow snowball,",
                            "which poisons the target for 5 seconds. 0 = never.")
                    .defineInRange("yellowSnowballChance", 0.1, 0.0, 1.0);
            ROCK_SNOWBALL_CHANCE = b.comment("Chance per throw that a snow golem throws a rock-filled snowball, which deals 1 damage. 0 = never.",
                            "Yellow is rolled first, so if the two chances add up to more than 1, rock only gets the leftover share.")
                    .defineInRange("rockSnowballChance", 0.1, 0.0, 1.0);
            SPEC = b.build();
        }
    }

    public enum FireResistance { OFF, NO_AFTERBURN, FULL }

    @EventBusSubscriber(modid = MOD_ID)
    public static final class Events {
        private static final ResourceLocation ARMOR_ID =
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "golem_armor");
        private static final ResourceLocation TOUGHNESS_ID =
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "golem_toughness");
        private static final ResourceLocation HEALTH_ID =
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "golem_health");
        private static final ResourceLocation SNOW_ARMOR_ID =
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "snow_golem_armor");
        private static final ResourceLocation SNOW_TOUGHNESS_ID =
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "snow_golem_toughness");
        /** Poison from a yellow snowball. */
        private static final int YELLOW_POISON_TICKS = 5 * 20;

        @SubscribeEvent
        public static void onJoin(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide()) return;
            if (!(event.getEntity() instanceof IronGolem golem)) return;
            update(golem);
            // Goals aren't saved, so every golem instance needs one. Always added; it checks the config itself.
            if (golem.targetSelector.getAvailableGoals().stream()
                    .noneMatch(w -> w.getGoal() instanceof CreeperTargetGoal)) {
                // Same priority as the vanilla golem's monster-targeting goal.
                golem.targetSelector.addGoal(3, new CreeperTargetGoal(golem));
            }
        }

        @SubscribeEvent
        public static void onSnowGolemJoin(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide()) return;
            if (event.getEntity() instanceof SnowGolem golem) updateSnowGolem(golem);
        }

        /** Snowballs thrown by snow golems get a variant here; the item they carry decides what they do on impact. */
        @SubscribeEvent
        public static void onSnowballJoin(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide()) return;
            if (!(event.getEntity() instanceof Snowball ball) || !(ball.getOwner() instanceof SnowGolem)) return;
            // Saved projectiles rejoin when their chunk loads with their item intact; don't reroll them.
            if (!ball.getItem().is(Items.SNOWBALL)) return;

            float roll = ball.getRandom().nextFloat();
            double yellowChance = Config.YELLOW_SNOWBALL_CHANCE.get();
            if (roll < yellowChance) {
                ball.setItem(new ItemStack(YELLOW_SNOWBALL.get()));
            } else if (roll < yellowChance + Config.ROCK_SNOWBALL_CHANCE.get()) {
                ball.setItem(new ItemStack(ROCK_SNOWBALL.get()));
            }
        }

        @SubscribeEvent
        public static void onSnowballImpact(ProjectileImpactEvent event) {
            if (!(event.getProjectile() instanceof Snowball ball)) return;
            if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
            if (!(hit.getEntity() instanceof LivingEntity target)) return;

            ItemStack stack = ball.getItem();
            if (stack.is(ROCK_SNOWBALL.get())) {
                target.hurt(ball.damageSources().thrown(ball, ball.getOwner()), 1.0F);
            } else if (stack.is(YELLOW_SNOWBALL.get())) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, YELLOW_POISON_TICKS));
            }
        }

        @SubscribeEvent
        public static void onEffectApplicable(MobEffectEvent.Applicable event) {
            if (event.getEntity() instanceof SnowGolem) {
                if (Config.SNOW_POISON_IMMUNITY.get() && event.getEffectInstance().is(MobEffects.POISON)) {
                    event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                }
                return;
            }
            if (!(event.getEntity() instanceof IronGolem golem)) return;
            if (Config.POISON_IMMUNITY.get() && isEligible(golem)
                    && event.getEffectInstance().is(MobEffects.POISON)) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        }

        @SubscribeEvent
        public static void onSnowGolemIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof SnowGolem golem)) return;
            DamageSource source = event.getSource();
            // Water damage has no attacker, so a source-less hit while wet is the environmental one.
            if ((Config.SNOW_FIRE_RESISTANCE.get() == FireResistance.FULL && source.is(DamageTypeTags.IS_FIRE))
                    || (Config.SNOW_MAGMA_IMMUNITY.get() && source.is(DamageTypes.HOT_FLOOR))
                    || (Config.SNOW_PLANT_IMMUNITY.get()
                            && (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)))
                    || (Config.SNOW_WATER_IMMUNITY.get() && source.getEntity() == null && golem.isInWaterOrRain())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onSnowGolemTick(EntityTickEvent.Post event) {
            if (!(event.getEntity() instanceof SnowGolem golem)) return;
            if (golem.level().isClientSide()) return;
            if (golem.getRemainingFireTicks() <= 0 || Config.SNOW_FIRE_RESISTANCE.get() == FireResistance.OFF) return;
            clearAfterburnOutsideFire(golem);
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof IronGolem golem)) return;
            if (!isEligible(golem)) return;
            DamageSource source = event.getSource();
            // IS_FIRE is the same tag the vanilla Fire Resistance effect checks.
            if ((Config.FIRE_RESISTANCE.get() == FireResistance.FULL && source.is(DamageTypeTags.IS_FIRE))
                    || (Config.MAGMA_IMMUNITY.get() && source.is(DamageTypes.HOT_FLOOR))
                    || (Config.PLANT_IMMUNITY.get()
                            && (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)))
                    || (Config.FREEZE_IMMUNITY.get() && source.is(DamageTypes.FREEZE))) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onTick(EntityTickEvent.Post event) {
            if (!(event.getEntity() instanceof IronGolem golem)) return;
            // Cheap checks first: this runs for every entity every tick.
            if (golem.level().isClientSide()) return;
            boolean burning = golem.getRemainingFireTicks() > 0;
            boolean freezing = golem.getTicksFrozen() > 0;
            if (!burning && !freezing) return;
            if (!isEligible(golem)) return;

            // Resetting every tick keeps the golem from ever reaching fully frozen,
            // which also stops the shaking animation.
            if (freezing && Config.FREEZE_IMMUNITY.get()) golem.setTicksFrozen(0);

            if (!burning || Config.FIRE_RESISTANCE.get() == FireResistance.OFF) return;
            clearAfterburnOutsideFire(golem);
        }

        /** Puts out burning once the entity is no longer standing in fire or lava. */
        private static void clearAfterburnOutsideFire(LivingEntity entity) {
            // Same "still in fire?" test vanilla uses in Entity.move before letting fire tick down.
            boolean inFire = entity.level()
                    .getBlockStatesIfLoaded(entity.getBoundingBox().deflate(1.0E-6))
                    .anyMatch(state -> state.is(BlockTags.FIRE) || state.is(Blocks.LAVA));
            if (!inFire) entity.clearFire();
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
                    for (SnowGolem golem : level.getEntities(EntityType.SNOW_GOLEM, g -> true)) {
                        updateSnowGolem(golem);
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

        private static void updateSnowGolem(SnowGolem golem) {
            apply(golem, Attributes.ARMOR, SNOW_ARMOR_ID, Config.SNOW_ARMOR.get());
            apply(golem, Attributes.ARMOR_TOUGHNESS, SNOW_TOUGHNESS_ID, Config.SNOW_TOUGHNESS.get());
            if (Config.SNOW_POISON_IMMUNITY.get()) golem.removeEffect(MobEffects.POISON);
        }

        private static boolean canTargetCreeper(IronGolem golem, Creeper creeper) {
            if (!Config.ATTACK_CREEPERS.get() || !isEligible(golem)) return false;
            double radius = Config.CREEPER_VILLAGER_RADIUS.get();
            if (radius <= 0.0) return true;
            if (creeper.isPowered()) radius *= 2.0;
            return creeper.level()
                    .getEntitiesOfClass(AbstractVillager.class, creeper.getBoundingBox().inflate(radius))
                    .isEmpty();
        }

        private static boolean isEligible(IronGolem golem) {
            return !Config.PLAYER_CREATED_ONLY.get() || golem.isPlayerCreated();
        }

        /** Idempotent. Returns the change in modifier amount (new - old, 0 if unchanged). */
        private static double apply(LivingEntity golem, Holder<Attribute> attr, ResourceLocation id, double amount) {
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

    /**
     * Vanilla golems never target creepers: their monster goal filters them out, and
     * IronGolem.canAttackType rejects them, which fails any combat TargetingConditions.
     * Non-combat conditions skip that type check; the golem's melee goal attacks whatever it targets.
     */
    static final class CreeperTargetGoal extends NearestAttackableTargetGoal<Creeper> {
        private final IronGolem golem;

        CreeperTargetGoal(IronGolem golem) {
            super(golem, Creeper.class, 5, false, false, null);
            this.golem = golem;
            // Replaces the combat conditions super just built.
            this.targetConditions = TargetingConditions.forNonCombat()
                    .range(this.getFollowDistance())
                    .selector(target -> Events.canTargetCreeper(golem, (Creeper) target));
        }

        @Override
        public boolean canContinueToUse() {
            // Re-check so the golem backs off when a villager wanders close or the option is turned off.
            LivingEntity target = golem.getTarget();
            if (target instanceof Creeper creeper && !Events.canTargetCreeper(golem, creeper)) return false;
            return super.canContinueToUse();
        }
    }
}
