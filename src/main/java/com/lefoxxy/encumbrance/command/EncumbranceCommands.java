package com.lefoxxy.encumbrance.command;

import com.lefoxxy.encumbrance.config.EncumbranceConfig;
import com.lefoxxy.encumbrance.weight.WeightManager;
import com.lefoxxy.encumbrance.weight.WeightSnapshot;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

public final class EncumbranceCommands {
    private EncumbranceCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("encumbrance")
                .then(Commands.literal("weight")
                        .executes(context -> showWeight(context.getSource())))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reload(context.getSource()))));
    }

    private static int showWeight(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Only players can check carried weight."));
            return 0;
        }

        WeightSnapshot snapshot = WeightManager.getOrUpdateSnapshot(player);
        int penaltyPercent = (int) Math.round(snapshot.penaltyRatio() * 100.0D);

        source.sendSuccess(() -> Component.literal("Weight: ")
                .append(Component.literal(format(snapshot.currentWeight())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" / "))
                .append(Component.literal(format(snapshot.maxWeightBeforePenalty())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | Penalty: "))
                .append(Component.literal(penaltyPercent + "%").withStyle(snapshot.isEncumbered() ? ChatFormatting.RED : ChatFormatting.GREEN)), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FMLPaths.CONFIGDIR.get());
        EncumbranceConfig.normalizeItemWeights(ConfigTracker.INSTANCE.fileMap().get("encumbrance-common.toml"));
        WeightManager.clearAllCachedSnapshots();
        source.sendSuccess(() -> Component.literal("Encumbrance config reloaded and weight cache cleared."), true);
        return 1;
    }

    private static String format(double value) {
        return String.format("%.1f", value);
    }
}
