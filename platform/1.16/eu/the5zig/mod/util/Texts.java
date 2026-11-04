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

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * Component factory shim for 1.16.
 * <p>
 * {@code Component.literal/empty/translatable} are 1.19+ static factories. 1.16 constructs the
 * concrete {@link TextComponent}/{@link TranslatableComponent} types directly, so the few call sites
 * that need a component go through here instead of repeating the constructor choice.
 */
public final class Texts {

	private Texts() {
	}

	public static MutableComponent literal(String text) {
		return new TextComponent(text == null ? "" : text);
	}

	public static MutableComponent empty() {
		return new TextComponent("");
	}

	public static MutableComponent translatable(String key, Object... args) {
		return new TranslatableComponent(key, args);
	}

	/**
	 * Readable alias so call sites can make their intent explicit when they wrap an existing one.
	 */
	public static Component of(Component component) {
		return component;
	}
}
