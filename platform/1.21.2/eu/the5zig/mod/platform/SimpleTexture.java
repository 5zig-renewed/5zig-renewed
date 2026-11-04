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
 * 1.21.2 already has the E8 texture stack: {@code SimpleTexture} is a {@code ReloadableTexture} that has
 * to produce {@code TextureContents} from a resource pack, so these textures (which never come from a
 * pack) derive from {@link AbstractTexture} directly - the GL id is allocated on first use and the
 * pixels are uploaded by hand. 1.21.2 still declares {@code AbstractTexture#load(ResourceManager)}
 * abstract (it only disappeared in 1.21.4), so it is implemented here as a no-op.
 */
public class SimpleTexture extends AbstractTexture {

	private boolean textureUploaded;
	private BufferedImage bufferedImage;

	public SimpleTexture() {
	}

	/**
	 * No-op: this texture is fed from a {@link BufferedImage} rather than a resource-pack file, so
	 * there is nothing to reload. Still abstract in 1.21.2.
	 */
	@Override
	public void load(net.minecraft.server.packs.resources.ResourceManager resourceManager) {
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
			// 1.21.2 still has the flag-shaped upload overloads but not the 1.21.4 eight-argument one:
			// the nine-argument form is (level, x, y, skipRows, skipPixels, width, height, mipmap, close).
			// Mipmapping stays off and the pixel buffer is released after the upload; filtering is
			// applied through AbstractTexture#setFilter below. GUI textures are always nearest filtered.
			image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), false, true);
			// Mark done before touching the filter: setFilter binds, and bind -> getId() would
			// re-enter this method while textureUploaded is still false.
			textureUploaded = true;
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

	@Override
	public int getId() {
		checkTextureUploaded();
		return super.getId();
	}
}
