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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import eu.the5zig.mod.gui.elements.IButton;
import eu.the5zig.mod.util.GuiContext;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Created by 5zig.
 * All rights reserved © 2015
 * <p>
 * 1.19.2 port: the vanilla widget is {@code net.minecraft.client.gui.components.Button}, which
 * renders itself through {@code renderButton(PoseStack, int, int, float)}. The 5zig core still
 * drives its widgets manually, so {@link #draw(int, int)} grabs the frame's {@code GuiContext}
 * from {@link RenderContext} and forwards to the vanilla render path.
 * <p>
 * 1.19.2's {@code AbstractWidget} has no {@code getX()}/{@code getY()}/{@code setX}/{@code setY}
 * accessors (they arrive in 1.20), so they are provided here for the {@link IButton} contract.
 */
public class Button extends net.minecraft.client.gui.components.Button implements IButton {

	private static final int COLOR_ENABLED = 14737632;
	private static final int COLOR_DISABLED = 10526880;

	private int disabledTicks = 0;
	private int id;

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
		super(x, y, width, height, Component.literal(label == null ? "" : label), button -> {
		});
		this.id = id;
		this.active = enabled;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public String getLabel() {
		return getMessage().getString();
	}

	@Override
	public void setLabel(String label) {
		setMessage(Component.literal(label == null ? "" : label));
	}

	@Override
	public int getX() {
		return this.x;
	}

	@Override
	public void setX(int x) {
		this.x = x;
	}

	@Override
	public int getY() {
		return this.y;
	}

	@Override
	public void setY(int y) {
		this.y = y;
	}

	@Override
	public int callGetWidth() {
		return this.width;
	}

	@Override
	public void callSetWidth(int width) {
		this.width = width;
	}

	@Override
	public int callGetHeight() {
		return this.height;
	}

	@Override
	public void callSetHeight(int height) {
		this.height = height;
	}

	@Override
	public boolean isEnabled() {
		return this.active;
	}

	@Override
	public void setEnabled(boolean enabled) {
		this.active = enabled;
	}

	@Override
	public boolean isVisible() {
		return this.visible;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public void setHovered(boolean hovered) {
		this.isHovered = hovered;
	}

	@Override
	public void draw(int mouseX, int mouseY) {
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			// Not inside a render pass (e.g. a tooltip probe); never crash, just skip.
			return;
		}
		render(graphics.pose(), mouseX, mouseY, 0.0F);
	}

	@Override
	public void tick() {
		if (disabledTicks > 0) {
			disabledTicks--;
			if (disabledTicks == 0) {
				setEnabled(true);
			}
		}
		if (disabledTicks < 0)
			disabledTicks = 1;
	}

	/**
	 * 1.15 hit test. The click sound is played by the core ({@code Gui#mouseClicked0} calls
	 * {@link #playClickSound()} once), so this deliberately avoids the extra
	 * {@code playDownSound} that {@code AbstractWidget#mouseClicked} would play.
	 */
	@Override
	public boolean mouseClicked(int mouseX, int mouseY) {
		if (!this.active || !this.visible) {
			return false;
		}
		if (!this.clicked(mouseX, mouseY)) {
			return false;
		}
		this.onClick(mouseX, mouseY);
		return true;
	}

	@Override
	public void callMouseReleased(int mouseX, int mouseY) {
		this.onRelease(mouseX, mouseY);
	}

	@Override
	public void playClickSound() {
		playDownSound(Minecraft.getInstance().getSoundManager());
	}

	@Override
	public void setTicksDisabled(int ticks) {
		setEnabled(false);
		disabledTicks = ticks;
	}

	@Override
	public void guiClosed() {
	}

	@Override
	public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		GuiContext graphics = new GuiContext(poseStack);
		graphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		graphics.blitNineSliced(WIDGETS_LOCATION, getX(), getY(), getWidth(), getHeight(), 20, 4, 200, 20, 0, getTextureY());
		graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		drawLabel(graphics);
	}

	/**
	 * Draws the (centered) label, like 1.15's {@code Gui.drawCenteredString(getMessage(), x + width / 2, y + (height - 8) / 2, color)}.
	 */
	protected void drawLabel(GuiContext graphics) {
		int color = this.active ? COLOR_ENABLED : COLOR_DISABLED;
		graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2,
				getY() + (getHeight() - 8) / 2, color);
	}

	/**
	 * @return the texture v-offset of the widgets.png button sprite for the current state.
	 */
	protected int getTextureY() {
		int state = 1;
		if (!this.active) {
			state = 0;
		} else if (this.isHoveredOrFocused()) {
			state = 2;
		}
		return 46 + state * 20;
	}
}
