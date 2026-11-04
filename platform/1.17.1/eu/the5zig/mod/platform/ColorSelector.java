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

import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.gui.elements.IColorSelector;
import eu.the5zig.mod.util.ColorSelectorCallback;
import eu.the5zig.mod.util.RenderContext;
import eu.the5zig.util.minecraft.ChatColor;
import eu.the5zig.mod.util.GuiContext;

/**
 * A button with a colour preview that opens the 16 vanilla chat colours on hover.
 * <p>
 * 1.17.1 port: the rectangles are drawn with {@link GuiContext#fill} from the frame's
 * {@link RenderContext}; the 1.15 version used {@code Gui.drawRect} on top of the immediate mode
 * tessellator. Everything else (hover / move-out tracking, palette position) is unchanged.
 */
public class ColorSelector extends Button implements IColorSelector {

	private final ColorSelectorCallback callback;

	private final int boxWidth = 14, boxHeight = 14;

	private boolean selected = false;
	private int selectedX = -1, selectedY = -1;
	private boolean requireMoveOut = false;

	public ColorSelector(int id, int x, int y, int width, int height, String label, ColorSelectorCallback callback) {
		super(id, x, y, width, height, label);
		this.callback = callback;
	}

	@Override
	public void draw(int mouseX, int mouseY) {
		super.draw(mouseX, mouseY);

		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}

		int boxX = getBoxX();
		int boxY = getBoxY();

		if (isEnabled() && !selected && !requireMoveOut && mouseX >= boxX && mouseX <= boxX + boxWidth && mouseY >= boxY && mouseY <= boxY + boxHeight) {
			selectedX = boxX - 16 * 12 / 2 + 7;
			selectedY = boxY + 3;
			if (boxX + 16 * 12 / 2 + 2 > MinecraftFactory.getVars().getCurrentScreen().getWidth())
				this.selectedX = MinecraftFactory.getVars().getCurrentScreen().getWidth() - 192 - 2;

			selected = true;
		} else if (requireMoveOut) {
			if (mouseX < boxX || mouseX > boxX + boxWidth || mouseY < selectedY || mouseY > selectedY + 8) {
				selectedX = selectedY = -1;
				selected = false;
				requireMoveOut = false;
			}
		} else if (!selected || (mouseX < selectedX || mouseX > selectedX + 192 || mouseY < selectedY - 8 || mouseY > selectedY + 8 + 8)) {
			selectedX = selectedY = -1;
			selected = false;
			requireMoveOut = false;
		}

		// draw Selected Color Box
		graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight,
				((selected && !requireMoveOut) || (mouseX >= boxX && mouseX <= boxX + boxWidth && mouseY >= boxY && mouseY <= boxY + boxHeight) ? 0xFF444444 : 0xFF222222));
		graphics.fill(boxX + 2, boxY + 2, boxX + boxWidth - 2, boxY + boxHeight - 2, (0x00FFFFFF & callback.getColor().getColor()) | 0xFF << 24);

		// draw Select Color Box
		if (selected) {
			int width = 192;
			graphics.fill(selectedX - 2, selectedY - 2, selectedX + width + 2, selectedY + 10, 0xFF222222);
			int x = 0;
			for (ChatColor chatColor : ChatColor.values()) {
				if (chatColor.getColor() == -1)
					continue;
				graphics.fill(selectedX + x, selectedY, selectedX + x + 12, selectedY + 8, (0x00FFFFFF & chatColor.getColor()) | 0xFF << 24);
				x += 12;
			}
		}
	}

	@Override
	public boolean mouseClicked(int mouseX, int mouseY) {
		boolean result = super.mouseClicked(mouseX, mouseY);

		if (selected && mouseX >= selectedX && mouseX <= selectedX + 192 && mouseY >= selectedY && mouseY <= selectedY + 8) {
			callback.setColor(ChatColor.values()[((int) (16 - Math.ceil((double) (selectedX + 192 - mouseX) / 12.0)))]);

			MinecraftFactory.getVars().getCurrentScreen().actionPerformed0(this);

			requireMoveOut = true;
			selected = false;
		}
		return result;
	}


	private int getBoxX() {
		return getX() + (callGetWidth() + MinecraftFactory.getVars().getStringWidth(getLabel())) / 2 + 4;
	}

	private int getBoxY() {
		return getY() + (callGetHeight() - boxHeight) / 2;
	}
}
