package com.lefoxxy.encumbrance.event;

import com.lefoxxy.encumbrance.EncumbranceMod;
import com.lefoxxy.encumbrance.config.EncumbranceConfig;
import com.lefoxxy.encumbrance.weight.WeightManager;
import com.lefoxxy.encumbrance.weight.WeightSnapshot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public final class PlayerMovementHandler {
    private static final UUID ENCUMBRANCE_SPEED_MODIFIER_ID = UUID.fromString("4a10fa13-559f-4aa6-a2c0-77155c522e6d");
    private static final String ENCUMBRANCE_SPEED_MODIFIER_NAME = EncumbranceMod.MOD_ID + ".movement_penalty";

    private PlayerMovementHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        removeMovementPenalty(movementSpeed);

        if (!WeightManager.shouldAffect(player)) {
            WeightManager.clearCachedSnapshot(player);
            return;
        }

        WeightSnapshot snapshot = WeightManager.getOrUpdateSnapshot(player);
        if (!snapshot.isEncumbered()) {
            return;
        }

        addMovementPenalty(movementSpeed, -calculateMovementPenalty(player, snapshot));
        applyRealismHungerDrain(player, snapshot);
    }

    @SubscribeEvent
    public static void onLivingJump(LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }

        if (!WeightManager.shouldAffect(player) || !EncumbranceConfig.REALISM_MODE.get()) {
            return;
        }

        WeightSnapshot snapshot = WeightManager.getOrUpdateSnapshot(player);
        if (!snapshot.isEncumbered()) {
            return;
        }

        double jumpPenalty = EncumbranceConfig.REALISM_MAX_JUMP_PENALTY.get() * snapshot.penaltyRatio();
        if (jumpPenalty <= 0.0D) {
            return;
        }

        player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 1.0D - jumpPenalty, 1.0D));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        cleanupPlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        WeightManager.clearCachedSnapshot(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanupPlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        cleanupPlayer(event.getOriginal());
        WeightManager.clearCachedSnapshot(event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            cleanupPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        WeightManager.clearCachedSnapshot(event.getEntity());
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        WeightManager.clearCachedSnapshot(event.getPlayer());
    }

    private static void addMovementPenalty(AttributeInstance movementSpeed, double penalty) {
        movementSpeed.addTransientModifier(new AttributeModifier(
                ENCUMBRANCE_SPEED_MODIFIER_ID,
                ENCUMBRANCE_SPEED_MODIFIER_NAME,
                penalty,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        ));
    }

    private static double calculateMovementPenalty(Player player, WeightSnapshot snapshot) {
        double maxPenalty = EncumbranceConfig.MAX_MOVEMENT_PENALTY.get();
        double penalty = maxPenalty * snapshot.penaltyRatio();

        if (EncumbranceConfig.REALISM_MODE.get() && player.isSprinting()) {
            penalty *= EncumbranceConfig.REALISM_SPRINT_PENALTY_MULTIPLIER.get();
        }

        return Math.max(0.0D, Math.min(maxPenalty, penalty));
    }

    private static void applyRealismHungerDrain(Player player, WeightSnapshot snapshot) {
        if (!EncumbranceConfig.REALISM_MODE.get()
                || !EncumbranceConfig.REALISM_ENABLE_HUNGER_DRAIN.get()
                || !player.isSprinting()
                || !WeightManager.shouldUpdate(player)) {
            return;
        }

        float exhaustion = (float) (EncumbranceConfig.REALISM_HUNGER_EXHAUSTION.get() * snapshot.penaltyRatio());
        if (exhaustion > 0.0F) {
            player.causeFoodExhaustion(exhaustion);
        }
    }

    private static void cleanupPlayer(Player player) {
        if (!player.level().isClientSide()) {
            AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (movementSpeed != null) {
                removeMovementPenalty(movementSpeed);
            }
            WeightManager.clearCachedSnapshot(player);
        }
    }

    private static void removeMovementPenalty(AttributeInstance movementSpeed) {
        movementSpeed.removeModifier(ENCUMBRANCE_SPEED_MODIFIER_ID);
    }
}
