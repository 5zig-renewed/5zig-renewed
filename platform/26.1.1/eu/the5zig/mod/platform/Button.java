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

import eu.the5zig.mod.gui.elements.IButton;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Created by 5zig.
 * All rights reserved © 2015
 * <p>
 * 26.x port: the vanilla {@code Button} widget is no longer reused. Its render path changed twice
 * since 1.20.1 (sprite-based nine-slicing, {@code extractWidgetRenderState}) and the 5zig core drives
 * its widgets itself, so this is a small standalone widget that draws through the frame's
 * {@link GuiGraphicsExtractor} and implements {@link IButton} directly.
 */
public class Button implements IButton {

	protected static final int COLOR_ENABLED = 14737632;
	protected static final int COLOR_DISABLED = 10526880;
	protected static final int COLOR_HOVERED = 16777120;

	private static final int BACKGROUND = 0xFF2B2B2B;
	private static final int BACKGROUND_HOVERED = 0xFF3D3D3D;
	private static final int BACKGROUND_DISABLED = 0xFF1E1E1E;
	private static final int BORDER = 0xFF000000;
	private static final int BORDER_HOVERED = 0xFFFFFFFF;

	protected int id;
	protected int x;
	protected int y;
	protected int width;
	protected int height;
	protected Component message;
	protected boolean active = true;
	protected boolean visible = true;
	protected boolean hovered;

	private int disabledTicks;

	public Button(int id, int x, int y, String label) {
		this(id, x, y, 200, 20, label, true);
	}

	public Button(int id, int x, int y, String label, boolean enabled) {
		this(id, x, y, 200, 20, label, enabled);
	}

	public Button(int id, int x, int y, int width, int height, String label) {
		this(id, x, y, width, height, label, true);
	}

	public Button(int id, int x, int y, int width, int height, String label, boolean enabled) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.message = Component.literal(label == null ? "" : label);
		this.active = enabled;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public String getLabel() {
		return message.getString();
	}

	@Override
	public void setLabel(String label) {
		this.message = Component.literal(label == null ? "" : label);
	}

	public Component getMessage() {
		return message;
	}

	@Override
	public int callGetWidth() {
		return width;
	}

	@Override
	public void callSetWidth(int width) {
		this.width = width;
	}

	@Override
	public int callGetHeight() {
		return height;
	}

	@Override
	public void callSetHeight(int height) {
		this.height = height;
	}

	@Override
	public int getX() {
		return x;
	}

	@Override
	public void setX(int x) {
		this.x = x;
	}

	@Override
	public int getY() {
		return y;
	}

	@Override
	public void setY(int y) {
		this.y = y;
	}

	@Override
	public boolean isEnabled() {
		return active;
	}

	@Override
	public void setEnabled(boolean enabled) {
		this.active = enabled;
	}

	@Override
	public boolean isVisible() {
		return visible;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public boolean isHovered() {
		return hovered;
	}

	@Override
	public void setHovered(boolean hovered) {
		this.hovered = hovered;
	}

	@Override
	public void draw(int mouseX, int mouseY) {
		GuiGraphicsExtractor graphics = RenderContext.get();
		if (graphics == null) {
			// Not inside a render pass (e.g. a tooltip probe); never crash, just skip.
			return;
		}
		drawWidget(graphics, mouseX, mouseY);
	}

	protected void drawWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!visible) {
			return;
		}
		this.hovered = active && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
		int background = !active ? BACKGROUND_DISABLED : (hovered ? BACKGROUND_HOVERED : BACKGROUND);
		graphics.fill(x, y, x + width, y + height, background);
		graphics.outline(x, y, width, height, hovered && active ? BORDER_HOVERED : BORDER);
		drawLabel(graphics);
	}

	protected void drawLabel(GuiGraphicsExtractor graphics) {
		int color = !active ? COLOR_DISABLED : (hovered ? COLOR_HOVERED : COLOR_ENABLED);
		// Labels may carry legacy '§' codes; 26.x renders raw strings literally, so they are parsed.
		Component label = getLabel().indexOf('\u00a7') < 0 ? message : eu.the5zig.mod.util.LegacyText.fromLegacy(getLabel());
		// Same call shape as the HUD text that is known to render (Variables#drawCenteredString):
		// draw it explicitly rather than through centeredText, and force an opaque alpha.
		int opaque = (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
		graphics.text(Minecraft.getInstance().font, label, x + width / 2 - Minecraft.getInstance().font.width(label) / 2,
				y + (height - 8) / 2, opaque, true);
	}

	@Override
	public void tick() {
		if (disabledTicks > 0) {
			disabledTicks--;
			if (disabledTicks == 0) {
				setEnabled(true);
			}
		}
		if (disabledTicks < 0) {
			disabledTicks = 1;
		}
	}

	/**
	 * 1.15 hit test. The click sound is played by the core ({@code Gui#mouseClicked0} calls
	 * {@link #playClickSound()} once), so this deliberately avoids a second click sound.
	 */
	@Override
	public boolean mouseClicked(int mouseX, int mouseY) {
		if (!active || !visible) {
			return false;
		}
		if (!(mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height)) {
			return false;
		}
		onClick(mouseX, mouseY);
		return true;
	}

	protected void onClick(int mouseX, int mouseY) {
	}

	protected void onRelease(int mouseX, int mouseY) {
	}

	@Override
	public void callMouseReleased(int mouseX, int mouseY) {
		onRelease(mouseX, mouseY);
	}

	@Override
	public void playClickSound() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.getSoundManager() != null) {
			minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	}

	@Override
	public void setTicksDisabled(int ticks) {
		setEnabled(false);
		disabledTicks = ticks;
	}

	@Override
	public void guiClosed() {
	}
}
