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
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge 26.1.2 copy of the platform's {@code MixinGui}.
 * <p>
 * Publishing the frame's {@link GuiGraphicsExtractor} to {@link RenderContext} and driving the HUD
 * from {@link HudFrame} is unchanged — the HUD itself lives in the platform, this class is only the
 * hook. The one NeoForge delta is the hotbar pass: NeoForge 26.1.2 rewrites
 * {@code Gui#extractRenderState} into a {@code GuiLayerManager} and, in
 * {@code patches/net/minecraft/client/gui/Gui.java.patch}, <em>renames</em>
 * {@code extractHotbarAndDecorations} to {@code extractHotbar}. The platform mixin targets the
 * vanilla name, which does not exist in the NeoForge-patched jar, so its hotbar injector would fail
 * {@code require = 1} and take the whole {@code Gui} transform down with it. Same class name, same
 * package, corrected target — the build excludes the platform file (as it does for
 * {@code PluginMessages}).
 */
@Mixin(Gui.class)
public abstract class MixinGui {

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
	private void the5zig$beginFrame(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
		HudFrame.begin(graphics);
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
	private void the5zig$endFrame(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
		HudFrame.end();
	}

	/**
	 * The 5zig hotbar numbers are drawn on top of the vanilla hotbar. NeoForge's
	 * {@code Gui#extractHotbar} is the renamed vanilla {@code extractHotbarAndDecorations}.
	 */
	@Inject(method = "extractHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
	private void the5zig$afterHotbar(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
		HudFrame.afterHotbar(graphics);
	}

	@Inject(method = "tick(Z)V", at = @At("TAIL"))
	private void the5zig$tick(boolean pauseGame, CallbackInfo callbackInfo) {
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
