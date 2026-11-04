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
import eu.the5zig.mod.util.GuiContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link Screen} the mod's own {@link Gui} screens are rendered in.
 * <p>
 * The core of the mod was written against 1.15, where the screen drew itself through a globally
 * reachable tessellator. In 1.19.2 the frame arrives as a {@link PoseStack}; it is wrapped in a
 * {@link GuiContext} and published to {@link RenderContext} for the duration of
 * {@link #render(PoseStack, int, int, float)} so that the core's {@code IVariables#drawString(...)}
 * and friends keep working unchanged.
 */
public class GuiHandle extends Screen implements IGuiHandle {

	/**
	 * 1.15's {@code Screen#blit} drew from whatever texture had been bound last. 1.20.1 has no
	 * texture binding at all, so {@link BoundTexture} records it instead.
	 */
	private static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation("minecraft", "textures/gui/widgets.png");

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
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		RenderContext.set(poseStack);
		GuiContext graphics = RenderContext.get();
		try {
			// 1.15's Screen#render drew the default background itself; modern screens do it explicitly.
			this.renderBackground(poseStack);
			child.drawScreen0(mouseX, mouseY, partialTick);
			// Screen#render renders the screen's own widgets, which is a no-op for this wrapper.
			super.render(poseStack, mouseX, mouseY, partialTick);
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
		child.keyTyped0(character, modifiers);
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
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		boolean result = super.mouseScrolled(mouseX, mouseY, delta);
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
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		this.renderBackground(graphics.pose());
	}

	@Override
	public void drawMenuBackground() {
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		this.renderDirtBackground(0);
	}

	@Override
	public void callDrawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height) {
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		graphics.blit(getBoundTexture(), x, y, textureX, textureY, width, height);
	}

	@Override
	public void callDrawHoveringText(List<String> lines, int x, int y) {
		GuiContext graphics = RenderContext.get();
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
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		// (resourceLocation, x, y, blitOffset, u, v, width, height, textureWidth, textureHeight)
		graphics.blit(getBoundTexture(), x, y, 0, u, v, width, height, (int) textureWidth, (int) textureHeight);
	}

	public static void drawRect(double left, double top, double right, double bottom, int color) {
		GuiContext graphics = RenderContext.get();
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
		GuiContext graphics = RenderContext.get();
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
}
