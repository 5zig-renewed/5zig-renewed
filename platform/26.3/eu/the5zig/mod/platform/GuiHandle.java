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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link Screen} the mod's own {@link Gui} screens are rendered in.
 * <p>
 * The core of the mod was written against 1.15, where the screen drew itself through a globally
 * reachable tessellator. 26.x hands a {@link GuiGraphicsExtractor} down the (renamed)
 * {@code Screen#extractRenderState} call, which is published to {@link RenderContext} for its
 * duration so that the core's {@code IVariables#drawString(...)} and friends keep working unchanged.
 * <p>
 * 26.x input notes: the integer-argument event methods became records
 * ({@link KeyEvent}, {@link CharacterEvent}, {@link MouseButtonEvent}); this adapter unwraps them
 * and forwards the same primitives the core has always used.
 */
public class GuiHandle extends Screen implements IGuiHandle {

	/**
	 * 1.15's {@code Screen#blit} drew from whatever texture had been bound last. 26.x has no texture
	 * binding at all, so {@link BoundTexture} records it instead.
	 */
	private static final Identifier DEFAULT_TEXTURE = Identifier.withDefaultNamespace("textures/gui/widgets.png");

	private Gui child;

	public GuiHandle(Gui child) {
		super(Component.literal("The 5zig Mod"));
		this.child = child;
	}

	public static Identifier getBoundTexture() {
		return BoundTexture.currentOr(DEFAULT_TEXTURE);
	}

	@Override
	protected void init() {
		child.initGui0();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		RenderContext.set(graphics);
		try {
			// 26.x: the background is extracted separately from the screen contents.
			this.extractBackground(graphics, mouseX, mouseY, partialTick);
			child.drawScreen0(mouseX, mouseY, partialTick);
			// Screen#extractRenderState extracts the screen's own widgets, a no-op for this wrapper.
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		} finally {
			RenderContext.clear();
		}
	}

	@Override
	public void tick() {
		child.tick0();
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		boolean result = super.charTyped(event);
		child.keyTyped0((char) event.codepoint(), 0);
		return result;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean result = super.keyPressed(event);
		// 26.3 renamed KeyEvent#scancode() to keycode() (key() is still the GLFW key, modifiers() the mods).
		child.keyPressed0(event.key(), event.keycode(), event.modifiers());
		return result;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		boolean result = super.mouseClicked(event, doubleClick);
		child.mouseClicked0((int) event.x(), (int) event.y(), event.button());
		return result;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean result = super.mouseReleased(event);
		child.mouseReleased0((int) event.x(), (int) event.y(), event.button());
		return result;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		boolean result = super.mouseDragged(event, dragX, dragY);
		child.mouseDragged0(event.x(), event.y(), event.button(), dragX, dragY);
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
		GuiGraphicsExtractor graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		this.extractBackground(graphics, 0, 0, 0.0F);
	}

	@Override
	public void drawMenuBackground() {
		GuiGraphicsExtractor graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		this.extractMenuBackground(graphics);
	}

	@Override
	public void callDrawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height) {
		GuiGraphicsExtractor graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		// The bound texture is a 256x256 GUI sheet; 26.x blit expects normalised UVs.
		blitPixels(graphics, getBoundTexture(), x, y, textureX, textureY, width, height, 256, 256);
	}

	@Override
	public void callDrawHoveringText(List<String> lines, int x, int y) {
		GuiGraphicsExtractor graphics = RenderContext.get();
		if (graphics == null || lines.isEmpty()) {
			return;
		}
		List<Component> components = new ArrayList<>(lines.size());
		for (String line : lines) {
			components.add(LegacyText.fromLegacy(line));
		}
		graphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, components, x, y);
	}

	/**
	 * Draws a piece of a texture at its native size, from the texture bound last.
	 */
	public static void callDrawModalRectWithCustomSizedTexture(int x, int y, float u, float v, int width, int height, float textureWidth, float textureHeight) {
		GuiGraphicsExtractor graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		blitPixels(graphics, getBoundTexture(), x, y, (int) u, (int) v, width, height,
				(int) textureWidth, (int) textureHeight);
	}

	/**
	 * 26.x {@code blit} takes the destination rect plus normalised texture coordinates, so the
	 * pixel-based UVs the core works with are divided by the texture size here.
	 */
	private static void blitPixels(GuiGraphicsExtractor graphics, Identifier texture, int x, int y,
								   int u, int v, int width, int height, int textureWidth, int textureHeight) {
		if (textureWidth <= 0 || textureHeight <= 0) {
			return;
		}
		float u0 = u / (float) textureWidth;
		float v0 = v / (float) textureHeight;
		graphics.blit(texture, x, y, x + width, y + height,
				u0, u0 + width / (float) textureWidth, v0, v0 + height / (float) textureHeight);
	}

	public static void drawRect(double left, double top, double right, double bottom, int color) {
		GuiGraphicsExtractor graphics = RenderContext.get();
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
		GuiGraphicsExtractor graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		int x1 = (int) Math.min(left, right);
		int y1 = (int) Math.min(top, bottom);
		int x2 = (int) Math.max(left, right);
		int y2 = (int) Math.max(top, bottom);
		// 1.15 fed the colours in by vertex order, the modern fillGradient is always
		// top-to-bottom, so a "vertical" gradient simply swaps the two colours.
		graphics.fillGradient(x1, y1, x2, y2, vertical ? endColor : startColor, vertical ? startColor : endColor);
	}

	public Gui getChild() {
		return child;
	}
}
