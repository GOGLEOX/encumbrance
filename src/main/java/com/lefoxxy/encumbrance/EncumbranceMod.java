package com.lefoxxy.encumbrance;

import com.lefoxxy.encumbrance.command.EncumbranceCommands;
import com.lefoxxy.encumbrance.config.EncumbranceConfig;
import com.lefoxxy.encumbrance.event.PlayerMovementHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(EncumbranceMod.MOD_ID)
public final class EncumbranceMod {
    public static final String MOD_ID = "encumbrance";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EncumbranceMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EncumbranceConfig.COMMON_SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EncumbranceConfig::onConfigLoaded);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EncumbranceConfig::onConfigReloaded);

        MinecraftForge.EVENT_BUS.register(PlayerMovementHandler.class);
        MinecraftForge.EVENT_BUS.register(EncumbranceCommands.class);
    }
}
