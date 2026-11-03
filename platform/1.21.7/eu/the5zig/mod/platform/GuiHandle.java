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

import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.gui.Gui;
import eu.the5zig.mod.gui.IGuiHandle;
import eu.the5zig.mod.util.BoundTexture;
import eu.the5zig.mod.util.LegacyText;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@link Screen} the mod's own {@link Gui} screens are rendered in.
 * <p>
 * The core of the mod was written against 1.15, where the screen drew itself through a globally
 * reachable tessellator. Modern Minecraft passes a {@link GuiGraphics} down the render call, so it
 * is published to {@link RenderContext} for the duration of
 * {@link #render(GuiGraphics, int, int, float)} and the core's {@code IVariables#drawString(...)}
 * and friends keep working unchanged.
 * <p>
 * 1.21.9 replaced the {@code (int, int, int)} mouse/keyboard callbacks with the
 * {@code net.minecraft.client.input} event records; the core still speaks plain integers, so the
 * overrides unwrap the records here.
 */
public class GuiHandle extends Screen implements IGuiHandle {

	/**
	 * 1.15's {@code Screen#blit} drew from whatever texture had been bound last. Modern Minecraft
	 * has no texture binding at all, so {@link BoundTexture} records it instead.
	 */
	private static final net.minecraft.resources.ResourceLocation DEFAULT_TEXTURE =
			net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/widgets.png");

	private Gui child;

	public GuiHandle(Gui child) {
		super(Component.literal("The 5zig Mod"));
		this.child = child;
	}

	public static net.minecraft.resources.ResourceLocation getBoundTexture() {
		return BoundTexture.currentOr(DEFAULT_TEXTURE);
	}

	@Override
	protected void init() {
		child.initGui0();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		RenderContext.set(graphics);
		try {
			// 1.15's Screen#render drew the default background itself; modern screens do it explicitly.
			this.renderBackground(graphics, mouseX, mouseY, partialTick);
			child.drawScreen0(mouseX, mouseY, partialTick);
			// Screen#render renders the screen's own widgets, which is a no-op for this wrapper.
			super.render(graphics, mouseX, mouseY, partialTick);
		} finally {
			RenderContext.clear();
		}
	}

	@Override
	public void tick() {
		child.tick0();
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		boolean result = super.charTyped(codePoint, modifiers);
		child.keyTyped0(codePoint, modifiers);
		return result;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		boolean result = super.keyPressed(keyCode, scanCode, modifiers);
		child.keyPressed0(keyCode, scanCode, modifiers);
		return result;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		boolean result = super.mouseClicked(mouseX, mouseY, button);
		child.mouseClicked0((int) mouseX, (int) mouseY, button);
		return result;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		boolean result = super.mouseReleased(mouseX, mouseY, button);
		child.mouseReleased0((int) mouseX, (int) mouseY, button);
		return result;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		boolean result = super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		child.mouseDragged0(mouseX, mouseY, button, dragX, dragY);
		return result;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		boolean result = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		child.mouseScrolled0(scrollY);
		return result;
	}

	@Override
	public void onClose() {
		The5zigMod.getVars().displayScreen(child.lastScreen);
		child.guiClosed0();
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public void setResolution(int width, int height) {
		// Deliberately not Screen#resize: that rebuilds the widgets, i.e. re-runs the child's
		// initGui0() while it is still initializing. 1.15's setSize only assigned the fields.
		this.width = width;
		this.height = height;
	}

	@Override
	public void callDrawDefaultBackground() {
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		this.renderBackground(graphics, 0, 0, 0.0f);
	}

	@Override
	public void drawMenuBackground() {
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		this.renderMenuBackground(graphics);
	}

	@Override
	public void callDrawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height) {
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		// 1.21.5+ requires an explicit render pipeline for every blit; GUI textures use the
		// textured-GUI pipeline (position/uv/colour, no depth).
		graphics.blit(RenderPipelines.GUI_TEXTURED, getBoundTexture(), x, y, textureX, textureY, width, height, 256, 256);
	}

	@Override
	public void callDrawHoveringText(List<String> lines, int x, int y) {
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null || lines.isEmpty()) {
			return;
		}
		List<Component> components = new ArrayList<>(lines.size());
		for (String line : lines) {
			components.add(LegacyText.fromLegacy(line));
		}
		graphics.setTooltipForNextFrame(Minecraft.getInstance().font, components, Optional.empty(), x, y);
	}

	/**
	 * Draws a piece of a texture at its native size, from the texture bound last.
	 */
	public static void callDrawModalRectWithCustomSizedTexture(int x, int y, float u, float v, int width, int height, float textureWidth, float textureHeight) {
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		// 1.21.x: (pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight)
		graphics.blit(RenderPipelines.GUI_TEXTURED, getBoundTexture(), x, y, u, v, width, height,
				(int) textureWidth, (int) textureHeight);
	}

	public static void drawRect(double left, double top, double right, double bottom, int color) {
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		int x1 = (int) Math.min(left, right);
		int y1 = (int) Math.min(top, bottom);
		int x2 = (int) Math.max(left, right);
		int y2 = (int) Math.max(top, bottom);
		graphics.fill(x1, y1, x2, y2, color);
	}

	/**
	 * @param vertical true to interpolate from the bottom to the top instead of top to bottom.
	 */
	public static void drawGradientRect(double left, double top, double right, double bottom, int startColor, int endColor, boolean vertical) {
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		int x1 = (int) Math.min(left, right);
		int y1 = (int) Math.min(top, bottom);
		int x2 = (int) Math.max(left, right);
		int y2 = (int) Math.max(top, bottom);
		// 1.15 fed the colours in by vertex order, modern fillGradient is always top-to-bottom, so a
		// "vertical" gradient simply swaps the two colours.
		graphics.fillGradient(x1, y1, x2, y2, vertical ? endColor : startColor, vertical ? startColor : endColor);
	}

	public Gui getChild() {
		return child;
	}
}
