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

import com.mojang.blaze3d.platform.InputConstants;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.util.IKeybinding;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns the Fabric side of the mod's key bindings.
 * <p>
 * Fabric only accepts new key bindings while {@code Minecraft.options} is still unset; once the
 * {@link Options} instance exists, {@link KeyBindingHelper#registerKeyBinding(KeyMapping)} throws.
 * The game constructs {@code Options} long before any screen is shown, so the bindings have to be
 * created from the mod initialiser (which Fabric runs right at the start of
 * {@code Minecraft.<init>}) rather than from {@link The5zigMod#init()}.
 * <p>
 * {@link KeyBindingHelper} is therefore used here, at bootstrap time, and the resulting
 * {@link KeyMapping} instances are handed back to the core through {@link IKeybinding} adapters so
 * that {@code KeybindingManager} never creates a second, unregistered mapping.
 * <p>
 * The ids and defaults mirror {@code eu.the5zig.mod.manager.KeybindingManager}; if the core grows a
 * binding, {@link #wrap(String, int, String)} lazily creates it as well (appending to
 * {@code Options.keyMappings} when the game is already running).
 */
public final class FiveZigKeybinds {

	/**
	 * Translation key of the category all of the mod's bindings are grouped under.
	 */
	public static final String CATEGORY = "The 5zig Mod";

	private static final Map<String, FiveZigKeybinding> BINDINGS = new LinkedHashMap<>();

	private static boolean bootstrapped;

	private FiveZigKeybinds() {
	}

	/**
	 * Creates and registers every key binding the core asks for during {@code The5zigMod.init()}.
	 * Must run while {@code Minecraft.options} is still {@code null}.
	 */
	public static synchronized void bootstrap() {
		if (bootstrapped) {
			return;
		}
		bootstrapped = true;

		// Keep in sync with eu.the5zig.mod.manager.KeybindingManager.
		bind("the5zigmod.key.toggle_mod", GLFW.GLFW_KEY_M);
		bind("the5zigmod.key.toggle_chat", GLFW.GLFW_KEY_F4);
		bind("the5zigmod.key.coordinates_clipboard", GLFW.GLFW_KEY_F12);
		bind("the5zigmod.key.raid_calculator", GLFW.GLFW_KEY_UNKNOWN);
		bind("the5zigmod.key.hypixel_stats", GLFW.GLFW_KEY_UNKNOWN);
		bind("the5zigmod.key.zoom", GLFW.GLFW_KEY_Z);
		bind("the5zigmod.key.timer.start", GLFW.GLFW_KEY_KP_0);
		bind("the5zigmod.key.timer.reset", GLFW.GLFW_KEY_KP_DECIMAL);
		bind("the5zigmod.key.teamspeak", GLFW.GLFW_KEY_F9);
		bind("the5zigmod.key.username_history", GLFW.GLFW_KEY_UNKNOWN);
		bind("the5zigmod.key.toggle_chat_filter", GLFW.GLFW_KEY_UNKNOWN);
		bind("the5zigmod.key.change_account", GLFW.GLFW_KEY_UNKNOWN);
	}

	/**
	 * Returns the binding for the given id, creating and registering it on demand.
	 * <p>
	 * This is the method {@code Variables.createKeybinding} should delegate to on Fabric. It is
	 * safe to call at any point: before {@code Options} exists the mapping is registered through
	 * {@link KeyBindingHelper}, afterwards it is appended to {@code Options.keyMappings} directly,
	 * which is the only late-registration route Fabric leaves open.
	 */
	public static synchronized IKeybinding wrap(String description, int keyCode, String category) {
		FiveZigKeybinding existing = BINDINGS.get(description);
		if (existing != null) {
			return existing;
		}
		return bind(description, keyCode, category == null || category.isEmpty() ? CATEGORY : category);
	}

	/**
	 * @return true if the given id has been registered by this class.
	 */
	public static synchronized boolean isRegistered(String description) {
		return BINDINGS.containsKey(description);
	}

	private static FiveZigKeybinding bind(String description, int keyCode) {
		return bind(description, keyCode, CATEGORY);
	}

	private static FiveZigKeybinding bind(String description, int keyCode, String category) {
		int code = keyCode == 0 ? GLFW.GLFW_KEY_UNKNOWN : keyCode;
		KeyMapping mapping = new KeyMapping(description, code, category);
		register(mapping);
		FiveZigKeybinding binding = new FiveZigKeybinding(mapping);
		BINDINGS.put(description, binding);
		return binding;
	}

	private static void register(KeyMapping mapping) {
		if (Minecraft.getInstance().options == null) {
			KeyBindingHelper.registerKeyBinding(mapping);
			return;
		}
		// Late registration: Fabric's helper refuses to run once Options exists, so append the
		// mapping to the live array and rebuild the name -> key index.
		try {
			Options options = Minecraft.getInstance().options;
			KeyMapping[] current = options.keyMappings;
			KeyMapping[] extended = new KeyMapping[current.length + 1];
			System.arraycopy(current, 0, extended, 0, current.length);
			extended[current.length] = mapping;

			Field field = Options.class.getDeclaredField("keyMappings");
			field.setAccessible(true);
			field.set(options, extended);
			KeyMapping.resetMapping();
		} catch (Throwable throwable) {
			The5zigMod.logger.warn("Could not register key binding '" + mapping.getName() + "'", throwable);
		}
	}

	/**
	 * Adapts a vanilla {@link KeyMapping} to the 5zig key binding contract.
	 */
	private static final class FiveZigKeybinding implements IKeybinding {

		private final KeyMapping mapping;

		private FiveZigKeybinding(KeyMapping mapping) {
			this.mapping = mapping;
		}

		@Override
		public boolean callIsPressed() {
			return mapping.consumeClick();
		}

		@Override
		public int callGetKeyCode() {
			InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
			if (key.getType() == InputConstants.Type.MOUSE) {
				// 5zig encodes mouse buttons as -(100 + button); see ZoomListener.
				return -(100 + key.getValue());
			}
			return key.getValue();
		}
	}
}
