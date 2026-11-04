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

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/**
 * A single, already line-broken line of the second chat.
 * <p>
 * 1.15 stored a plain {@code TextComponent} here; 1.16 splits messages into one
 * {@link FormattedText} per rendered line so that per-character styles survive the line break, so
 * both representations are kept: the line is drawn, the {@link Component} it came from is what the
 * public API returns.
 * <p>
 * 1.16.1 predates {@code FormattedCharSequence} (the "reordering processor" wrapper that 1.16.2
 * introduced), so the per-line run is a {@code FormattedText}.
 */
public class GuiChatLine {

	private final int updateCounterCreated;
	private final Component component;
	private final FormattedText visual;
	private final int chatLineID;

	public GuiChatLine(int updateCounterCreated, Component component, FormattedText visual, int chatLineID) {
		this.updateCounterCreated = updateCounterCreated;
		this.component = component;
		this.visual = visual;
		this.chatLineID = chatLineID;
	}

	public GuiChatLine(int updateCounterCreated, Component component, int chatLineID) {
		this(updateCounterCreated, component, component, chatLineID);
	}

	public Component getChatComponent() {
		return this.component;
	}

	public FormattedText getVisual() {
		return this.visual;
	}

	public int getUpdatedCounter() {
		return this.updateCounterCreated;
	}

	public int getChatLineID() {
		return this.chatLineID;
	}

	/**
	 * @return the plain text of the rendered line, formatting codes removed.
	 */
	public String getPlainText() {
		return this.visual.getString();
	}
}
