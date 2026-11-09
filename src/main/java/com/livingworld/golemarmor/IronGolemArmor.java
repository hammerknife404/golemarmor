package com.livingworld.golemarmor;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Gives vanilla Iron Golems armor, armor toughness and optional bonus health,
 * plus optional resistance to fire and immunities to poison, magma blocks, plants and freezing,
 * and can make them attack creepers. Snow Golems get their own armor, immunities, a snow trail
 * toggle and a chance to throw yellow (poison and nausea) or rock-filled (damage and knockback) snowballs.
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
    private static final Logger LOGGER = LogUtils.getLogger();

    public IronGolemArmor(IEventBus modBus, ModContainer container) {
        // Must run before the config file is loaded, so the loader sees the sectioned layout.
        Config.migrateFlatLayout();
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modBus.addListener(Events::onConfigReload);
    }

    /**
     * Options are grouped into sections so the in-game config screen shows them as sub-menus.
     * Every entry and section has an explicit translation key, so its label and mouse-over
     * text come from the lang file (key and key + ".tooltip"); the comments only document the TOML file.
     */
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
        public static final ModConfigSpec.BooleanValue SNOW_TRAILS;
        public static final ModConfigSpec.DoubleValue SNOW_ARMOR;
        public static final ModConfigSpec.DoubleValue SNOW_TOUGHNESS;
        public static final ModConfigSpec.BooleanValue SNOW_WATER_IMMUNITY;
        public static final ModConfigSpec.EnumValue<FireResistance> SNOW_FIRE_RESISTANCE;
        public static final ModConfigSpec.BooleanValue SNOW_MAGMA_IMMUNITY;
        public static final ModConfigSpec.BooleanValue SNOW_PLANT_IMMUNITY;
        public static final ModConfigSpec.BooleanValue SNOW_POISON_IMMUNITY;
        public static final ModConfigSpec.DoubleValue YELLOW_SNOWBALL_CHANCE;
        public static final ModConfigSpec.DoubleValue ROCK_SNOWBALL_CHANCE;

        private static String tr(String path) {
            return MOD_ID + ".configuration." + path;
        }

        static {
            ModConfigSpec.Builder b = new ModConfigSpec.Builder();

            b.comment("Iron golem settings.").translation(tr("ironGolems")).push("ironGolems");
            PLAYER_CREATED_ONLY = b.comment("If true, only player-built golems are buffed; village golems stay vanilla.")
                    .translation(tr("ironGolems.playerCreatedOnly"))
                    .define("playerCreatedOnly", false);

            b.comment("Armor, toughness and health.").translation(tr("ironGolems.stats")).push("stats");
            ARMOR = b.comment("Armor points added. 15 = full vanilla iron set.")
                    .translation(tr("ironGolems.stats.armor"))
                    .defineInRange("armor", 15.0, 0.0, 30.0);
            TOUGHNESS = b.comment("Armor toughness added. 8 = full diamond set, 12 = full netherite set.")
                    .translation(tr("ironGolems.stats.toughness"))
                    .defineInRange("toughness", 8.0, 0.0, 20.0);
            BONUS_HEALTH = b.comment("Extra max health added to the vanilla 100. Max 924 (vanilla caps max health at 1024).")
                    .translation(tr("ironGolems.stats.bonusHealth"))
                    .defineInRange("bonusHealth", 0.0, 0.0, 924.0);
            b.pop();

            b.comment("Environmental immunities.").translation(tr("ironGolems.immunities")).push("immunities");
            FIRE_RESISTANCE = b.comment("OFF: vanilla.",
                            "NO_AFTERBURN: stop burning as soon as they leave fire or lava, but still take damage while in it.",
                            "FULL: like the Fire Resistance potion, no damage from fire, lava, burning, magma or fireballs.",
                            "FULL stops lava-based iron farms from killing golems.")
                    .translation(tr("ironGolems.immunities.fire"))
                    .defineEnum("fire", FireResistance.NO_AFTERBURN);
            POISON_IMMUNITY = b.comment("If true, golems can't be poisoned.")
                    .translation(tr("ironGolems.immunities.poison"))
                    .define("poison", true);
            MAGMA_IMMUNITY = b.comment("If true, no damage from standing on magma blocks.")
                    .translation(tr("ironGolems.immunities.magma"))
                    .define("magma", true);
            PLANT_IMMUNITY = b.comment("If true, no damage from cactus or sweet berry bushes.")
                    .translation(tr("ironGolems.immunities.plants"))
                    .define("plants", true);
            FREEZE_IMMUNITY = b.comment("If true, golems don't freeze in powder snow.")
                    .translation(tr("ironGolems.immunities.freezing"))
                    .define("freezing", true);
            b.pop();

            b.comment("Creeper hunting.").translation(tr("ironGolems.creepers")).push("creepers");
            ATTACK_CREEPERS = b.comment("If true, golems attack creepers. Vanilla golems ignore them.")
                    .translation(tr("ironGolems.creepers.attack"))
                    .define("attack", false);
            CREEPER_VILLAGER_RADIUS = b.comment("Leave a creeper alone while a villager or wandering trader is within this many blocks of it,",
                            "and back off if one walks into range mid-fight. Doubled for charged creepers. 0 = always attack.",
                            "A creeper explosion hurts out to 6 blocks, 12 when charged.")
                    .translation(tr("ironGolems.creepers.villagerSafetyRadius"))
                    .defineInRange("villagerSafetyRadius", 8.0, 0.0, 32.0);
            b.pop();
            b.pop();

            b.comment("Snow golem settings.").translation(tr("snowGolems")).push("snowGolems");
            SNOW_TRAILS = b.comment("If true, snow golems leave snow layers as they walk (vanilla; also needs the mobGriefing gamerule).",
                            "If false, they never leave trails.")
                    .translation(tr("snowGolems.snowTrails"))
                    .define("snowTrails", true);

            b.comment("Armor and toughness.").translation(tr("snowGolems.stats")).push("stats");
            SNOW_ARMOR = b.comment("Armor points added. 0 = vanilla.")
                    .translation(tr("snowGolems.stats.armor"))
                    .defineInRange("armor", 1.0, 0.0, 30.0);
            SNOW_TOUGHNESS = b.comment("Armor toughness added.")
                    .translation(tr("snowGolems.stats.toughness"))
                    .defineInRange("toughness", 0.0, 0.0, 20.0);
            b.pop();

            b.comment("Environmental immunities.").translation(tr("snowGolems.immunities")).push("immunities");
            SNOW_WATER_IMMUNITY = b.comment("If true, no damage from rain or water.")
                    .translation(tr("snowGolems.immunities.water"))
                    .define("water", true);
            SNOW_FIRE_RESISTANCE = b.comment("Same modes as the iron golem fire option.")
                    .translation(tr("snowGolems.immunities.fire"))
                    .defineEnum("fire", FireResistance.NO_AFTERBURN);
            SNOW_POISON_IMMUNITY = b.comment("If true, snow golems can't be poisoned.")
                    .translation(tr("snowGolems.immunities.poison"))
                    .define("poison", true);
            SNOW_MAGMA_IMMUNITY = b.comment("If true, no damage from standing on magma blocks.")
                    .translation(tr("snowGolems.immunities.magma"))
                    .define("magma", true);
            SNOW_PLANT_IMMUNITY = b.comment("If true, no damage from cactus or sweet berry bushes.")
                    .translation(tr("snowGolems.immunities.plants"))
                    .define("plants", true);
            b.pop();

            b.comment("Special snowballs.").translation(tr("snowGolems.snowballs")).push("snowballs");
            YELLOW_SNOWBALL_CHANCE = b.comment("Chance per throw of a yellow snowball: poison and nausea for 5 seconds. 0 = never.")
                    .translation(tr("snowGolems.snowballs.yellowChance"))
                    .defineInRange("yellowChance", 0.1, 0.0, 1.0);
            ROCK_SNOWBALL_CHANCE = b.comment("Chance per throw of a rock-filled snowball: 1 damage and extra knockback. 0 = never.",
                            "Yellow is rolled first, so if the two chances add up to more than 1, rock only gets the leftover share.")
                    .translation(tr("snowGolems.snowballs.rockChance"))
                    .defineInRange("rockChance", 0.1, 0.0, 1.0);
            b.pop();
            b.pop();

            SPEC = b.build();
        }

        /** Flat keys used up to 1.7.0, and where each now lives. */
        private static final Map<String, String> FLAT_KEYS = Map.ofEntries(
                Map.entry("armor", "ironGolems.stats.armor"),
                Map.entry("toughness", "ironGolems.stats.toughness"),
                Map.entry("bonusHealth", "ironGolems.stats.bonusHealth"),
                Map.entry("playerCreatedOnly", "ironGolems.playerCreatedOnly"),
                Map.entry("poisonImmunity", "ironGolems.immunities.poison"),
                Map.entry("fireResistance", "ironGolems.immunities.fire"),
                Map.entry("magmaImmunity", "ironGolems.immunities.magma"),
                Map.entry("plantImmunity", "ironGolems.immunities.plants"),
                Map.entry("freezeImmunity", "ironGolems.immunities.freezing"),
                Map.entry("attackCreepers", "ironGolems.creepers.attack"),
                Map.entry("creeperVillagerRadius", "ironGolems.creepers.villagerSafetyRadius"),
                Map.entry("snowArmor", "snowGolems.stats.armor"),
                Map.entry("snowToughness", "snowGolems.stats.toughness"),
                Map.entry("snowWaterImmunity", "snowGolems.immunities.water"),
                Map.entry("snowFireResistance", "snowGolems.immunities.fire"),
                Map.entry("snowMagmaImmunity", "snowGolems.immunities.magma"),
                Map.entry("snowPlantImmunity", "snowGolems.immunities.plants"),
                Map.entry("snowPoisonImmunity", "snowGolems.immunities.poison"),
                Map.entry("yellowSnowballChance", "snowGolems.snowballs.yellowChance"),
                Map.entry("rockSnowballChance", "snowGolems.snowballs.rockChance"));

        /**
         * Moves values from the flat layout (1.7.0 and older) into the sections, so upgrading keeps
         * custom settings. Without this the loader would drop the old keys and reset everything to
         * defaults. Best effort: on any error the file is left alone and the loader resets it as usual.
         */
        static void migrateFlatLayout() {
            Path file = FMLPaths.CONFIGDIR.get().resolve(MOD_ID + "-common.toml");
            if (!Files.isRegularFile(file)) return;
            try (CommentedFileConfig config = CommentedFileConfig.builder(file, TomlFormat.instance()).build()) {
                config.load();
                boolean changed = false;
                for (Map.Entry<String, String> key : FLAT_KEYS.entrySet()) {
                    if (!config.contains(key.getKey())) continue;
                    Object value = config.remove(key.getKey());
                    if (!config.contains(key.getValue())) config.set(key.getValue(), value);
                    changed = true;
                }
                if (changed) {
                    config.save();
                    LOGGER.info("Moved {} settings into the sectioned config layout", MOD_ID);
                }
            } catch (Exception e) {
                LOGGER.warn("Couldn't migrate {}; old settings will reset to defaults", file, e);
            }
        }
    }

    public enum FireResistance implements TranslatableEnum {
        OFF, NO_AFTERBURN, FULL;

        @Override
        public Component getTranslatedName() {
            return Component.translatable(MOD_ID + ".configuration.fireResistance." + name().toLowerCase(Locale.ROOT));
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
        private static final ResourceLocation SNOW_ARMOR_ID =
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "snow_golem_armor");
        private static final ResourceLocation SNOW_TOUGHNESS_ID =
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "snow_golem_toughness");
        /** Poison and nausea from a yellow snowball. */
        private static final int YELLOW_EFFECT_TICKS = 5 * 20;
        /** Extra knockback from a rock snowball, the same push as a Punch I arrow. */
        private static final double ROCK_KNOCKBACK = 0.6;
        /**
         * Snowball variants ride on vanilla items so the mod stays optional on clients:
         * no new registry entries means vanilla and mod-less clients can still join.
         * Those clients see plain flint or yellow dye in flight. Clients with the mod see the
         * snowball textures via custom_model_data overrides on the vanilla item models.
         * Values must match assets/minecraft/models/item/{flint,yellow_dye}.json.
         */
        private static final int YELLOW_SNOWBALL_MODEL = 4041701;
        private static final int ROCK_SNOWBALL_MODEL = 4041702;

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
                ball.setItem(variant(Items.YELLOW_DYE, YELLOW_SNOWBALL_MODEL));
            } else if (roll < yellowChance + Config.ROCK_SNOWBALL_CHANCE.get()) {
                ball.setItem(variant(Items.FLINT, ROCK_SNOWBALL_MODEL));
            }
        }

        @SubscribeEvent
        public static void onSnowballImpact(ProjectileImpactEvent event) {
            if (!(event.getProjectile() instanceof Snowball ball)) return;
            if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
            if (!(hit.getEntity() instanceof LivingEntity target)) return;

            // Snow golems' snowballs pass through other snow golems instead of hitting them.
            // Covers plain snowballs too, which vanilla lets knock back the golem they hit.
            if (target instanceof SnowGolem && ball.getOwner() instanceof SnowGolem) {
                event.setCanceled(true);
                return;
            }

            ItemStack stack = ball.getItem();
            if (isVariant(stack, Items.FLINT, ROCK_SNOWBALL_MODEL)) {
                // Vanilla's own hit lands after this one and does 3 to blazes, which i-frames would cap
                // at this hit's damage. Deal vanilla's 3 plus the rock's 1 up front instead.
                float damage = target instanceof Blaze ? 4.0F : 1.0F;
                if (target.hurt(ball.damageSources().thrown(ball, ball.getOwner()), damage)) {
                    // Pushed along the snowball's flight, scaled down by knockback resistance like arrows.
                    double resistance = Math.max(0.0, 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                    Vec3 push = ball.getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize().scale(ROCK_KNOCKBACK * resistance);
                    if (push.lengthSqr() > 0.0) {
                        target.push(push.x, 0.1, push.z);
                        target.hurtMarked = true; // sync the new velocity to players
                    }
                }
            } else if (isVariant(stack, Items.YELLOW_DYE, YELLOW_SNOWBALL_MODEL)) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, YELLOW_EFFECT_TICKS));
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, YELLOW_EFFECT_TICKS));
            }
        }

        private static ItemStack variant(Item item, int model) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(model));
            return stack;
        }

        private static boolean isVariant(ItemStack stack, Item item, int model) {
            CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            return stack.is(item) && data != null && data.value() == model;
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
            if (!(event.getEntity() instanceof SnowGolem)) return;
            DamageSource source = event.getSource();
            // Vanilla hurts water-sensitive mobs in rain or water with the drown damage type.
            if ((Config.SNOW_FIRE_RESISTANCE.get() == FireResistance.FULL && source.is(DamageTypeTags.IS_FIRE))
                    || (Config.SNOW_MAGMA_IMMUNITY.get() && source.is(DamageTypes.HOT_FLOOR))
                    || (Config.SNOW_PLANT_IMMUNITY.get()
                            && (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)))
                    || (Config.SNOW_WATER_IMMUNITY.get() && source.is(DamageTypes.DROWN))) {
                event.setCanceled(true);
            }
        }

        /** Snow golems' snow trail is their only mob-griefing check, so denying it here only stops the trail. */
        @SubscribeEvent
        public static void onMobGriefing(EntityMobGriefingEvent event) {
            if (event.getEntity() instanceof SnowGolem && !Config.SNOW_TRAILS.get()) event.setCanGrief(false);
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
