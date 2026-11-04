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

import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.util.CoreState;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screen-change hook.
 * <p>
 * 1.15/1.20.1 opened screens through {@code Minecraft#setScreen}; 26.2 moved that method onto
 * {@link Gui} (and {@code Minecraft#setScreenAndShow} just calls {@code gui.setScreen} plus a
 * render), so the text-field flush follows it here. The HUD hooks live in {@link MixinHud},
 * because 26.2 split the in-game overlay out of {@code Gui} into a separate {@code Hud} class.
 */
@Mixin(Gui.class)
public abstract class MixinGui {

	/**
	 * 1.15 cleared the text field registry whenever a screen was opened. The mod itself is
	 * initialised from {@code ClientLifecycleEvents}, not from here, so that a failure can never
	 * take down {@code Gui#setScreen}.
	 */
	@Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
	private void the5zig$setScreen(Screen screen, CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			The5zigMod.getKeybindingManager().flushTextfields();
		} catch (Throwable ignored) {
			// Text field bookkeeping is best effort.
		}
	}
}
