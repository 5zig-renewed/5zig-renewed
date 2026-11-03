package eu.the5zig.mod.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FiveZigFabric implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("5zig");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[5zig Renewed] Fabric entrypoint reached");
    }
}
