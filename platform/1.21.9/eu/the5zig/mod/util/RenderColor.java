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

/**
 * Colour helper for the strict-ARGB GUI of 1.21.9+.
 * <p>
 * Up to 1.21.8 a colour without an alpha byte was treated as opaque by the fixed-function
 * pipeline. Since 1.21.9 every {@code GuiGraphics} draw call takes a real ARGB value and feeds its
 * alpha straight into the blend state, so a legacy {@code 0xRRGGBB} constant (alpha 0) draws
 * nothing at all - silently, without an error. The core is full of those constants
 * ({@code 0xFFFFFF}, {@code 14737632}, {@code ChatColor#getColor()}, ...), so every colour that
 * crosses the platform boundary has to be repaired first.
 */
public final class RenderColor {

	private RenderColor() {
	}

	/**
	 * Restores the alpha byte of a legacy {@code 0xRRGGBB} colour.
	 *
	 * @param color the colour as the core passed it.
	 * @return the same colour with a fully opaque alpha channel; colours that already carry alpha
	 * (including deliberately transparent ones) are returned unchanged.
	 */
	public static int opaque(int color) {
		return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
	}
}
