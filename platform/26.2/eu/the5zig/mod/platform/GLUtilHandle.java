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

import eu.the5zig.mod.util.IGLUtil;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 1.20.1 implementation of {@link IGLUtil}.
 * <p>
 * There is no fixed-function pipeline any more: matrices are the {@link com.mojang.blaze3d.vertex.PoseStack}
 * of the frame currently held by {@link RenderContext}, state changes go through {@link RenderSystem} and
 * everything that only existed for the GL immediate mode renderer (lighting, alpha test, matrix mode, fog
 * on GUI geometry) is a no-op. None of the calls may crash when there is no frame being drawn.
 */
public class GLUtilHandle implements IGLUtil {

	/**
	 * Number of matrices this handle pushed itself, so an unbalanced {@link #popMatrix()} of the core never
	 * pops the pose stack of the screen that is being rendered.
	 */
	private int pushedMatrices;

	@Override
	public void enableBlend() {
		// 26.x render pipelines declare blending per pipeline; there is no global state to toggle.
	}

	@Override
	public void disableBlend() {
		// See enableBlend().
	}

	@Override
	public void scale(float x, float y, float z) {
		GuiGraphicsExtractor graphics = RenderContext.get();
		if (graphics != null) {
			graphics.pose().scale(x, y);
		}
	}

	@Override
	public void translate(float x, float y, float z) {
		GuiGraphicsExtractor graphics = RenderContext.get();
		if (graphics != null) {
			graphics.pose().translate(x, y);
		}
	}

	@Override
	public void color(float r, float g, float b, float a) {
		// 26.x removed the global shader colour: tints are per-draw arguments, and the blit overloads
		// the platform layer uses are untinted. The call is kept so the core's draw sequences stay
		// balanced, but it no longer changes any state.
	}

	@Override
	public void color(float r, float g, float b) {
		color(r, g, b, 1.0f);
	}

	@Override
	public void pushMatrix() {
		GuiGraphicsExtractor graphics = RenderContext.get();
		if (graphics != null) {
			graphics.pose().pushMatrix();
			pushedMatrices++;
		}
	}

	@Override
	public void popMatrix() {
		GuiGraphicsExtractor graphics = RenderContext.get();
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
		// Handled by the PoseStack; the core only ever calls this right after matrixMode(GL_MODELVIEW).
	}

	@Override
	public void clear(int i) {
		// Clearing inside the GUI pass would erase the whole framebuffer; the GUI render state owns
		// the clear now, so this is a no-op.
	}

	@Override
	public void disableDepth() {
		// Depth state is baked into the GUI pipelines.
	}

	@Override
	public void enableDepth() {
		// See disableDepth().
	}

	@Override
	public void depthMask(boolean b) {
		// See disableDepth().
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
		// Blend functions are fixed by the pipeline now.
	}

	@Override
	public void disableAlpha() {
		// The alpha test was removed from the render pipeline in 1.17+.
	}
}
