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
import eu.the5zig.util.minecraft.ChatColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of 1.15's {@code MixinGuiPlayerTabOverlay}: prints the numeric ping next to the player name
 * instead of (or rather, on top of) the signal bars.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class MixinPlayerTabOverlay {

	@Inject(method = "renderPingIcon(Lnet/minecraft/client/gui/GuiGraphics;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V",
			at = @At("HEAD"), cancellable = true)
	private void the5zig$renderPing(GuiGraphics graphics, int width, int x, int y, PlayerInfo playerInfo, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || playerInfo == null) {
			return;
		}
		try {
			if (!The5zigMod.getConfig().getBool("pingOnTab")) {
				return;
			}
			int ping = playerInfo.getLatency();
			String display = ping <= 0 ? "?" : Integer.toString(ping);
			int color = ChatColor.getPingColor(ping);

			// The icon is drawn at (x + width - 11, y) with a size of 10x8; centre the text on it
			// and halve it so a three digit ping still fits. 1.21.5+ replaced the 3D PoseStack in
			// GUI rendering with a 2D JOML Matrix3x2fStack.
			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			pose.scale(0.5F, 0.5F);
			graphics.drawCenteredString(Minecraft.getInstance().font, display, (x + width - 6) * 2, (y + 1) * 2, color);
			pose.popMatrix();

			callbackInfo.cancel();
		} catch (Throwable ignored) {
			// Fall back to the vanilla icon.
		}
	}
}
