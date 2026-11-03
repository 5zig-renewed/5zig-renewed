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

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * An animated 5zig texture (capes). The supplied image is a vertical sprite sheet whose frames are
 * {@code width / 2} pixels tall (the 2:1 face/cape layout of the 5zig texture service); every frame is
 * registered with the vanilla {@link TextureManager} as its own texture.
 * <p>
 * 1.21.11 removed {@code net.minecraft.client.renderer.texture.Tickable} (texture animation is now
 * driven by sprite contents metadata), so the current frame is derived from the wall clock instead of
 * being advanced by a client tick. That keeps the animation running without a per-frame driver and
 * costs nothing when the cape is not drawn.
 */
public class SimpleTickingTexture extends SimpleTexture {

	/** One animation step every 100 ms, the rate the 1.15 implementation used (2 ticks). */
	private static final long FRAME_MILLIS = 100L;

	private final ResourceLocation resourceLocation;

	private boolean framesUploaded;
	private List<BufferedImage> bufferedImages;
	private List<ResourceLocation> frames;
	private int index;
	private long lastFrameTime;

	public SimpleTickingTexture(ResourceLocation resourceLocation) {
		this.resourceLocation = resourceLocation;
	}

	private synchronized void checkFramesUploaded() {
		if (framesUploaded || frames == null || bufferedImages == null) {
			return;
		}
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		for (int i = 0; i < bufferedImages.size(); i++) {
			ResourceLocation frameLocation = new ResourceLocation(resourceLocation.callGetResourceDomain(),
					resourceLocation.callGetResourcePath() + i);
			net.minecraft.client.renderer.texture.AbstractTexture existing =
					textureManager.getTexture(frameLocation.toResourceLocation());
			SimpleTexture frameTexture;
			if (existing instanceof SimpleTexture) {
				frameTexture = (SimpleTexture) existing;
			} else {
				frameTexture = new SimpleTexture();
				textureManager.register(frameLocation.toResourceLocation(), frameTexture);
			}
			frameTexture.setBufferedImage(bufferedImages.get(i));
			frameTexture.checkTextureUploaded();
			frames.add(frameLocation);
		}
		framesUploaded = true;
	}

	public synchronized void setBufferedImage(BufferedImage bufferedImage) {
		if (bufferedImage == null) {
			frames = null;
			bufferedImages = null;
			index = 0;
			framesUploaded = false;
			return;
		}
		int frameUnit = Math.max(1, bufferedImage.getWidth() / 2);
		int parts = Math.max(1, bufferedImage.getHeight() / frameUnit);
		int partHeight = Math.max(1, bufferedImage.getHeight() / parts);
		bufferedImages = new ArrayList<>(parts);
		frames = new ArrayList<>(parts);
		for (int part = 0; part < parts; part++) {
			bufferedImages.add(bufferedImage.getSubimage(0, part * partHeight, bufferedImage.getWidth(), partHeight));
		}
		framesUploaded = false;
		index = 0;
		lastFrameTime = System.currentTimeMillis();
	}

	/**
	 * Advances the animation from the wall clock. Safe to call from the render thread every frame.
	 */
	public synchronized void tick() {
		checkFramesUploaded();
		if (!framesUploaded || frames == null || frames.size() <= 1) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastFrameTime < FRAME_MILLIS) {
			return;
		}
		lastFrameTime = now;
		index = (index + 1) % frames.size();
	}

	/**
	 * @return the resource location of the frame that should currently be drawn, or {@code null} while no
	 * image has been supplied yet.
	 */
	public synchronized ResourceLocation getCurrentResource() {
		tick();
		if (!framesUploaded || frames == null || index < 0 || index >= frames.size()) {
			return null;
		}
		return frames.get(index);
	}
}
