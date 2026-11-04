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
import eu.the5zig.mod.util.SliderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Created by 5zig.
 * All rights reserved © 2015
 * <p>
 * 1.20.3 port of the 1.15 slider: still a {@link Button} whose background is the normal-state
 * widget sprite plus the knob from {@code textures/gui/sprites/widget/} (1.20.2 replaced the
 * {@code widgets.png} sheet).
 */
public class Slider extends Button {

	/**
	 * 1.20.2's slider is sprite based: a 200x20 track plus an 8x20 handle, both from the GUI atlas.
	 * The 1.15 implementation blitted the same shapes out of {@code textures/gui/widgets.png}.
	 */
	private static final net.minecraft.resources.ResourceLocation SLIDER_SPRITE =
			new net.minecraft.resources.ResourceLocation("widget/slider");
	private static final net.minecraft.resources.ResourceLocation SLIDER_HANDLE_SPRITE =
			new net.minecraft.resources.ResourceLocation("widget/slider_handle");
	private static final net.minecraft.resources.ResourceLocation SLIDER_HANDLE_HIGHLIGHTED_SPRITE =
			new net.minecraft.resources.ResourceLocation("widget/slider_handle_highlighted");
	private static final int HANDLE_WIDTH = 8;

	private final String str;
	public float value;
	public boolean dragging;
	private final SliderCallback sliderItem;

	public Slider(int id, int x, int y, SliderCallback sliderItem) {
		this(id, x, y, 150, 20, sliderItem);
	}

	public Slider(int id, int x, int y, int width, int height, SliderCallback sliderItem) {
		super(id, x, y, width, height, "");
		this.sliderItem = sliderItem;
		this.str = sliderItem.translate();
		this.value = normalizeValue(sliderItem.get());
		setLabel(this.str);
	}

	@Override
	public void setLabel(String label) {
		this.value = normalizeValue(sliderItem.get());
		super.setLabel(label + ": " + getValue());
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (!isVisible()) {
			return;
		}
		if (this.dragging) {
			updateValueFromMouse(mouseX);
		}

		graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		// Always the "normal" track sprite (1.15 overrode getYImage to return 0 for the same effect).
		graphics.blitSprite(SLIDER_SPRITE, getX(), getY(), getWidth(), getHeight());
		int handleX = getX() + (int) (this.value * (callGetWidth() - HANDLE_WIDTH));
		graphics.blitSprite(isHoveredOrFocused() ? SLIDER_HANDLE_HIGHLIGHTED_SPRITE : SLIDER_HANDLE_SPRITE,
				handleX, getY(), HANDLE_WIDTH, getHeight());

		drawLabel(graphics);
	}

	public float denormalizeValue(float value) {
		return this.snapToStepClamp(sliderItem.getMinValue() + (sliderItem.getMaxValue() - sliderItem.getMinValue()) * Mth.clamp(value, 0.0F, 1.0F));
	}

	public float normalizeValue(float value) {
		return Mth.clamp((snapToStepClamp(value) - sliderItem.getMinValue()) / (sliderItem.getMaxValue() - sliderItem.getMinValue()), 0.0F, 1.0F);
	}

	public float snapToStepClamp(float value) {
		value = snapToStep(value);
		return Mth.clamp(value, sliderItem.getMinValue(), sliderItem.getMaxValue());
	}

	protected float snapToStep(float value) {
		if (sliderItem.getSteps() != -1) {
			value = sliderItem.getSteps() * (float) Math.round(value / sliderItem.getSteps());
		}

		return value;
	}

	@Override
	public boolean mouseClicked(int x, int y) {
		if (super.mouseClicked(x, y)) {
			// 1.15 divided by (y - 8) here, which was a bug; the knob is positioned by width.
			this.value = Mth.clamp((x - (getX() + 4)) / (float) (callGetWidth() - 8), 0.0F, 1.0F);
			setLabel(this.str);
			this.dragging = true;
			return true;
		}
		return false;
	}

	/**
	 * Applies the current mouse position while dragging. Called from the render pass instead of
	 * {@code mouseDragged}: the core only forwards drags to {@code IGuiList}s, so a slider has to
	 * sample the mouse itself.
	 */
	protected void updateValueFromMouse(int mouseX) {
		this.value = Mth.clamp((mouseX - (getX() + 4)) / (float) (callGetWidth() - 8), 0.0F, 1.0F);

		float denormalized = denormalizeValue(this.value);
		sliderItem.set(denormalized);
		this.value = normalizeValue(denormalized);

		setLabel(this.str);
	}

	@Override
	public void tick() {
		super.tick();
		// The mouse may be released outside of the screen (or after the screen lost focus); the core
		// does not send us a mouseReleased in that case.
		if (this.dragging && !Minecraft.getInstance().mouseHandler.isLeftPressed()) {
			finishDragging();
		}
	}

	@Override
	public void callMouseReleased(int x, int y) {
		if (this.dragging) {
			finishDragging();
		}
	}

	@Override
	public void guiClosed() {
		this.dragging = false;
	}

	private void finishDragging() {
		this.dragging = false;
		playClickSound();
		sliderItem.action();
	}

	private String getValue() {
		String customValue = sliderItem.getCustomValue(this.value);
		if (customValue != null)
			return customValue;
		return Math.round(this.value * (sliderItem.getMaxValue() - sliderItem.getMinValue()) + sliderItem.getMinValue()) + sliderItem.getSuffix();
	}
}
