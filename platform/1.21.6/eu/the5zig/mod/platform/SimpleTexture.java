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
import eu.the5zig.mod.MinecraftFactory;
import net.minecraft.client.renderer.texture.DynamicTexture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A texture whose contents are supplied as a {@link BufferedImage} instead of a resource pack file.
 * <p>
 * 1.21.11 removed the numeric GL texture ids, {@code TextureUtil.prepareImage} and the synchronous
 * {@code ImageIO}-style upload path: a texture is now a {@code GpuTexture} owned by an
 * {@link DynamicTexture}, which allocates on construction and re-uploads its {@link NativeImage} on
 * {@link DynamicTexture#upload()}. This class therefore wraps a dynamic texture instead of extending
 * the (now resource-pack-only) vanilla {@code SimpleTexture}. Callers may set the image from any
 * thread; the upload itself is deferred to the render thread.
 */
public class SimpleTexture extends DynamicTexture {

	private boolean textureUploaded;
	private BufferedImage bufferedImage;

	public SimpleTexture() {
		// 1x1 placeholder; replaced by the first setBufferedImage/checkTextureUploaded pair.
		super(() -> "5zig buffered texture", new NativeImage(1, 1, false));
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
		if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
			return;
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(bufferedImage, "png", out);
			NativeImage image = NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
			setPixels(image);
			upload();
			textureUploaded = true;
		} catch (IOException e) {
			MinecraftFactory.getClassProxyCallback().getLogger().error("Unable to upload a 5zig texture", e);
		}
	}

	public synchronized void setBufferedImage(BufferedImage bufferedImage) {
		this.textureUploaded = false;
		this.bufferedImage = bufferedImage;
	}

	public BufferedImage getBufferedImage() {
		return bufferedImage;
	}
}
