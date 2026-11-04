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
 * A texture whose pixels are pushed in by the mod (capes, item models) instead of being loaded from
 * a resource pack.
 * <p>
 * 26.x note: uploading a raw {@code NativeImage} through {@code TextureUtil#prepareImage} /
 * {@code NativeImage#upload} is gone - textures are GPU resources created by the device and managed
 * by the {@code TextureManager}. Porting the upload path means re-implementing it on top of
 * {@code GpuTexture}/{@code CommandEncoder}, which is not done in this build, so the upload is
 * refused with a precise message instead of silently producing a white cape.
 */
public class SimpleTexture {

	private BufferedImage bufferedImage;

	public SimpleTexture() {
	}

	/**
	 * Uploads the current buffered image to the GPU if that has not happened yet.
	 *
	 * @throws UnsupportedOperationException always, until the GPU upload path is ported.
	 */
	public synchronized void checkTextureUploaded() {
		if (bufferedImage == null) {
			return;
		}
		throw new UnsupportedOperationException("5zig 26.1.1: dynamic texture upload is not implemented "
				+ "(26.x textures are GPU resources; the cape/item-model upload path still needs porting)");
	}

	public synchronized void setBufferedImage(BufferedImage bufferedImage) {
		this.bufferedImage = bufferedImage;
	}

	public BufferedImage getBufferedImage() {
		return bufferedImage;
	}

	public int getId() {
		throw new UnsupportedOperationException("5zig 26.1.1: GL texture ids no longer exist "
				+ "(AbstractTexture exposes a GpuTexture instead)");
	}
}
