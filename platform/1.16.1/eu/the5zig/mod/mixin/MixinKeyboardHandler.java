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

package eu.the5zig.mod.mixin;

import eu.the5zig.mod.util.CoreState;
import eu.the5zig.mod.util.KeyDispatch;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keyboard hook for 1.16.1.
 * <p>
 * 1.15 drove every hotkey of the mod from {@code Minecraft#dispatchKeypresses()}, which drained the
 * LWJGL2 keyboard queue once per tick and fired a {@link eu.the5zig.mod.event.KeyPressEvent} for
 * each press (via {@code BytecodeHook#onDispatchKeyPresses} → {@code IVariables#dispatchKeypresses}
 * → {@code ClassProxyCallback#fireKeyPressEvent}). Neither that hook nor a replacement was wired in
 * this port: {@code fireKeyPressEvent} had no caller at all, so {@code KeybindingManager#onKeyPress}
 * never ran and every binding in its {@code globalKeybindings} map (toggle chat, TeamSpeak, name
 * history, ...) was dead.
 * <p>
 * 1.16.1 has no pollable queue - {@code KeyboardHandler} receives the GLFW event directly - so the
 * presses are queued here and drained from the tick ({@link KeyDispatch#dispatch()}). Repeats are
 * dropped: the core treats a press as an edge, and 1.15 only delivered key-down events.
 */
@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {

	@Inject(method = "keyPress(JIIII)V", at = @At("HEAD"))
	private void the5zig$onKeyPress(long window, int key, int scanCode, int action, int modifiers, CallbackInfo callbackInfo) {
		if (action != GLFW.GLFW_PRESS || !CoreState.isReady()) {
			return;
		}
		try {
			KeyDispatch.enqueue(key);
		} catch (Throwable ignored) {
			// A hotkey must never be able to break the keyboard handler.
		}
	}
}
