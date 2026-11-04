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
import eu.the5zig.mod.util.Keyboard;
import eu.the5zig.mod.util.LegacyText;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link Screen} the mod's own {@link Gui} screens are rendered in.
 * <p>
 * The core of the mod was written against 1.15, where the screen drew itself through a globally
 * reachable tessellator. In 1.20.1 every draw call needs a {@link GuiGraphics}, which is published
 * to {@link RenderContext} for the duration of {@link #render(GuiGraphics, int, int, float)} so that
 * the core's {@code IVariables#drawString(...)} and friends keep working unchanged.
 */
public class GuiHandle extends Screen implements IGuiHandle {

	/**
	 * 1.15's {@code Screen#blit} drew from whatever texture had been bound last. 1.20.1 has no
	 * texture binding at all, so {@link BoundTexture} records it instead.
	 */
	private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.parse("minecraft:textures/gui/widgets.png");

	private Gui child;

	public GuiHandle(Gui child) {
		super(Component.literal("The 5zig Mod"));
		this.child = child;
	}

	public static ResourceLocation getBoundTexture() {
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
			// 1.15's Screen#render drew the default background itself; 1.20.1 screens do it explicitly.
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
	public boolean charTyped(char character, int modifiers) {
		boolean result = super.charTyped(character, modifiers);
		child.keyTyped0(character, keyCodeFor(character));
		return result;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		boolean result = super.keyPressed(keyCode, scanCode, modifiers);
		deliverKeyType(keyCode);
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
		double delta = scrollY;
		child.mouseScrolled0(delta);
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
		this.renderBackground(graphics, 0, 0, 0.0F);
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
		graphics.blit(getBoundTexture(), x, y, textureX, textureY, width, height);
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
		graphics.renderComponentTooltip(Minecraft.getInstance().font, components, x, y);
	}

	/**
	 * Draws a piece of a texture at its native size, from the texture bound last.
	 */
	public static void callDrawModalRectWithCustomSizedTexture(int x, int y, float u, float v, int width, int height, float textureWidth, float textureHeight) {
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		// 1.20.1 signature: (resourceLocation, x, y, blitOffset, u, v, width, height, textureWidth, textureHeight)
		graphics.blit(getBoundTexture(), x, y, 0, u, v, width, height, (int) textureWidth, (int) textureHeight);
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
		// 1.15 fed the colours in by vertex order, 1.20.1's fillGradient is always top-to-bottom, so a
		// "vertical" gradient simply swaps the two colours.
		graphics.fillGradient(x1, y1, x2, y2, vertical ? endColor : startColor, vertical ? startColor : endColor);
	}

	public Gui getChild() {
		return child;
	}

	/**
	 * Maps a character from a {@code charTyped} callback to the platform key code that the core's
	 * {@link Gui#keyTyped0(char, int)} compares against. A char event carries no key code, but the
	 * core compares one for the printable keys it handles: Space (insert player position) and,
	 * where the platform reports a char while Ctrl is held, letters (the Ctrl+D / Ctrl+F
	 * shortcuts). Text fields only use the character itself, so everything unmapped returns 0.
	 */
	private static int keyCodeFor(char character) {
		if (character == ' ') {
			return Keyboard.KEY_SPACE;
		}
		// GLFW, LWJGL2 and SDL all number A-Z alphabetically and consecutively.
		if (character >= 'a' && character <= 'z') {
			return Keyboard.KEY_A + (character - 'a');
		}
		if (character >= 'A' && character <= 'Z') {
			return Keyboard.KEY_A + (character - 'A');
		}
		// Digits are only consecutive under GLFW, so they are mapped one by one.
		switch (character) {
			case '0':
				return Keyboard.KEY_0;
			case '1':
				return Keyboard.KEY_1;
			case '2':
				return Keyboard.KEY_2;
			case '3':
				return Keyboard.KEY_3;
			case '4':
				return Keyboard.KEY_4;
			case '5':
				return Keyboard.KEY_5;
			case '6':
				return Keyboard.KEY_6;
			case '7':
				return Keyboard.KEY_7;
			case '8':
				return Keyboard.KEY_8;
			case '9':
				return Keyboard.KEY_9;
			default:
				return 0;
		}
	}

	/**
	 * Delivers the keys that never produce a character to {@link Gui#keyTyped0(char, int)}.
	 * <p>
	 * The core handles Escape, Return, Tab, Delete and Backspace there, but a platform
	 * {@code keyPressed} callback is the only place those keys arrive, so they are forwarded with an
	 * empty character. Space is deliberately not listed: it also produces a char event, and
	 * handling it in both callbacks would insert the player position twice.
	 */
	private void deliverKeyType(int keyCode) {
		if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER
				|| keyCode == Keyboard.KEY_TAB || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
			child.keyTyped0('\0', keyCode);
		}
	}
}
