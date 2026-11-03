package eu.the5zig.mod.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(FiveZigNeoForge.MOD_ID)
public class FiveZigNeoForge {

    public static final String MOD_ID = "the5zigmod";
    public static final Logger LOGGER = LogManager.getLogger("5zig");

    public FiveZigNeoForge(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("[5zig Renewed] NeoForge mod constructor reached");
    }
}
