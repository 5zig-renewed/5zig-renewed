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

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Holds the {@link GuiContext} of the frame currently being drawn.
 * <p>
 * The core of the mod was written for 1.15, where drawing was done through a globally reachable
 * {@code Tessellator}. 1.16.1 threads a {@code PoseStack} down the render call instead, so the
 * platform layer publishes a {@link GuiContext} (a thin drawing facade over that stack) here for
 * the duration of a frame.
 */
public final class RenderContext {

	private static final ThreadLocal<GuiContext> CURRENT = new ThreadLocal<>();

	private RenderContext() {
	}

	public static void set(GuiContext graphics) {
		CURRENT.set(graphics);
	}

	/**
	 * Wraps the frame's stack. Called by the HUD/screen mixins at the head of a render pass.
	 */
	public static void set(PoseStack poseStack) {
		CURRENT.set(poseStack == null ? null : new GuiContext(poseStack));
	}

	public static void clear() {
		CURRENT.remove();
	}

	/**
	 * @return the graphics of the frame being drawn, or {@code null} when the mod is drawing outside
	 * of a render pass (in which case the caller must skip drawing rather than crash).
	 */
	public static GuiContext get() {
		return CURRENT.get();
	}

	public static boolean isAvailable() {
		return CURRENT.get() != null;
	}
}
