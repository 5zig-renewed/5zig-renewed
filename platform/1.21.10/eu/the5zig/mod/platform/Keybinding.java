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

import eu.the5zig.mod.util.Keybinds;
import com.mojang.blaze3d.platform.InputConstants;
import eu.the5zig.mod.util.IKeybinding;
import net.minecraft.client.KeyMapping;

/**
 * A keybinding of the mod.
 * <p>
 * 1.15 had to look the key code up in a hand-maintained name→code map because
 * {@code KeyBinding#getKeyCode()} had already been removed; 1.20.1 has no getter for the bound
 * {@link InputConstants.Key} either, so the numeric code is cached and refreshed whenever the key is
 * (re)bound. The core polls {@link #callIsPressed()} every tick and compares
 * {@link #callGetKeyCode()} against raw GLFW key codes, so both must be cheap and side effect free.
 */
public class Keybinding extends KeyMapping implements IKeybinding {

	private int keyCode;

	public Keybinding(String description, int keyCode, String category) {
		super(description, keyCode, Keybinds.category(category));
		this.keyCode = keyCode;
	}

	@Override
	public void setKey(InputConstants.Key key) {
		super.setKey(key);
		this.keyCode = key == null ? -1 : key.getValue();
	}

	/**
	 * @return true while the key is held down.
	 */
	@Override
	public boolean callIsPressed() {
		return isDown();
	}

	@Override
	public int callGetKeyCode() {
		return this.keyCode;
	}
}
