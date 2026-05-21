package com.lefoxxy.encumbrance.weight;

import com.lefoxxy.encumbrance.EncumbranceMod;
import com.lefoxxy.encumbrance.config.EncumbranceConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WeightManager {
    private static final double DEFAULT_ITEM_WEIGHT = 1.0D;
    private static final double MIN_ITEM_WEIGHT = 0.0D;
    private static final double MAX_ITEM_WEIGHT = 3.0D;
    private static final WeightSnapshot EMPTY_CLIENT_SNAPSHOT = new WeightSnapshot(0.0D, 0.0D, 0.0D);
    private static final Map<UUID, CachedWeight> PLAYER_WEIGHT_CACHE = new HashMap<>();
    private static List<? extends String> cachedEntries = List.of();
    private static Map<ResourceLocation, Double> cachedWeights = Map.of();

    private WeightManager() {
    }

    private static WeightSnapshot createSnapshot(Player player) {
        double currentWeight = calculateCarriedWeight(player);
        double maxWeightBeforePenalty = EncumbranceConfig.MAX_WEIGHT_BEFORE_PENALTY.get();
        double penaltyRatio = calculatePenaltyRatio(currentWeight, maxWeightBeforePenalty);

        return new WeightSnapshot(currentWeight, maxWeightBeforePenalty, penaltyRatio);
    }

    public static double calculateCarriedWeight(Player player) {
        double total = 0.0D;

        for (ItemStack stack : player.getInventory().items) {
            total += getStackWeight(stack);
        }

        for (ItemStack stack : player.getInventory().offhand) {
            total += getStackWeight(stack);
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                total += getStackWeight(player.getItemBySlot(slot));
            }
        }

        return total;
    }

    public static double getStackWeight(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }

        return getItemWeight(stack) * stack.getCount();
    }

    public static double getItemWeight(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) {
            return DEFAULT_ITEM_WEIGHT;
        }

        return configuredWeights().getOrDefault(key, DEFAULT_ITEM_WEIGHT);
    }

    private static double calculatePenaltyRatio(double currentWeight, double maxWeightBeforePenalty) {
        if (currentWeight <= maxWeightBeforePenalty) {
            return 0.0D;
        }

        if (maxWeightBeforePenalty <= 0.0D) {
            return 1.0D;
        }

        double ratio = (currentWeight - maxWeightBeforePenalty) / maxWeightBeforePenalty;
        return Math.max(0.0D, Math.min(1.0D, ratio));
    }

    private static synchronized Map<ResourceLocation, Double> configuredWeights() {
        List<? extends String> entries = EncumbranceConfig.ITEM_WEIGHTS.get();
        if (entries.equals(cachedEntries)) {
            return cachedWeights;
        }

        Map<ResourceLocation, Double> weights = new HashMap<>();
        for (String entry : entries) {
            parseWeightEntry(entry, weights);
        }

        cachedEntries = List.copyOf(entries);
        cachedWeights = Map.copyOf(weights);
        return cachedWeights;
    }

    private static void parseWeightEntry(String entry, Map<ResourceLocation, Double> weights) {
        String[] parts = entry.split("=", 2);
        if (parts.length != 2) {
            EncumbranceMod.LOGGER.warn("Ignoring malformed Encumbrance weight entry '{}'. Expected format: namespace:item = weight", entry);
            return;
        }

        String rawId = parts[0].trim();
        String rawWeight = parts[1].trim();
        ResourceLocation location = ResourceLocation.tryParse(rawId);
        if (location == null || !isKnownItemOrBlock(location)) {
            EncumbranceMod.LOGGER.warn("Ignoring Encumbrance weight entry with unknown registry ID '{}'.", rawId);
            return;
        }

        try {
            weights.put(location, clamp(Double.parseDouble(rawWeight), MIN_ITEM_WEIGHT, MAX_ITEM_WEIGHT));
        } catch (NumberFormatException exception) {
            EncumbranceMod.LOGGER.warn("Ignoring Encumbrance weight entry '{}' because '{}' is not a number.", entry, rawWeight);
        }
    }

    private static boolean isKnownItemOrBlock(ResourceLocation location) {
        return ForgeRegistries.ITEMS.containsKey(location) || ForgeRegistries.BLOCKS.containsKey(location);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (Double.isNaN(value)) {
            return DEFAULT_ITEM_WEIGHT;
        }

        if (value < minimum) {
            return minimum;
        }

        if (value > maximum) {
            return maximum;
        }

        return value;
    }

    public static boolean shouldAffect(Player player) {
        if (!EncumbranceConfig.ENABLE_SYSTEM.get()) {
            return false;
        }

        if (player.isCreative() && !EncumbranceConfig.AFFECT_CREATIVE.get()) {
            return false;
        }

        if (player.isSpectator() && !EncumbranceConfig.AFFECT_SPECTATOR.get()) {
            return false;
        }

        return true;
    }

    public static boolean shouldUpdate(Player player) {
        int interval = Math.max(1, EncumbranceConfig.UPDATE_INTERVAL_TICKS.get());
        return player.tickCount % interval == 0;
    }

    public static void clearCachedSnapshot(Player player) {
        PLAYER_WEIGHT_CACHE.remove(player.getUUID());
    }

    public static synchronized void clearAllCachedSnapshots() {
        PLAYER_WEIGHT_CACHE.clear();
    }

    public static synchronized WeightSnapshot getOrUpdateSnapshot(Player player) {
        if (player.level().isClientSide()) {
            return EMPTY_CLIENT_SNAPSHOT;
        }

        UUID playerId = player.getUUID();
        CachedWeight cached = PLAYER_WEIGHT_CACHE.get(playerId);
        if (cached != null && player.tickCount < cached.nextUpdateTick()) {
            return cached.snapshot();
        }

        WeightSnapshot snapshot = createSnapshot(player);
        int interval = Math.max(1, EncumbranceConfig.UPDATE_INTERVAL_TICKS.get());
        PLAYER_WEIGHT_CACHE.put(playerId, new CachedWeight(snapshot, player.tickCount + interval));
        return snapshot;
    }

    private record CachedWeight(WeightSnapshot snapshot, int nextUpdateTick) {
    }
}
