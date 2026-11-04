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

package eu.the5zig.mod.neoforge;

import eu.the5zig.mod.BytecodeHook;
import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.asm.Transformer;
import eu.the5zig.mod.platform.PluginMessages;
import eu.the5zig.mod.util.CoreState;
import eu.the5zig.mod.util.Keybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge 1.20.2 client entrypoint for The 5zig Mod.
 * <p>
 * 1.20.2 is the very first real {@code net.neoforged.*} loader (FML 1.0): {@code @Mod} takes only
 * the mod id and the constructor is injected with the mod event bus. The tick event still has the
 * pre-1.20.5 {@link TickEvent.ClientTickEvent} + {@link TickEvent.Phase} shape, and networking is
 * the Forge-derived {@code EventNetworkChannel} API (there is no payload registrar yet), which is
 * why {@link PluginMessages#bootstrap()} has to run from this constructor: the channel registry
 * locks as soon as mod loading ends.
 */
@Mod(FiveZigNeoForge.MOD_ID)
public final class FiveZigNeoForge {

	public static final String MOD_ID = "the5zigmod";
	public static final Logger LOGGER = LogManager.getLogger("5zig");

	/**
	 * Bindings created before {@link RegisterKeyMappingsEvent} fires; the loader only accepts them there.
	 */
	static final List<KeyMapping> PENDING_MAPPINGS = new ArrayList<>();

	private static volatile boolean coreReady;
	private static boolean initialisationAttempted;
	private static boolean tickFailureLogged;
	private static boolean initFailureLogged;

	public FiveZigNeoForge(IEventBus modEventBus) {
		Transformer.FORGE = true;
		Transformer.FABRIC = false;

		// Must happen before Options is constructed, i.e. before RegisterKeyMappingsEvent fires.
		Keybinds.setRegistrar(FiveZigNeoForge::collectMapping);
		Keybinds.bootstrap();

		// Declares the 5zig plugin channels. 1.20.2 has no payload-registration event: the
		// channels are created through NetworkRegistry and must exist before the registry locks,
		// so they are created here rather than from an event handler.
		PluginMessages.bootstrap();

		// Mod bus: key mappings.
		modEventBus.addListener(this::onRegisterKeyMappings);

		// Game bus: the per-tick driver.
		NeoForge.EVENT_BUS.addListener(this::onClientTick);

		LOGGER.info("[5zig Renewed] NeoForge 1.20.2 mod constructed; pending={} known={}",
				PENDING_MAPPINGS.size(), Keybinds.count());
	}

	static synchronized void collectMapping(KeyMapping mapping) {
		PENDING_MAPPINGS.add(mapping);
	}

	private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		for (KeyMapping mapping : PENDING_MAPPINGS) {
			event.register(mapping);
		}
		Keybinds.closeRegistrationWindow();
		LOGGER.info("[5zig Renewed] Registered {} key bindings", PENDING_MAPPINGS.size());
	}

	/**
	 * Runs the core's init path once. Never throws: a failure disables the mod instead of the game.
	 */
	static synchronized void initialiseCore() {
		try {
			// The core's static initialiser resolves the platform immediately, and the mod class
			// loader cannot look it up by name, so install it before The5zigMod is touched.
			if (MinecraftFactory.getVarsOrNull() == null) {
				MinecraftFactory.bootstrap((eu.the5zig.mod.util.IVariables)
						Class.forName("eu.the5zig.mod.platform.Variables").getDeclaredConstructor().newInstance());
			}
			if (coreReady) {
				return;
			}
			// Touching The5zigMod triggers class verification, which resolves its optional
			// dependencies (DiscordIPC, sqlite). Keep that inside the guard: a missing library
			// must disable the mod, not kill the client.
			if (The5zigMod.hasBeenInitialized()) {
				coreReady = true;
				CoreState.setReady(true);
				return;
			}
			if (initialisationAttempted) {
				return;
			}
			initialisationAttempted = true;
			long start = System.currentTimeMillis();
			The5zigMod.init();
			coreReady = true;
			CoreState.setReady(true);
			LOGGER.info("[5zig Renewed] Core initialised in {} ms", System.currentTimeMillis() - start);
		} catch (Throwable throwable) {
			if (!initFailureLogged) {
				initFailureLogged = true;
				LOGGER.error("[5zig Renewed] The 5zig Mod could not be initialised; HUD and hooks stay disabled", throwable);
			}
		}
	}

	private void onClientTick(TickEvent.ClientTickEvent event) {
		// 1.20.2 fires the client tick twice per tick; only the END phase runs post-update, which
		// is what the core expects (it reads the state the game just produced).
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (!coreReady) {
			if (minecraft.options == null || minecraft.getWindow() == null) {
				return;
			}
			initialiseCore();
			return;
		}
		if (!The5zigMod.hasBeenInitialized()) {
			return;
		}
		try {
			BytecodeHook.onDispatchKeyPresses();
			The5zigMod.getListener().onTick();
		} catch (Throwable throwable) {
			if (!tickFailureLogged) {
				tickFailureLogged = true;
				LOGGER.error("[5zig Renewed] Tick handler failed; further tick errors will be suppressed", throwable);
			}
		}
	}
}
