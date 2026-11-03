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

/**
 * Colour helpers for the modern GUI renderer.
 * <p>
 * 1.15's {@code FontRenderer#drawString} ignored the alpha component of the colour it was handed,
 * so the 5zig core - and everything derived from it - still passes plain {@code 0xRRGGBB} values.
 * Since the GUI render-state rewrite (1.21.6+, so 1.21.7 included) {@code GuiGraphics#drawString}
 * test the alpha byte and <em>skip the draw entirely</em> when it is zero:
 * <pre>{@code if (ARGB.alpha(k) != 0) { ...submitText... }}</pre>
 * A legacy {@code 0x00RRGGBB} therefore renders as nothing at all, which looks exactly like a
 * broken HUD. Every colour that reaches a text draw is made opaque here; explicit translucent
 * colours (alpha != 0) are passed through untouched.
 */
public final class Argb {

	private Argb() {
	}

	/**
	 * @param color a {@code 0xRRGGBB} or {@code 0xAARRGGBB} colour.
	 * @return the same colour with a fully opaque alpha channel.
	 */
	public static int opaque(int color) {
		return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
	}

	/**
	 * @param color a {@code 0xRRGGBB} or {@code 0xAARRGGBB} colour.
	 * @param alpha the alpha channel to apply, 0-255.
	 * @return the colour with the given alpha channel.
	 */
	public static int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
	}
}
