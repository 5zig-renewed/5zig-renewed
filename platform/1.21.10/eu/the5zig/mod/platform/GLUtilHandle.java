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

import com.mojang.blaze3d.opengl.GlStateManager;
import eu.the5zig.mod.util.IGLUtil;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 1.21.10 implementation of {@link IGLUtil}.
 * <p>
 * The 1.21.5 render-pipeline overhaul removed what was left of the global GL state API:
 * {@code RenderSystem} no longer has {@code setShaderColor}, {@code enableBlend},
 * {@code depthMask} or {@code blendFuncSeparate} - blending, depth and the colour tint are now
 * baked into the {@code RenderPipeline} a draw call selects, and per-draw tint is passed as the
 * {@code ARGB} argument of {@code GuiGraphics#blit}/{@code #fill}. Those calls therefore become
 * no-ops here; the few places that genuinely tinted (the chat-symbol backdrop) pass the tint to the
 * draw call directly in {@link Variables}.
 * <p>
 * Matrices are the 2D JOML {@code Matrix3x2fStack} of the frame published to
 * {@link RenderContext}, so the Z component of {@code scale}/{@code translate} is dropped. None of
 * the calls may crash when there is no frame being drawn.
 */
public class GLUtilHandle implements IGLUtil {

	/**
	 * Number of matrices this handle pushed itself, so an unbalanced {@link #popMatrix()} of the core never
	 * pops the matrix stack of the screen that is being rendered.
	 */
	private int pushedMatrices;

	@Override
	public void enableBlend() {
		// Blending is a property of the pipeline selected by each draw call.
	}

	@Override
	public void disableBlend() {
		// Blending is a property of the pipeline selected by each draw call.
	}

	@Override
	public void scale(float x, float y, float z) {
		GuiGraphics graphics = RenderContext.get();
		if (graphics != null) {
			graphics.pose().scale(x, y);
		}
	}

	@Override
	public void translate(float x, float y, float z) {
		GuiGraphics graphics = RenderContext.get();
		if (graphics != null) {
			graphics.pose().translate(x, y);
		}
	}

	@Override
	public void color(float r, float g, float b, float a) {
		// Global shader colour is gone; tint is passed per draw call.
	}

	@Override
	public void color(float r, float g, float b) {
		color(r, g, b, 1.0f);
	}

	@Override
	public void pushMatrix() {
		GuiGraphics graphics = RenderContext.get();
		if (graphics != null) {
			graphics.pose().pushMatrix();
			pushedMatrices++;
		}
	}

	@Override
	public void popMatrix() {
		GuiGraphics graphics = RenderContext.get();
		if (graphics != null && pushedMatrices > 0) {
			graphics.pose().popMatrix();
			pushedMatrices--;
		}
	}

	@Override
	public void matrixMode(int mode) {
		// GL matrix mode selection does not exist in the modern render pipeline.
	}

	@Override
	public void loadIdentity() {
		// Handled by the matrix stack; the core only ever calls this right after matrixMode(GL_MODELVIEW).
	}

	@Override
	public void clear(int i) {
		GlStateManager._clear(i);
	}

	@Override
	public void disableDepth() {
		// Depth state belongs to the pipeline; GUI pipelines never depth-test.
	}

	@Override
	public void enableDepth() {
		// Depth state belongs to the pipeline; GUI pipelines never depth-test.
	}

	@Override
	public void depthMask(boolean b) {
		// Depth writes belong to the pipeline.
	}

	@Override
	public void disableLighting() {
		// GUI shaders are unlit; nothing to disable.
	}

	@Override
	public void enableLighting() {
		// GUI shaders are unlit; nothing to enable.
	}

	@Override
	public void disableFog() {
		// GUI shaders do not sample fog.
	}

	@Override
	public void tryBlendFuncSeparate(int i, int i1, int i2, int i3) {
		// Blend function is a property of the pipeline.
	}

	@Override
	public void disableAlpha() {
		// The alpha test was removed from the render pipeline in 1.17+.
	}
}
