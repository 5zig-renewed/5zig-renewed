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

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A texture whose contents are supplied as a {@link BufferedImage} instead of a resource pack file.
 * <p>
 * 1.15 uploaded the image straight through {@code TextureUtil} whenever the texture id was requested.
 * 1.20.1 keeps the same model ({@link NativeImage} + {@code TextureUtil.prepareImage} + upload), but
 * the upload has to happen on the render thread, so it is deferred until the texture is actually bound
 * for drawing. Callers may set the image from any thread.
 * <p>
 * 1.21.4 reworked the texture stack: {@code SimpleTexture} became a {@code ReloadableTexture} that has
 * to produce {@code TextureContents} from a resource pack, and {@link AbstractTexture} lost its
 * {@code load(ResourceManager)} contract. 1.21.3 still declares it abstract, so it is implemented here
 * as a no-op: these textures never come from a pack, the GL id is allocated on first use and the pixels
 * are uploaded by hand.
 */
public class SimpleTexture extends AbstractTexture {

	private boolean textureUploaded;
	private BufferedImage bufferedImage;

	public SimpleTexture() {
	}

	/**
	 * Uploads the current buffered image to the GPU if that has not happened yet. Does nothing when no
	 * image has been set or when called off the render thread (the next bind on the render thread will
	 * pick the image up).
	 */
	public synchronized void checkTextureUploaded() {
		if (textureUploaded || bufferedImage == null) {
			return;
		}
		if (!RenderSystem.isOnRenderThreadOrInit()) {
			return;
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(bufferedImage, "png", out);
			NativeImage image = NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
			TextureUtil.prepareImage(super.getId(), 0, image.getWidth(), image.getHeight());
			// 1.21.3 still has the pre-1.21.4 upload shape: the eight-argument overload of 1.21.4
			// (level, x, y, skipRows, skipPixels, width, height, close) corresponds to this nine-argument
			// one with an extra leading blur flag set to false.
			image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), false, true);
			// Mark done before touching the filter: setFilter binds, and bind -> getId() would
			// re-enter this method while textureUploaded is still false.
			textureUploaded = true;
			// 1.21.4 collapsed the blur/clamp/mipmap/close flags of NativeImage#upload into a single
			// "close" boolean; 1.21.3 still takes them separately. Either way filtering is applied
			// through AbstractTexture#setFilter afterwards. GUI textures are always nearest filtered.
			setFilter(false, false);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public synchronized void setBufferedImage(BufferedImage bufferedImage) {
		this.textureUploaded = false;
		this.bufferedImage = bufferedImage;
	}

	public BufferedImage getBufferedImage() {
		return bufferedImage;
	}

	/**
	 * 1.21.3-only contract: {@link AbstractTexture} is still reloadable in this version. These textures
	 * are supplied by the mod, never by a resource pack, so nothing has to be (re)loaded from disk.
	 */
	@Override
	public void load(ResourceManager resourceManager) {
		// no-op: the image is pushed through setBufferedImage/checkTextureUploaded
	}

	@Override
	public int getId() {
		checkTextureUploaded();
		return super.getId();
	}
}
