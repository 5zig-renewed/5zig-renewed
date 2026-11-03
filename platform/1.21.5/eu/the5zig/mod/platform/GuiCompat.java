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

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Drawing helpers that paper over APIs 1.21.1 removed.
 * <p>
 * 1.21.2+ turned every {@code GuiGraphics} {@code blit}/{@code blitSprite} into a
 * pipeline-driven call: the first argument is the {@link RenderType} factory (vanilla GUI code uses
 * {@code RenderType::guiTextured}) and the per-draw tint moved from {@code GuiGraphics#setColor}
 * (removed) into a trailing ARGB colour argument. The helpers below keep the 1.21.1 call shapes the
 * platform code was written against.
 */
final class GuiCompat {

	/** 0.125 grey, the tint the list/file-selector backgrounds were drawn with via {@code setColor}. */
	static final int TINT_DARKENED = 0xFF202020;

	/** Fully opaque white, i.e. "no tint". */
	static final int TINT_NONE = 0xFFFFFFFF;

	private GuiCompat() {
	}

	/**
	 * 1.21.1's {@code GuiGraphics#blit(ResourceLocation, int, int, int, int, int, int)}: draws a region
	 * at its native size from a 256x256 texture.
	 */
	static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
		blit(graphics, texture, x, y, u, v, width, height, 256, 256, TINT_NONE);
	}

	/**
	 * 1.21.1's {@code blit(ResourceLocation, int, int, float, float, int, int, int, int)}.
	 */
	static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v,
			int width, int height, int textureWidth, int textureHeight) {
		blit(graphics, texture, x, y, u, v, width, height, textureWidth, textureHeight, TINT_NONE);
	}

	/**
	 * As above, with an explicit ARGB tint.
	 */
	static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v,
			int width, int height, int textureWidth, int textureHeight, int color) {
		if (texture == null || width <= 0 || height <= 0) {
			return;
		}
		graphics.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
	}

	/**
	 * 1.21.1's {@code blit(ResourceLocation, int, int, int, int, float, float, int, int, int, int)}: a
	 * scaled region ({@code uWidth}/{@code vHeight}) out of a fixed texture size.
	 */
	static void blitScaled(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
			float u, float v, int uWidth, int vHeight, int textureWidth, int textureHeight) {
		if (texture == null || width <= 0 || height <= 0) {
			return;
		}
		graphics.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, uWidth, vHeight,
				textureWidth, textureHeight, TINT_NONE);
	}

	/**
	 * 1.21.4's sprite blit, with the {@code guiTextured} pipeline.
	 */
	static void blitSprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
		blitSprite(graphics, sprite, x, y, width, height, TINT_NONE);
	}

	/**
	 * As above, with an explicit ARGB tint.
	 */
	static void blitSprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height, int color) {
		if (sprite == null || width <= 0 || height <= 0) {
			return;
		}
		graphics.blitSprite(RenderType::guiTextured, sprite, x, y, width, height, color);
	}

	/**
	 * Blits an already-resolved atlas sprite (e.g. from the mob-effect atlas, which is not the GUI
	 * atlas {@code blitSprite(ResourceLocation, ...)} resolves against).
	 */
	static void blitSprite(GuiGraphics graphics, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
			int x, int y, int width, int height) {
		if (sprite == null || width <= 0 || height <= 0) {
			return;
		}
		graphics.blitSprite(RenderType::guiTextured, sprite, x, y, width, height, TINT_NONE);
	}

	/**
	 * 1.20.1's {@code GuiGraphics#blitRepeating(ResourceLocation, int, int, int, int, int, int, int, int)}
	 * is gone since 1.21.1. Tiling is reimplemented here with the plain {@code blit} overload.
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
	static void blitRepeating(GuiGraphics graphics, ResourceLocation texture,
			int x, int y, int width, int height, int u, int v, int tileWidth, int tileHeight) {
		blitRepeating(graphics, texture, x, y, width, height, u, v, tileWidth, tileHeight, TINT_NONE);
	}

	/**
	 * As above, with an explicit ARGB tint applied to every tile.
	 */
	static void blitRepeating(GuiGraphics graphics, ResourceLocation texture,
			int x, int y, int width, int height, int u, int v, int tileWidth, int tileHeight, int color) {
		if (texture == null || tileWidth <= 0 || tileHeight <= 0) {
			return;
		}
		// 1.21.5 removed the global blend toggles: blending is baked into the pipeline the blit selects.
		for (int offsetX = 0; offsetX < width; offsetX += tileWidth) {
			int partWidth = Math.min(tileWidth, width - offsetX);
			for (int offsetY = 0; offsetY < height; offsetY += tileHeight) {
				int partHeight = Math.min(tileHeight, height - offsetY);
				graphics.blit(RenderType::guiTextured, texture, x + offsetX, y + offsetY, (float) u, (float) v,
						partWidth, partHeight, tileWidth, tileHeight, color);
			}
		}
	}
}
