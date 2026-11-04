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

package eu.the5zig.mod.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.util.CoreState;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.Minecraft;

/**
 * The HUD frame, factored out of the mixins.
 * <p>
 * Data: 5zig's overlay must be drawn while a {@link PoseStack} is available, and 1.16 threads
 * that object through the render call. Forge adds a wrinkle: {@code net.minecraftforge.client.gui.CustomizeGuiOverlayEvent}
 * aside, the game instantiates {@code net.minecraftforge.client.gui.overlay.ForgeGui}, which
 * <em>overrides</em> {@code Gui#render}. A mixin on {@code Gui} therefore never runs on Forge, so both
 * loaders' hooks call into this class instead of duplicating the logic.
 */
public final class HudFrame {

	private static boolean failureLogged;

	private HudFrame() {
	}

	/**
	 * Publishes the frame's graphics, then draws the overlay when the core is up.
	 */
	public static void begin(PoseStack poseStack) {
		// Drop a context leaked by a frame that threw before its end hook ran.
		RenderContext.clear();
		RenderContext.set(poseStack);

		if (!The5zigMod.hasBeenInitialized() || !CoreState.isReady()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		// F1 hides the vanilla HUD; the overlay follows it.
		if (minecraft != null && minecraft.options != null && minecraft.options.hideGui) {
			return;
		}
		try {
			The5zigMod.getGuiIngame().renderGameOverlay();
		} catch (Throwable throwable) {
			if (!failureLogged) {
				failureLogged = true;
				The5zigMod.logger.error("[5zig Renewed] HUD rendering failed; further HUD errors will be suppressed", throwable);
			}
		}
	}

	/**
	 * Releases the frame context.
	 */
	public static void end() {
		RenderContext.clear();
	}

	/**
	 * Draws the "text above the hotbar" additions on top of the vanilla hotbar.
	 */
	public static void afterHotbar(PoseStack poseStack) {
		if (!The5zigMod.hasBeenInitialized() || !CoreState.isReady()) {
			return;
		}
		try {
			The5zigMod.getGuiIngame().onRenderHotbar();
		} catch (Throwable throwable) {
			if (!failureLogged) {
				failureLogged = true;
				The5zigMod.logger.error("[5zig Renewed] Hotbar overlay failed", throwable);
			}
		}
	}
}
