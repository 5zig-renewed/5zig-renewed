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

import com.mojang.blaze3d.platform.InputConstants;
import eu.the5zig.mod.util.IKeybinding;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * A keybinding of the mod.
 * <p>
 * 26.x note: {@code KeyMapping(String, int, String)} became
 * {@code KeyMapping(String, InputConstants.Type, int, KeyMapping.Category)}; the free-form category
 * string of 1.15 turned into a registered {@link KeyMapping.Category} record. Categories are
 * derived from the core's category name and registered once per name.
 */
public class Keybinding extends KeyMapping implements IKeybinding {

	private static final java.util.Map<String, KeyMapping.Category> CATEGORIES = new java.util.HashMap<>();

	private int keyCode;

	public Keybinding(String description, int keyCode, String category) {
		// 26.3 renamed InputConstants.Type.KEYSYM to KEYBOARD.
		super(description, InputConstants.Type.KEYBOARD, keyCode, categoryOf(category));
		this.keyCode = keyCode;
	}

	/**
	 * Maps the core's free-form category name onto a registered 26.x category. The category id is
	 * derived from the name, so several core categories stay separate while still being stable
	 * across restarts.
	 */
	public static KeyMapping.Category categoryOf(String category) {
		String name = (category == null || category.isEmpty()) ? "The 5zig Mod" : category;
		KeyMapping.Category cached;
		synchronized (CATEGORIES) {
			cached = CATEGORIES.get(name);
		}
		if (cached != null) {
			return cached;
		}
		String path = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		KeyMapping.Category created = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("the5zigmod", path));
		synchronized (CATEGORIES) {
			CATEGORIES.put(name, created);
		}
		return created;
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
