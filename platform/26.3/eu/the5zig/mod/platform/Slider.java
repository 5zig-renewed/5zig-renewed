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

import eu.the5zig.mod.util.SliderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Created by 5zig.
 * All rights reserved © 2015
 * <p>
 * 26.x port: the slider keeps 1.15's drawing model (a track plus a knob) but paints it with plain
 * rectangles instead of the removed {@code widgets.png} nine-slice, which no longer exists as a GUI
 * texture in 26.x.
 */
public class Slider extends Button {

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
	protected void drawWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!isVisible()) {
			return;
		}
		if (this.dragging) {
			updateValueFromMouse(mouseX);
		}
		this.hovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + callGetWidth()
				&& mouseY < getY() + callGetHeight();
		graphics.fill(getX(), getY(), getX() + callGetWidth(), getY() + callGetHeight(), 0xFF2B2B2B);
		graphics.outline(getX(), getY(), callGetWidth(), callGetHeight(), 0xFFFFFFFF);
		int knobWidth = 8;
		int knobX = getX() + (int) (this.value * (callGetWidth() - knobWidth));
		graphics.fill(knobX, getY(), knobX + knobWidth, getY() + callGetHeight(), 0xFF8A8A8A);
		graphics.outline(knobX, getY(), knobWidth, callGetHeight(), 0xFF000000);
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
