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

import eu.the5zig.mod.BytecodeHook;
import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.util.CoreState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of 1.15's {@code MixinGuiDisconnected}: starts the auto-reconnect countdown and draws it.
 */
@Mixin(DisconnectedScreen.class)
public abstract class MixinDisconnectedScreen {

	@Shadow
	@Final
	private Screen parent;

	@Inject(method = "init()V", at = @At("HEAD"))
	private void the5zig$init(CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			BytecodeHook.onGuiDisconnectedInit(parent);
		} catch (Throwable ignored) {
			// Auto-reconnect is optional.
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
	private void the5zig$render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			Minecraft minecraft = Minecraft.getInstance();
			MinecraftFactory.getClassProxyCallback().checkAutoreconnectCountdown(
					minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
		} catch (Throwable ignored) {
			// Auto-reconnect is optional.
		}
	}
}
