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
import eu.the5zig.mod.platform.HudFrame;
import eu.the5zig.mod.util.CoreState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes the frame's {@link PoseStack} to {@code RenderContext} and drives the mod's HUD.
 * <p>
 * 1.19.2 draws with a {@code PoseStack} threaded through every draw call (the {@code GuiGraphics}
 * holder only arrives in 1.20), while the 5zig core still draws through
 * {@code IVariables.drawString} and friends. The bracketing and the actual overlay draw live in
 * {@link HudFrame} because Forge overrides {@code Gui#render} in {@code ForgeGui} and needs its own
 * mixin pointing at the same logic.
 */
@Mixin(Gui.class)
public abstract class MixinGui {

	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"))
	private void the5zig$beginFrame(PoseStack poseStack, float partialTick, CallbackInfo callbackInfo) {
		HudFrame.begin(poseStack);
	}

	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("RETURN"))
	private void the5zig$endFrame(PoseStack poseStack, float partialTick, CallbackInfo callbackInfo) {
		HudFrame.end();
	}

	/**
	 * The 5zig hotbar numbers are drawn on top of the vanilla hotbar.
	 */
	@Inject(method = "renderHotbar(FLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
	private void the5zig$afterHotbar(float partialTick, PoseStack poseStack, CallbackInfo callbackInfo) {
		HudFrame.afterHotbar(poseStack);
	}

	@Inject(method = "tick()V", at = @At("TAIL"))
	private void the5zig$tick(CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
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
		if (!CoreState.isReady() || message == null) {
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
