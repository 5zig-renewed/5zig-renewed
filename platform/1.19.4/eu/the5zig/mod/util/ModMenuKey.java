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

import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.gui.Gui;
import eu.the5zig.mod.gui.GuiSettings;
import eu.the5zig.mod.gui.IGuiHandle;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Opens the 5zig menu from the keyboard.
 * <p>
 * The core binds {@code the5zigmod.key.toggle_mod} (M) and handles it in
 * {@code KeybindingListener#onTick} by flipping the {@code showMod} config flag - the 1.15
 * behaviour of that binding. No core path opens {@link GuiSettings}: it is only reachable through
 * {@code ClassProxyCallbackImpl#displayGuiSettings}, which 1.15 called from a pause-menu button
 * that this port does not have, so on 1.19.x the mod menu could not be opened at all.
 * <p>
 * This class gives that binding the meaning a "menu" key is expected to have: one press opens the
 * main 5zig menu. HUD visibility stays configurable from that menu ("Show 5zig Mod" in the
 * {@code main} category), and pressing M in the menu is ignored so it cannot stack screens.
 * <p>
 * The press is recorded by the keyboard hook ({@link #onKeyPress(int)}) and acted on during the
 * client tick ({@link #tick()}): reacting inside the GLFW callback would run before
 * {@code KeyboardHandler} has finished with the event, and vanilla records the mapping click only
 * after the hook.
 */
public final class ModMenuKey {

	private static boolean pending;
	private static boolean failureLogged;

	private ModMenuKey() {
	}

	/**
	 * Records a key press. Called from the tick that drains the keyboard hook queue, for every GLFW
	 * key press.
	 * <p>
	 * Unlike a vanilla key mapping this also works from a menu, so the menu is reachable without
	 * having to load a world first. It is ignored while a text field has focus, otherwise typing an
	 * "m" in chat would open the menu, and while one of the mod's own screens is open.
	 */
	public static void onKeyPress(int keyCode) {
		if (keyCode == GLFW.GLFW_KEY_UNKNOWN) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || !accepts(minecraft.screen)) {
			return;
		}
		KeyMapping mapping = Keybinds.mapping(Keybinds.TOGGLE_MOD);
		if (mapping != null && keyCode == Keybinds.keyCodeOf(mapping)) {
			pending = true;
		}
	}

	/**
	 * @return whether a press of the menu key should be acted on while {@code screen} is open.
	 */
	private static boolean accepts(Screen screen) {
		if (screen == null) {
			return true;
		}
		if (screen instanceof IGuiHandle) {
			// One of the mod's own screens is already open.
			return false;
		}
		GuiEventListener focused = screen.getFocused();
		return !(focused instanceof EditBox) || !((EditBox) focused).canConsumeInput();
	}

	/**
	 * Acts on a recorded press. Called once per client tick, before the core's tick listeners.
	 */
	public static void tick() {
		if (!pending) {
			return;
		}
		pending = false;
		if (!CoreState.isReady()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || !accepts(minecraft.screen)) {
			// A screen that takes text input opened between the press and this tick; drop the press.
			return;
		}

		KeyMapping mapping = Keybinds.mapping(Keybinds.TOGGLE_MOD);
		if (mapping != null) {
			// Vanilla counted the press as a click on the mapping. Drain it, otherwise the core's
			// KeybindingListener would also flip showMod on the same press and hide the HUD every
			// time the menu is opened.
			while (mapping.consumeClick()) {
				// drain
			}
		}

		Screen previous = minecraft.screen;
		try {
			Gui lastScreen = previous == null ? null : The5zigMod.getVars().createWrappedGui(previous);
			// The core's own entry point for the menu screen (GuiSettings in the "main" category).
			MinecraftFactory.getClassProxyCallback().displayGuiSettings(lastScreen);
			The5zigMod.logger.info("[5zig Renewed] Opened the 5zig menu (binding '{}')", Keybinds.TOGGLE_MOD);
		} catch (Throwable throwable) {
			if (!failureLogged) {
				failureLogged = true;
				The5zigMod.logger.error("[5zig Renewed] Could not open the 5zig menu", throwable);
			}
			// Minecraft#setScreen assigns the new screen before running its init(), so a throwing
			// init leaves a half-built screen behind. Put the previous one back if possible.
			try {
				minecraft.setScreen(previous);
			} catch (Throwable ignored) {
				// Nothing else can be done from here.
			}
		}
	}
}
