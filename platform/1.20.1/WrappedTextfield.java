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

import eu.the5zig.mod.gui.elements.IWrappedTextfield;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

/**
 * Wraps a vanilla-ish text field handle (an {@link EditBox} or a platform
 * {@link Textfield}) so the core can ask whether it is focused.
 */
public class WrappedTextfield implements IWrappedTextfield {

	private final Object handle;

	public WrappedTextfield(Object handle) {
		this.handle = handle;
	}

	@Override
	public boolean isSelected() {
		if (handle instanceof EditBox) {
			return ((EditBox) handle).isFocused();
		}
		if (handle instanceof AbstractWidget) {
			return ((AbstractWidget) handle).isFocused();
		}
		if (handle instanceof eu.the5zig.mod.gui.elements.ITextfield) {
			return ((eu.the5zig.mod.gui.elements.ITextfield) handle).callIsFocused();
		}
		return false;
	}
}
