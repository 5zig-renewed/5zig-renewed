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
 * Remembers the texture that {@code IVariables#bindTexture} bound last.
 * <p>
 * On 1.15 binding a texture was global state that following drawing calls picked up implicitly.
 * {@code GuiGraphics} instead takes the texture as an argument, but the 5zig core still splits the
 * two operations ({@code bindTexture(...)} then e.g. {@code Gui.drawModalRectWithCustomSizedTexture}).
 * Platform drawing helpers that do not receive a texture of their own should therefore read
 * {@link #current()} to find the texture the core expects them to use.
 */
public final class BoundTexture {

	private static net.minecraft.resources.ResourceLocation current;

	private BoundTexture() {
	}

	public static void bind(net.minecraft.resources.ResourceLocation location) {
		// 1.21.5 has no global texture unit: every draw call takes the texture it samples, so this
		// only records the binding for the platform helpers that ask for it.
		current = location;
	}

	public static net.minecraft.resources.ResourceLocation current() {
		return current;
	}

	public static net.minecraft.resources.ResourceLocation currentOr(net.minecraft.resources.ResourceLocation fallback) {
		return current != null ? current : fallback;
	}
}
