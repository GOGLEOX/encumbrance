package com.lefoxxy.encumbrance.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EncumbranceConfig {
    public static final ForgeConfigSpec COMMON_SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_SYSTEM;
    public static final ForgeConfigSpec.BooleanValue REALISM_MODE;
    public static final ForgeConfigSpec.DoubleValue MAX_WEIGHT_BEFORE_PENALTY;
    public static final ForgeConfigSpec.DoubleValue MAX_MOVEMENT_PENALTY;
    public static final ForgeConfigSpec.IntValue UPDATE_INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue AFFECT_CREATIVE;
    public static final ForgeConfigSpec.BooleanValue AFFECT_SPECTATOR;
    public static final ForgeConfigSpec.DoubleValue REALISM_SPRINT_PENALTY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue REALISM_MAX_JUMP_PENALTY;
    public static final ForgeConfigSpec.BooleanValue REALISM_ENABLE_HUNGER_DRAIN;
    public static final ForgeConfigSpec.DoubleValue REALISM_HUNGER_EXHAUSTION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_WEIGHTS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");
        ENABLE_SYSTEM = builder
                .comment("Master switch for Encumbrance weight penalties.")
                .define("enableSystem", true);
        REALISM_MODE = builder
                .comment("Reserved for stricter future balance rules. Currently kept as a compatibility setting.")
                .define("realismMode", false);
        MAX_WEIGHT_BEFORE_PENALTY = builder
                .comment("Total carried weight a player can carry before movement penalties begin.")
                .defineInRange("maxWeightBeforePenalty", 64.0D, 0.0D, 100000.0D);
        MAX_MOVEMENT_PENALTY = builder
                .comment("Maximum movement speed multiplier penalty. 0.25 means movement can become up to 25% slower.")
                .defineInRange("maxMovementPenalty", 0.25D, 0.0D, 0.95D);
        UPDATE_INTERVAL_TICKS = builder
                .comment("How often player carried weight is recalculated, in ticks. 20 ticks is about one second.")
                .defineInRange("updateIntervalTicks", 20, 1, 1200);
        AFFECT_CREATIVE = builder
                .comment("Whether creative mode players receive Encumbrance movement penalties.")
                .define("affectCreative", false);
        AFFECT_SPECTATOR = builder
                .comment("Whether spectator mode players receive Encumbrance movement penalties.")
                .define("affectSpectator", false);
        builder.pop();

        builder.push("realism");
        REALISM_SPRINT_PENALTY_MULTIPLIER = builder
                .comment("Extra multiplier applied to the movement penalty while sprinting in realism mode. The final penalty is still capped by maxMovementPenalty.")
                .defineInRange("sprintPenaltyMultiplier", 1.5D, 1.0D, 4.0D);
        REALISM_MAX_JUMP_PENALTY = builder
                .comment("Maximum fraction of jump velocity removed in realism mode. 0.15 means jumps can be up to 15% lower.")
                .defineInRange("maxJumpPenalty", 0.15D, 0.0D, 0.5D);
        REALISM_ENABLE_HUNGER_DRAIN = builder
                .comment("Whether encumbered sprinting increases hunger exhaustion in realism mode.")
                .define("enableHungerDrain", false);
        REALISM_HUNGER_EXHAUSTION = builder
                .comment("Hunger exhaustion added per update interval while encumbered and sprinting in realism mode.")
                .defineInRange("hungerExhaustionPerInterval", 0.02D, 0.0D, 4.0D);
        builder.pop();

        builder.push("weights");
        ITEM_WEIGHTS = builder
                .comment(
                        "Item weights by registry ID. Format: namespace:item_name = weight",
                        "Missing items default to 1.0.",
                        "Values below 0.0 are clamped to 0.0. Values above 3.0 are clamped to 3.0.",
                        "",
                        "Item Weight Scale:",
                        "0.0 = Weightless / ignored",
                        "0.1 = Feather, seeds, paper",
                        "0.5 = Food, sticks, leather",
                        "1.0 = Most common items",
                        "2.0 = Stone, logs, ores",
                        "3.0 = Very heavy items like anvils, obsidian, metal blocks",
                        "",
                        "Example modded entry:",
                        "modid:steel_ingot = 1.8"
                )
                .defineList("itemWeights", createDefaultWeightEntries(), value -> value instanceof String);
        builder.pop();

        COMMON_SPEC = builder.build();
    }

    private EncumbranceConfig() {
    }

    public static void onConfigLoaded(ModConfigEvent.Loading event) {
        normalizeItemWeights(event.getConfig());
    }

    public static void onConfigReloaded(ModConfigEvent.Reloading event) {
        normalizeItemWeights(event.getConfig());
    }

    public static void normalizeItemWeights(ModConfig config) {
        if (config == null || config.getType() != ModConfig.Type.COMMON || COMMON_SPEC == null || ITEM_WEIGHTS == null) {
            return;
        }

        List<String> currentEntries = ITEM_WEIGHTS.get().stream()
                .map(String::valueOf)
                .toList();
        List<String> defaultEntries = createDefaultWeightEntries();
        Set<String> configuredIds = configuredIds(currentEntries);

        List<String> normalizedEntries = new ArrayList<>(currentEntries.size() + defaultEntries.size());
        normalizedEntries.addAll(currentEntries);
        for (String defaultEntry : defaultEntries) {
            String id = entryId(defaultEntry);
            if (id != null && !configuredIds.contains(id)) {
                normalizedEntries.add(defaultEntry);
            }
        }

        if (normalizedEntries.size() != currentEntries.size()) {
            ITEM_WEIGHTS.set(normalizedEntries);
            ITEM_WEIGHTS.save();
            COMMON_SPEC.save();
        }
    }

    private static List<String> createDefaultWeightEntries() {
        Map<ResourceLocation, Double> overrides = createDefaultWeightOverrides();
        List<ResourceLocation> ids = BuiltInRegistries.ITEM.keySet().stream()
                .filter(EncumbranceConfig::isVanillaId)
                .sorted()
                .toList();

        List<String> entries = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            Item item = BuiltInRegistries.ITEM.get(id);
            double weight = overrides.getOrDefault(id, defaultWeightFor(id, item));
            entries.add(id + " = " + weight);
        }

        return entries;
    }

    private static Set<String> configuredIds(List<String> entries) {
        Set<String> ids = new HashSet<>();
        for (String entry : entries) {
            String id = entryId(entry);
            if (id != null) {
                ids.add(id);
            }
        }

        return ids;
    }

    private static String entryId(String entry) {
        String[] parts = entry.split("=", 2);
        if (parts.length != 2) {
            return null;
        }

        ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
        return id == null ? null : id.toString();
    }

    private static boolean isVanillaId(ResourceLocation id) {
        return "minecraft".equals(id.getNamespace());
    }

    private static Map<ResourceLocation, Double> createDefaultWeightOverrides() {
        Map<ResourceLocation, Double> overrides = new HashMap<>();

        put(overrides, 3.0D,
                "minecraft:anvil",
                "minecraft:chipped_anvil",
                "minecraft:damaged_anvil",
                "minecraft:obsidian",
                "minecraft:crying_obsidian",
                "minecraft:iron_block",
                "minecraft:gold_block",
                "minecraft:netherite_block",
                "minecraft:raw_iron_block",
                "minecraft:raw_gold_block",
                "minecraft:raw_copper_block");

        put(overrides, 2.0D,
                "minecraft:stone",
                "minecraft:cobblestone",
                "minecraft:deepslate",
                "minecraft:cobbled_deepslate",
                "minecraft:netherrack",
                "minecraft:end_stone",
                "minecraft:oak_log",
                "minecraft:spruce_log",
                "minecraft:birch_log",
                "minecraft:jungle_log",
                "minecraft:acacia_log",
                "minecraft:dark_oak_log",
                "minecraft:mangrove_log",
                "minecraft:cherry_log",
                "minecraft:crimson_stem",
                "minecraft:warped_stem");

        put(overrides, 0.1D,
                "minecraft:feather",
                "minecraft:paper",
                "minecraft:wheat_seeds",
                "minecraft:pumpkin_seeds",
                "minecraft:melon_seeds",
                "minecraft:beetroot_seeds",
                "minecraft:torchflower_seeds");

        return overrides;
    }

    private static void put(Map<ResourceLocation, Double> overrides, double weight, String... ids) {
        for (String id : ids) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location != null) {
                overrides.put(location, weight);
            }
        }
    }

    private static double defaultWeightFor(ResourceLocation id, Item item) {
        String path = id.getPath();

        if (isHeavyBlock(path)) {
            return 3.0D;
        }

        if (isStoneLogOrOre(path)) {
            return 2.0D;
        }

        if (isToolWeaponOrArmor(item, path)) {
            return toolArmorWeight(path);
        }

        if (isLightItem(path)) {
            return 0.2D;
        }

        if (isFoodLeatherOrWool(item, path)) {
            return 0.6D;
        }

        return 1.0D;
    }

    private static boolean isHeavyBlock(String path) {
        return path.contains("anvil")
                || path.contains("obsidian")
                || path.equals("iron_block")
                || path.equals("gold_block")
                || path.equals("netherite_block")
                || path.equals("raw_iron_block")
                || path.equals("raw_gold_block")
                || path.equals("raw_copper_block");
    }

    private static boolean isStoneLogOrOre(String path) {
        return path.contains("stone")
                || path.contains("deepslate")
                || path.contains("netherrack")
                || path.contains("end_stone")
                || path.endsWith("_log")
                || path.endsWith("_wood")
                || path.endsWith("_stem")
                || path.endsWith("_hyphae")
                || path.contains("_ore");
    }

    private static boolean isToolWeaponOrArmor(Item item, String path) {
        return item instanceof ArmorItem
                || item instanceof TieredItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem
                || item instanceof ShieldItem
                || item instanceof FishingRodItem
                || item instanceof ShearsItem
                || item instanceof FlintAndSteelItem
                || path.endsWith("_helmet")
                || path.endsWith("_chestplate")
                || path.endsWith("_leggings")
                || path.endsWith("_boots")
                || path.endsWith("_sword")
                || path.endsWith("_axe")
                || path.endsWith("_pickaxe")
                || path.endsWith("_shovel")
                || path.endsWith("_hoe");
    }

    private static double toolArmorWeight(String path) {
        if (path.endsWith("_chestplate") || path.endsWith("_leggings")) {
            return 1.8D;
        }

        if (path.endsWith("_helmet") || path.endsWith("_boots") || path.endsWith("_axe") || path.endsWith("_pickaxe")) {
            return 1.5D;
        }

        return 1.2D;
    }

    private static boolean isLightItem(String path) {
        return path.contains("feather")
                || path.contains("seeds")
                || path.contains("paper")
                || path.contains("string")
                || path.contains("bamboo")
                || path.contains("kelp")
                || path.contains("flower")
                || path.contains("petal");
    }

    private static boolean isFoodLeatherOrWool(Item item, String path) {
        return new ItemStack(item).isEdible()
                || path.contains("leather")
                || path.contains("wool")
                || path.contains("carpet")
                || path.contains("bread")
                || path.contains("apple")
                || path.contains("beef")
                || path.contains("porkchop")
                || path.contains("chicken")
                || path.contains("mutton")
                || path.contains("rabbit")
                || path.contains("cod")
                || path.contains("salmon")
                || path.contains("cookie")
                || path.contains("potato")
                || path.contains("carrot")
                || path.contains("beetroot");
        }
}
