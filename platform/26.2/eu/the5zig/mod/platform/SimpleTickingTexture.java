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

import java.awt.image.BufferedImage;

/**
 * An animated 5zig texture (capes): a vertical sprite sheet whose frames are swapped once per client
 * tick.
 * <p>
 * 26.x note: the frame registration used {@code TextureManager#getTexture(location, null)} plus a
 * {@code Tickable} texture, both of which are gone. The frame bookkeeping is kept (so the caller can
 * still ask which frame is current), but no GPU texture is created - see {@link SimpleTexture}.
 */
public class SimpleTickingTexture extends SimpleTexture {

	private final ResourceLocation resourceLocation;

	private BufferedImage sheet;
	private int frameCount;
	private int index;

	public SimpleTickingTexture(ResourceLocation resourceLocation) {
		this.resourceLocation = resourceLocation;
	}

	public synchronized void setBufferedImage(BufferedImage bufferedImage) {
		this.sheet = bufferedImage;
		if (bufferedImage == null) {
			this.frameCount = 0;
			this.index = 0;
			return;
		}
		int frameUnit = Math.max(1, bufferedImage.getWidth() / 2);
		this.frameCount = Math.max(1, bufferedImage.getHeight() / frameUnit);
		this.index = 0;
	}

	public synchronized void tick() {
		if (frameCount > 1) {
			index = (index + 1) % frameCount;
		}
	}

	/**
	 * @return the resource location of the frame that should currently be drawn, or {@code null} while
	 * no image has been supplied yet.
	 */
	public synchronized ResourceLocation getCurrentResource() {
		if (sheet == null || frameCount == 0) {
			return null;
		}
		return new ResourceLocation(resourceLocation.callGetResourceDomain(),
				resourceLocation.callGetResourcePath() + index);
	}
}
