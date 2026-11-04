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

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Drawing helpers that paper over APIs 1.20.2 removed.
 */
final class GuiCompat {

	private GuiCompat() {
	}

	/**
	 * 1.20.1's {@code GuiGraphics#blitRepeating(ResourceLocation, int, int, int, int, int, int, int, int)}
	 * is gone in 1.20.2. Tiling is reimplemented here with the plain {@code blit} overload, which still
	 * honours the current {@code setColor} tint the callers rely on.
	 *
	 * @param graphics   the frame's graphics.
	 * @param texture    the texture to tile.
	 * @param x          left edge of the area to fill.
	 * @param y          top edge of the area to fill.
	 * @param width      width of the area to fill.
	 * @param height     height of the area to fill.
	 * @param u          texture u offset of the tile.
	 * @param v          texture v offset of the tile.
	 * @param tileWidth  width of one tile.
	 * @param tileHeight height of one tile.
	 */
	static void blitRepeating(GuiGraphics graphics, net.minecraft.resources.ResourceLocation texture,
			int x, int y, int width, int height, int u, int v, int tileWidth, int tileHeight) {
		if (texture == null || tileWidth <= 0 || tileHeight <= 0) {
			return;
		}
		// Same state the vanilla menu-background helper sets up before tiling.
		RenderSystem.enableBlend();
		for (int offsetX = 0; offsetX < width; offsetX += tileWidth) {
			int partWidth = Math.min(tileWidth, width - offsetX);
			for (int offsetY = 0; offsetY < height; offsetY += tileHeight) {
				int partHeight = Math.min(tileHeight, height - offsetY);
				graphics.blit(texture, x + offsetX, y + offsetY, (float) u, (float) v,
						partWidth, partHeight, tileWidth, tileHeight);
			}
		}
		RenderSystem.disableBlend();
	}
}
