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
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.util.CoreState;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Screen-side hooks: the transparent pause-menu background, 5zig's own control-key handling and a
 * {@link RenderContext} bracket so that mod screens (and the second chat drawn from
 * {@code ChatScreen}) have a {@code GuiGraphics} available.
 */
@Mixin(Screen.class)
public abstract class MixinScreen {

	@Unique
	private boolean the5zig$ownsRenderContext;

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"))
	private void the5zig$beginRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
		// Covers both "no context yet" and a context leaked by a screen that threw last frame.
		if (the5zig$ownsRenderContext) {
			the5zig$ownsRenderContext = false;
			RenderContext.clear();
		}
		if (!RenderContext.isAvailable()) {
			RenderContext.set(graphics);
			the5zig$ownsRenderContext = true;
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("RETURN"))
	private void the5zig$endRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
		if (the5zig$ownsRenderContext) {
			the5zig$ownsRenderContext = false;
			RenderContext.clear();
		}
		// 1.21's DisconnectedScreen does not override render(), so the auto-reconnect countdown is
		// drawn from here rather than from a mixin on that class.
		if ((Object) this instanceof net.minecraft.client.gui.screens.DisconnectedScreen && CoreState.isReady()) {
			try {
				net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
				eu.the5zig.mod.MinecraftFactory.getClassProxyCallback().checkAutoreconnectCountdown(
						minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
			} catch (Throwable ignored) {
				// Auto-reconnect is optional.
			}
		}
	}

	/**
	 * 1.15 hooked {@code Screen#renderBackground(int)}; 1.21 hands the screen a
	 * {@code GuiGraphics} plus the mouse position and partial tick.
	 */
	@Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
	private void the5zig$renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			if (!The5zigMod.shouldDrawWorldBackground()) {
				callbackInfo.cancel();
			}
		} catch (Throwable ignored) {
			// Fall back to the vanilla background.
		}
	}

	/**
	 * 5zig allows swapping the drop key modifier between Ctrl and Cmd on macOS.
	 */
	@Inject(method = "hasControlDown()Z", at = @At("HEAD"), cancellable = true)
	private static void the5zig$hasControlDown(CallbackInfoReturnable<Boolean> callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			callbackInfo.setReturnValue(BytecodeHook.isCtrlKeyDown(Minecraft.ON_OSX));
		} catch (Throwable ignored) {
			// Fall back to vanilla behaviour.
		}
	}
}
