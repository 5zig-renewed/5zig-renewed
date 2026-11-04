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

import net.minecraft.client.Minecraft;
import eu.the5zig.mod.util.GuiContext;

/**
 * Created by 5zig.
 * All rights reserved (C) 2015
 * <p>
 * A button that keeps its own text colouring (the "string" variant used by the 5zig menus).
 */
public class StringButton extends Button {

	public StringButton(int id, int x, int y, String label) {
		super(id, x, y, label);
	}

	public StringButton(int id, int x, int y, int width, int height, String label) {
		super(id, x, y, width, height, label);
	}

	@Override
	protected void drawLabel(GuiContext graphics) {
		int color = 14737632;
		if (!isEnabled()) {
			color = 10526880;
		} else if (isHovered()) {
			color = 16777120;
		}
		graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2,
				getY() + (getHeight() - 8) / 2, color);
	}
}
