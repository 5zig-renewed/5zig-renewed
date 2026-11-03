/*
 * Copyright (c) 2019-2020 5zig Reborn
 *
 * This file is part of The 5zig Mod
 * The 5zig Mod is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The 5zig Mod is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with The 5zig Mod.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.the5zig.mod.fabric;

import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.util.CoreState;
import eu.the5zig.mod.util.Keybinds;
import eu.the5zig.mod.asm.Transformer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fabric client entrypoint for The 5zig Mod.
 * <p>
 * Fabric invokes {@code onInitializeClient()} from the very top of {@code Minecraft.<init>}, right
 * after the run directory has been assigned — at that point {@code Minecraft.getInstance()} works
 * but {@code options}, {@code window}, {@code font}, {@code user} and the resource manager do not
 * exist yet. The 5zig core needs all of those (its {@code DataManager} reads the game profile on
 * construction), so initialisation is split in two:
 * <ol>
 *     <li>here: register the key bindings, which <em>must</em> happen before {@code Options} is
 *     constructed, and wire the lifecycle/tick callbacks;</li>
 *     <li>{@link ClientLifecycleEvents#CLIENT_STARTED}: run {@link The5zigMod#init()}, which is
 *     the same moment the 1.15 Forge build picked (first screen / end of client bootstrap).</li>
 * </ol>
 * The 1.15 build performed {@code The5zigMod.init()} lazily from {@code MixinMinecraft}'s
 * {@code displayGuiScreen} hook; that is kept as a fallback in {@code MixinMinecraft} so the mod
 * still comes up if {@code CLIENT_STARTED} never fires.
 */
public class FiveZigFabric implements ClientModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("5zig");

	private static volatile boolean coreReady;
	private static boolean initialisationAttempted;
	private static boolean tickFailureLogged;
	private static boolean initFailureLogged;

	@Override
	public void onInitializeClient() {
		Transformer.FABRIC = true;
		Transformer.FORGE = false;

		// Must run before Minecraft constructs its Options instance.
		Keybinds.setRegistrar(KeyBindingHelper::registerKeyBinding);
		Keybinds.bootstrap();
		Keybinds.closeRegistrationWindow();

		ClientLifecycleEvents.CLIENT_STARTED.register(this::onClientStarted);
		ClientLifecycleEvents.CLIENT_STOPPING.register(this::onClientStopping);
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		LOGGER.info("[5zig Renewed] Fabric entrypoint reached; core initialisation deferred until the client is started");
	}

	/**
	 * @return true once {@link The5zigMod#init()} has completed successfully.
	 */
	public static boolean isCoreReady() {
		return coreReady;
	}

	private void onClientStarted(Minecraft client) {
		initialiseCore();
	}

	private void onClientStopping(Minecraft client) {
		if (!coreReady) {
			return;
		}
		try {
			The5zigMod.shutdown();
		} catch (Throwable throwable) {
			LOGGER.warn("[5zig Renewed] Could not shut the 5zig Mod down cleanly", throwable);
		} finally {
			coreReady = false;
			CoreState.setReady(false);
		}
	}

	private void onClientTick(Minecraft client) {
		if (!coreReady || !The5zigMod.hasBeenInitialized()) {
			return;
		}
		try {
			The5zigMod.getListener().onTick();
		} catch (Throwable throwable) {
			if (!tickFailureLogged) {
				tickFailureLogged = true;
				LOGGER.error("[5zig Renewed] Tick handler failed; further tick errors will be suppressed", throwable);
			}
		}
	}

	/**
	 * Runs the core's init path once. Safe to call repeatedly and from mixins: the core itself
	 * guards against double initialisation, but throwing out of a mixin would kill the game.
	 */
	public static synchronized void initialiseCore() {
		if (coreReady) {
			return;
		}
		MinecraftFactory.bootstrap(new eu.the5zig.mod.platform.Variables());
		if (The5zigMod.hasBeenInitialized()) {
			// Initialised by another path (e.g. the core's own lazy init); nothing to do.
			coreReady = true;
			return;
		}
		if (initialisationAttempted) {
			return;
		}
		initialisationAttempted = true;
		long start = System.currentTimeMillis();
		try {
			The5zigMod.init();
			coreReady = true;
			CoreState.setReady(true);
			LOGGER.info("[5zig Renewed] Core initialised in {} ms", System.currentTimeMillis() - start);
		} catch (Throwable throwable) {
			if (!initFailureLogged) {
				initFailureLogged = true;
				LOGGER.error("[5zig Renewed] The 5zig Mod could not be initialised; the HUD and all hooks stay disabled", throwable);
			}
		}
	}
}
