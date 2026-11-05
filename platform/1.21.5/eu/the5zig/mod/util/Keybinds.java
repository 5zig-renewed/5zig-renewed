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

package eu.the5zig.mod.util;

import com.mojang.blaze3d.platform.InputConstants;
import eu.the5zig.mod.The5zigMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loader-neutral owner of the mod's key bindings.
 * <p>
 * Every loader freezes its key mapping list when {@code Minecraft.options} is constructed, and the
 * core only asks for its bindings later, during {@link The5zigMod#init()}. The only portable
 * strategy is therefore to create the bindings as early as the loader allows and hand those very
 * instances to the core:
 * <ul>
 *   <li>{@link #bootstrap()} creates the twelve ids {@code KeybindingManager} uses.</li>
 *   <li>{@link #setRegistrar(Registrar)} is set by the loader to its registration hook
 *       (Fabric: {@code KeyBindingHelper}; NeoForge/Forge: the {@code RegisterKeyMappingsEvent}).</li>
 *   <li>{@link #wrap(String, int, String)} is what {@code Variables.createKeybinding} delegates to,
 *       so the core never creates a second, unregistered mapping.</li>
 * </ul>
 * Bindings created after the loader's registration window (plugin API) are appended to
 * {@code Options.keyMappings} directly — the array contents are mutable even though the field is
 * final.
 */
public final class Keybinds {

	/**
	 * Translation key of the category all of the mod's bindings are grouped under.
	 */
	public static final String CATEGORY = "The 5zig Mod";

	/**
	 * Loader hook used while the loader's registration window is still open.
	 */
	public interface Registrar {
		void register(KeyMapping mapping);
	}

	private static final Map<String, FiveZigKeybinding> BINDINGS = new LinkedHashMap<>();

	private static Registrar registrar = Keybinds::appendToOptions;

	private static boolean bootstrapped;

	private Keybinds() {
	}

	public static synchronized void setRegistrar(Registrar newRegistrar) {
		registrar = newRegistrar == null ? Keybinds::appendToOptions : newRegistrar;
	}

	/**
	 * Loaders call this once their registration window is over (Forge/NeoForge after
	 * {@code RegisterKeyMappingsEvent}, Fabric right after bootstrap). Later bindings — the plugin
	 * API can add one at runtime — are appended to {@code Options.keyMappings} instead.
	 */
	public static synchronized void closeRegistrationWindow() {
		registrar = Keybinds::appendToOptions;
	}

	/**
	 * Creates and registers every key binding the core asks for during {@code The5zigMod.init()}.
	 * Must run while the loader can still accept mappings.
	 */
	public static synchronized void bootstrap() {
		if (bootstrapped) {
			return;
		}
		bootstrapped = true;

		// Keep in sync with eu.the5zig.mod.manager.KeybindingManager.
		bind("the5zigmod.key.toggle_mod", GLFW.GLFW_KEY_M);
		// The 5zig settings GUI: nothing else in the tree opens it, so without this binding the
		// whole configuration surface (plugins, display, capes, language) is unreachable in-game.
		bind("the5zigmod.key.settings", GLFW.GLFW_KEY_RIGHT_SHIFT);
		bind("the5zigmod.key.toggle_chat", GLFW.GLFW_KEY_F4);
		bind("the5zigmod.key.coordinates_clipboard", GLFW.GLFW_KEY_F12);
		bind("the5zigmod.key.raid_calculator", GLFW.GLFW_KEY_F6);
		bind("the5zigmod.key.hypixel_stats", GLFW.GLFW_KEY_F10);
		bind("the5zigmod.key.zoom", GLFW.GLFW_KEY_Z);
		bind("the5zigmod.key.timer.start", GLFW.GLFW_KEY_KP_0);
		bind("the5zigmod.key.timer.reset", GLFW.GLFW_KEY_KP_DECIMAL);
		bind("the5zigmod.key.teamspeak", GLFW.GLFW_KEY_F9);
		bind("the5zigmod.key.username_history", GLFW.GLFW_KEY_F11);
		bind("the5zigmod.key.toggle_chat_filter", GLFW.GLFW_KEY_F8);
		bind("the5zigmod.key.change_account", GLFW.GLFW_KEY_F7);
	}

	/**
	 * Returns the binding for the given id, creating and registering it on demand.
	 */
	public static synchronized IKeybinding wrap(String description, int keyCode, String category) {
		FiveZigKeybinding existing = BINDINGS.get(description);
		if (existing != null) {
			return existing;
		}
		return bind(description, keyCode, category == null || category.isEmpty() ? CATEGORY : category);
	}

	/**
	 * Registers a mapping that was created outside {@link #wrap(String, int, String)} (the plugin
	 * API can hand one over). Uses the loader hook while it is still open, otherwise appends to
	 * {@code Options.keyMappings}.
	 */
	public static synchronized void registerFallback(KeyMapping mapping) {
		register(mapping);
	}

	public static synchronized int count() {
		return BINDINGS.size();
	}

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
		// The loader owns the policy: Fabric hands over KeyBindingHelper (valid until Options is
		// built), Forge/NeoForge collect into their RegisterKeyMappingsEvent. Once the loader closes
		// the window, the registrar is swapped for the direct Options append.
		registrar.register(mapping);
	}

	/**
	 * Appends a mapping to the live {@code Options.keyMappings} array. The field is final, but its
	 * contents are not, and every loader re-reads the array when resolving keys.
	 */
	private static void appendToOptions(KeyMapping mapping) {
		try {
			Options options = Minecraft.getInstance().options;
			if (options == null) {
				return;
			}
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
			InputConstants.Key key = InputConstants.getKey(mapping.saveString());
			if (key.getType() == InputConstants.Type.MOUSE) {
				// 5zig encodes mouse buttons as -(100 + button); see ZoomListener.
				return -(100 + key.getValue());
			}
			return key.getValue();
		}
	}
}
