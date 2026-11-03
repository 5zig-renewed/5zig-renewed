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
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import eu.the5zig.mod.MinecraftFactory;
import net.minecraft.client.renderer.texture.AbstractTexture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A texture whose contents are supplied as a {@link BufferedImage} instead of a resource pack file.
 * <p>
 * 1.21.5 (the render-pipeline overhaul) removed the numeric GL texture ids and the immediate
 * {@code NativeImage#upload} path: a texture is now a {@link GpuTexture} whose size is fixed when it
 * is created. The image size is only known once the caller supplies an image, so the GPU texture is
 * created on the render thread at that point (and recreated whenever the image changes size).
 * Callers may set the image from any thread; the upload itself is deferred.
 */
public class SimpleTexture extends AbstractTexture {

	private boolean textureUploaded;
	private BufferedImage bufferedImage;

	/** Retained because the GPU upload is asynchronous - closing it before the copy is fatal. */
	private NativeImage uploadedImage;

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
		if (!RenderSystem.isOnRenderThread()) {
			return;
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(bufferedImage, "png", out);
			NativeImage image = NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
			GpuDevice device = RenderSystem.getDevice();
			if (this.texture != null) {
				this.texture.close();
			}
			this.texture = device.createTexture(() -> "5zig buffered texture", TextureFormat.RGBA8,
					image.getWidth(), image.getHeight(), 1);
			device.createCommandEncoder().writeToTexture(this.texture, image);
			if (this.uploadedImage != null) {
				this.uploadedImage.close();
			}
			this.uploadedImage = image;
			textureUploaded = true;
		} catch (IOException e) {
			MinecraftFactory.getClassProxyCallback().getLogger().error("Unable to upload a 5zig texture", e);
		}
	}

	/**
	 * Makes sure a binding always finds a GPU texture, even before the first image arrives: GUI blits
	 * run on the render thread and cannot fall back to a null texture.
	 */
	@Override
	public GpuTexture getTexture() {
		if (this.texture == null && RenderSystem.isOnRenderThread()) {
			this.texture = RenderSystem.getDevice().createTexture(() -> "5zig empty texture",
					TextureFormat.RGBA8, 1, 1, 1);
		}
		return super.getTexture();
	}

	public synchronized void setBufferedImage(BufferedImage bufferedImage) {
		this.textureUploaded = false;
		this.bufferedImage = bufferedImage;
	}

	public BufferedImage getBufferedImage() {
		return bufferedImage;
	}
}
