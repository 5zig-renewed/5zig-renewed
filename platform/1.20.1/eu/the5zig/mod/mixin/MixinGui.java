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
import eu.the5zig.mod.fabric.FiveZigFabric;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes the frame's {@link GuiGraphics} to {@link RenderContext} and drives the mod's HUD.
 * <p>
 * 1.20.1 replaced 1.15's global {@code Tessellator} with a {@code GuiGraphics} parameter threaded
 * through every draw call, while the 5zig core still draws through {@code IVariables.drawString}
 * and friends. Setting the context for the whole {@code Gui#render} pass lets every core draw call
 * made while the HUD, the chat or an overlay is rendered find the right buffer.
 * <p>
 * The 1.15 build drew the overlay at the head of {@code IngameGui#renderHotbar} and the "text above
 * hotbar" afterwards; 1.20.1 has folded the hotbar into {@code Gui#render}, so the overlay is drawn
 * at the head of the frame and the hotbar pass is still hooked separately.
 */
@Mixin(Gui.class)
public abstract class MixinGui {

	@Unique
	private static boolean the5zig$hudFailureLogged;

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("HEAD"))
	private void the5zig$beginFrame(GuiGraphics graphics, float partialTick, CallbackInfo callbackInfo) {
		// Drop a context leaked by a frame that threw before its RETURN hook ran.
		RenderContext.clear();
		RenderContext.set(graphics);

		if (!FiveZigFabric.isCoreReady()) {
			return;
		}
		// Vanilla skips the hotbar and the rest of the HUD while F1 is held; the core's overlay
		// has to follow or it would keep drawing over a "hidden" HUD.
		if (Minecraft.getInstance().options != null && Minecraft.getInstance().options.hideGui) {
			return;
		}
		try {
			The5zigMod.getGuiIngame().renderGameOverlay();
		} catch (Throwable throwable) {
			if (!the5zig$hudFailureLogged) {
				the5zig$hudFailureLogged = true;
				The5zigMod.logger.error("[5zig Renewed] HUD rendering failed; further HUD errors will be suppressed", throwable);
			}
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("RETURN"))
	private void the5zig$endFrame(GuiGraphics graphics, float partialTick, CallbackInfo callbackInfo) {
		RenderContext.clear();
	}

	/**
	 * The 5zig hotbar numbers are drawn on top of the vanilla hotbar.
	 */
	@Inject(method = "renderHotbar(FLnet/minecraft/client/gui/GuiGraphics;)V", at = @At("TAIL"))
	private void the5zig$afterHotbar(float partialTick, GuiGraphics graphics, CallbackInfo callbackInfo) {
		if (!FiveZigFabric.isCoreReady()) {
			return;
		}
		try {
			The5zigMod.getGuiIngame().onRenderHotbar();
		} catch (Throwable ignored) {
			// Never let a cosmetic feature break the HUD pass.
		}
	}

	@Inject(method = "tick()V", at = @At("TAIL"))
	private void the5zig$tick(CallbackInfo callbackInfo) {
		if (!FiveZigFabric.isCoreReady()) {
			return;
		}
		try {
			The5zigMod.getGuiIngame().tick();
		} catch (Throwable ignored) {
			// Cosmetic state only.
		}
	}

	/**
	 * 1.15 hooked {@code IngameGui#addChatMessage} for chat type 2; 1.20.1 routes the action bar
	 * through {@code Gui#setOverlayMessage}.
	 */
	@Inject(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
	private void the5zig$actionBar(Component message, boolean animateColour, CallbackInfo callbackInfo) {
		if (!FiveZigFabric.isCoreReady() || message == null) {
			return;
		}
		try {
			if (The5zigMod.getListener().onActionBar(message.getString())) {
				callbackInfo.cancel();
			}
		} catch (Throwable ignored) {
			// Leave vanilla behaviour untouched if a listener misbehaves.
		}
	}
}
