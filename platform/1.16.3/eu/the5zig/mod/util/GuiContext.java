/*
 * Copyright (c) 2019-2020 5zig Reborn
 * Copyright (c) 2015-2019 5zig
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

package eu.the5zig.mod.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import com.mojang.math.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 1.16.5 rendering facade.
 * <p>
 * 1.16.5 draws with a {@link PoseStack} plus the static {@code GuiComponent} helpers, exactly like
 * the 1.19.2 layer this platform was branched from. What differs is everything around it:
 * <ul>
 *     <li>there is no shader-object API ({@code RenderSystem#setShader} arrives in 1.17); shaders are
 *     fixed-function and configured through {@code RenderSystem} state calls and the
 *     {@code Tesselator}/{@code BufferUploader} immediate-mode pair,</li>
 *     <li>{@code BufferBuilder#begin} takes a raw GL mode int ({@code 7} = quads) instead of
 *     {@code VertexFormat.Mode},</li>
 *     <li>texture binding goes through {@code TextureManager#bind}, there is no
 *     {@code setShaderTexture},</li>
 *     <li>colour tinting is {@code RenderSystem#color4f} (no {@code setShaderColor}),</li>
 *     <li>scissoring is expressed in window pixels with a bottom-left origin, so the GUI scale and
 *     the flip have to be applied by hand.</li>
 * </ul>
 * The public surface is deliberately identical to the 1.19.2/1.20 {@code GuiContext} so the rest of
 * the platform sources keep the 1.20 call shape.
 */
public final class GuiContext {

	/** Default texture size used by the single-size blit overloads. */
	private static final int DEFAULT_TEXTURE_SIZE = 256;

	/** {@code GL11.GL_QUADS}; 1.16.5's BufferBuilder takes the raw mode. */
	private static final int GL_QUADS = 7;

	private final PoseStack poseStack;

	public GuiContext(PoseStack poseStack) {
		this.poseStack = poseStack;
	}

	/**
	 * @return the stack every draw call of this context is applied to.
	 */
	public PoseStack pose() {
		return poseStack;
	}

	// ------------------------------------------------------------------------------------------
	// Solid shapes
	// ------------------------------------------------------------------------------------------

	public void fill(int x1, int y1, int x2, int y2, int color) {
		GuiComponent.fill(poseStack, x1, y1, x2, y2, color);
	}

	public void fillGradient(int x1, int y1, int x2, int y2, int colorTop, int colorBottom) {
		fillGradient(x1, y1, x2, y2, colorTop, colorBottom, 0);
	}

	/**
	 * 1.20 signature kept for source compatibility. The {@code RenderType} only selected a buffer in
	 * 1.20 and has no equivalent here, because 1.16.5 draws GUI geometry immediately.
	 */
	public void fillGradient(Object renderType, int x1, int y1, int x2, int y2, int colorTop, int colorBottom, int z) {
		fillGradient(x1, y1, x2, y2, colorTop, colorBottom, z);
	}

	public void fillGradient(int x1, int y1, int x2, int y2, int colorTop, int colorBottom, int z) {
		float topAlpha = (colorTop >> 24 & 0xFF) / 255.0F;
		float topRed = (colorTop >> 16 & 0xFF) / 255.0F;
		float topGreen = (colorTop >> 8 & 0xFF) / 255.0F;
		float topBlue = (colorTop & 0xFF) / 255.0F;
		float bottomAlpha = (colorBottom >> 24 & 0xFF) / 255.0F;
		float bottomRed = (colorBottom >> 16 & 0xFF) / 255.0F;
		float bottomGreen = (colorBottom >> 8 & 0xFF) / 255.0F;
		float bottomBlue = (colorBottom & 0xFF) / 255.0F;

		Matrix4f matrix = poseStack.last().pose();
		RenderSystem.disableTexture();
		RenderSystem.enableBlend();
		RenderSystem.disableAlphaTest();
		RenderSystem.defaultBlendFunc();
		// Smooth shading interpolates the per-vertex colours across the quad: that is what makes this
		// a gradient rather than a flat block.
		RenderSystem.shadeModel(7425);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(GL_QUADS, DefaultVertexFormat.POSITION_COLOR);
		buffer.vertex(matrix, x2, y1, z).color(topRed, topGreen, topBlue, topAlpha).endVertex();
		buffer.vertex(matrix, x1, y1, z).color(topRed, topGreen, topBlue, topAlpha).endVertex();
		buffer.vertex(matrix, x1, y2, z).color(bottomRed, bottomGreen, bottomBlue, bottomAlpha).endVertex();
		buffer.vertex(matrix, x2, y2, z).color(bottomRed, bottomGreen, bottomBlue, bottomAlpha).endVertex();
		buffer.end();
		BufferUploader.end(buffer);
		RenderSystem.shadeModel(7424);
		RenderSystem.disableBlend();
		RenderSystem.enableAlphaTest();
		RenderSystem.enableTexture();
	}

	// ------------------------------------------------------------------------------------------
	// Texture blits
	// ------------------------------------------------------------------------------------------

	public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
		blit(texture, x, y, 0, u, v, width, height, DEFAULT_TEXTURE_SIZE, DEFAULT_TEXTURE_SIZE);
	}

	public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height, int textureWidth, int textureHeight) {
		blit(texture, x, y, 0, u, v, width, height, textureWidth, textureHeight);
	}

	public void blit(ResourceLocation texture, int x, int y, int blitOffset, int u, int v, int width, int height, int textureWidth, int textureHeight) {
		bind(texture);
		GuiComponent.blit(poseStack, x, y, blitOffset, (float) u, (float) v, width, height, textureWidth, textureHeight);
	}

	public void blit(ResourceLocation texture, int x, int y, int blitOffset, float u, float v, int width, int height, int textureWidth, int textureHeight) {
		bind(texture);
		GuiComponent.blit(poseStack, x, y, blitOffset, u, v, width, height, textureWidth, textureHeight);
	}

	public void blit(ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
		bind(texture);
		GuiComponent.blit(poseStack, x, y, 0, u, v, width, height, textureWidth, textureHeight);
	}

	public void blit(ResourceLocation texture, int x, int y, int width, int height, float u, float v, int uWidth, int vHeight, int textureWidth, int textureHeight) {
		bind(texture);
		GuiComponent.blit(poseStack, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
	}

	public void blit(int x, int y, int blitOffset, int width, int height, TextureAtlasSprite sprite) {
		GuiComponent.blit(poseStack, x, y, blitOffset, width, height, sprite);
	}

	/**
	 * Tiles a texture region over an area, like 1.20's {@code blitRepeating}. 1.16.5 has no helper
	 * for this, so the tiles are emitted one by one; the callers use it for the dirt background and
	 * the scrolling list gradient only.
	 */
	public void blitRepeating(ResourceLocation texture, int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {
		bind(texture);
		for (int doneX = 0; doneX < width; doneX += uWidth) {
			int tileWidth = Math.min(uWidth, width - doneX);
			for (int doneY = 0; doneY < height; doneY += vHeight) {
				int tileHeight = Math.min(vHeight, height - doneY);
				GuiComponent.blit(poseStack, x + doneX, y + doneY, 0, (float) u, (float) v, tileWidth, tileHeight, DEFAULT_TEXTURE_SIZE, DEFAULT_TEXTURE_SIZE);
			}
		}
	}

	/**
	 * Nine-slice blit (1.20's {@code blitNineSliced}) used by the button and slider backgrounds.
	 * Corners are drawn natively, edges and the centre are stretched.
	 * <p>
	 * Like vanilla, the sub-blits are addressed in the 256x256 GUI texture space; {@code textureWidth}
	 * / {@code textureHeight} describe the sprite region and are only used for the equal-size fast
	 * path and the edge extents.
	 */
	public void blitNineSliced(ResourceLocation texture, int x, int y, int width, int height, int slice, int pad,
			int textureWidth, int textureHeight, int u, int v) {
		int left = Math.min(slice, width / 2);
		int right = Math.min(slice, width / 2);
		int top = Math.min(slice, height / 2);
		int bottom = Math.min(slice, height / 2);

		if (width == textureWidth && height == textureHeight) {
			blit(texture, x, y, u, v, width, height);
			return;
		}
		if (height == textureHeight) {
			blitRegion(texture, x, y, left, height, u, v, left, height);
			blitRegion(texture, x + left, y, width - right - left, height, u + left, v, textureWidth - right - left, height);
			blitRegion(texture, x + width - right, y, right, height, u + textureWidth - right, v, right, height);
			return;
		}
		if (width == textureWidth) {
			blitRegion(texture, x, y, width, top, u, v, width, top);
			blitRegion(texture, x, y + top, width, height - bottom - top, u, v + top, width, textureHeight - bottom - top);
			blitRegion(texture, x, y + height - bottom, width, bottom, u, v + textureHeight - bottom, width, bottom);
			return;
		}

		blitRegion(texture, x, y, left, top, u, v, left, top);
		blitRegion(texture, x + left, y, width - right - left, top, u + left, v, textureWidth - right - left, top);
		blitRegion(texture, x + width - right, y, right, top, u + textureWidth - right, v, right, top);
		blitRegion(texture, x, y + top, left, height - bottom - top, u, v + top, left, textureHeight - bottom - top);
		blitRegion(texture, x + left, y + top, width - right - left, height - bottom - top, u + left, v + top,
				textureWidth - right - left, textureHeight - bottom - top);
		blitRegion(texture, x + width - right, y + top, right, height - bottom - top, u + textureWidth - right, v + top,
				right, textureHeight - bottom - top);
		blitRegion(texture, x, y + height - bottom, left, bottom, u, v + textureHeight - bottom, left, bottom);
		blitRegion(texture, x + left, y + height - bottom, width - right - left, bottom, u + left, v + textureHeight - bottom,
				textureWidth - right - left, bottom);
		blitRegion(texture, x + width - right, y + height - bottom, right, bottom, u + textureWidth - right, v + textureHeight - bottom, right, bottom);
	}

	/**
	 * Stretched blit addressed in 256x256 GUI texture pixels, matching the widget textures.
	 */
	private void blitRegion(ResourceLocation texture, int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {
		bind(texture);
		GuiComponent.blit(poseStack, x, y, width, height, u / 256.0F, v / 256.0F, uWidth, vHeight, 256, 256);
	}

	private static void bind(ResourceLocation texture) {
		// 1.16.5's GuiComponent helpers assume the caller enabled texturing/blending (1.20's
		// GuiGraphics did this per RenderType), and GuiComponent#fill leaves both in whatever state
		// it used. Re-assert them so a fill followed by a blit always draws textured pixels.
		RenderSystem.enableTexture();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		Minecraft.getInstance().getTextureManager().bind(texture);
	}

	// ------------------------------------------------------------------------------------------
	// Text
	// ------------------------------------------------------------------------------------------

	public int drawString(Font font, String text, int x, int y, int color) {
		return font.drawShadow(poseStack, text, x, y, color);
	}

	public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
		return shadow ? font.drawShadow(poseStack, text, x, y, color) : font.draw(poseStack, text, x, y, color);
	}

	public int drawString(Font font, Component text, int x, int y, int color) {
		return font.drawShadow(poseStack, text, x, y, color);
	}

	public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
		return shadow ? font.drawShadow(poseStack, text, x, y, color) : font.draw(poseStack, text, x, y, color);
	}

	public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
		return font.drawShadow(poseStack, text, x, y, color);
	}

	public void drawCenteredString(Font font, String text, int x, int y, int color) {
		font.drawShadow(poseStack, text, x - font.width(text) / 2, y, color);
	}

	public void drawCenteredString(Font font, Component text, int x, int y, int color) {
		FormattedCharSequence sequence = text.getVisualOrderText();
		font.drawShadow(poseStack, sequence, x - font.width(sequence) / 2, y, color);
	}

	/**
	 * Word-wrapped, centered tooltip text, mirroring 1.20's
	 * {@code GuiGraphics#drawWordWrap}.
	 */
	public void drawWordWrap(Font font, FormattedText text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font.split(text, width)) {
			font.drawShadow(poseStack, line, x, y, color);
			y += font.lineHeight;
		}
	}

	// ------------------------------------------------------------------------------------------
	// State
	// ------------------------------------------------------------------------------------------

	/**
	 * Tints the following blits. In 1.20 this also stores the colour for text; the 1.16.5 text
	 * renderer takes the colour per call, so the fixed-function colour is all that is needed here.
	 */
	public void setColor(float red, float green, float blue, float alpha) {
		RenderSystem.color4f(red, green, blue, alpha);
	}

	/**
	 * 1.16.5's {@code RenderSystem#enableScissor} works in window pixels with the origin at the
	 * bottom-left, so the GUI-scaled rectangle is converted here.
	 */
	public void enableScissor(int x1, int y1, int x2, int y2) {
		int left = Math.min(x1, x2);
		int top = Math.min(y1, y2);
		int right = Math.max(x1, x2);
		int bottom = Math.max(y1, y2);
		Minecraft minecraft = Minecraft.getInstance();
		double scale = minecraft.getWindow().getGuiScale();
		int windowHeight = minecraft.getWindow().getHeight();
		RenderSystem.enableScissor(
				(int) (left * scale),
				(int) (windowHeight - bottom * scale),
				(int) ((right - left) * scale),
				(int) ((bottom - top) * scale));
	}

	public void disableScissor() {
		RenderSystem.disableScissor();
	}

	/**
	 * Draws a multi-line tooltip through the current screen, which owns the tooltip style and the
	 * font. 1.16.5 exposes this on {@code Screen} rather than on a graphics object.
	 */
	public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
		if (lines.isEmpty()) {
			return;
		}
		if (Minecraft.getInstance().screen != null) {
			Minecraft.getInstance().screen.renderComponentTooltip(poseStack, lines, x, y);
			return;
		}
		// No screen (should not happen while a GUI is being drawn): fall back to plain lines.
		int lineY = y;
		for (Component line : lines) {
			drawString(font, line, x, lineY, 0xFFFFFF);
			lineY += font.lineHeight + 2;
		}
	}

	/**
	 * Draws the tooltip of a chat style's {@code SHOW_TEXT} hover event.
	 * <p>
	 * 1.20's {@code GuiGraphics} owns this method; in 1.16.5 it lives on {@code Screen} as a
	 * protected method, so the {@code SHOW_TEXT} case (the only one chat components use) is
	 * re-implemented here through the public tooltip API.
	 */
	public void renderComponentHoverEffect(Font font, net.minecraft.network.chat.Style style, int x, int y) {
		if (style == null) {
			return;
		}
		net.minecraft.network.chat.HoverEvent hover = style.getHoverEvent();
		if (hover == null || hover.getAction() != net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT) {
			return;
		}
		net.minecraft.network.chat.Component text = hover.getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT);
		if (text != null) {
			renderComponentTooltip(font, java.util.Collections.singletonList(text), x, y);
		}
	}

	/**
	 * @return the unformatted text of the wrapped lines, for callers that need the layout only.
	 */
	public static List<FormattedCharSequence> wrap(Font font, FormattedText text, int width) {
		return font.split(text, width);
	}

	/**
	 * Kept so call sites that measured a wrapped block can be ported one to one.
	 */
	public static int wrappedHeight(Font font, FormattedText text, int width) {
		return ComponentRenderUtils.wrapComponents(text, width, font).size() * font.lineHeight;
	}
}
