package dev.vicuna.sporedefender;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(SporeDefender.MODID)
public final class SporeDefender {
    public static final String MODID = "sporedefender";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SporeDefender(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SporeDefenderConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(SporeDefenderCommands::register);
        NeoForge.EVENT_BUS.addListener(SporeDefenderEvents::onEntityJoinLevel);
    }
}
