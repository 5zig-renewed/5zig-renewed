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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.Version;
import eu.the5zig.mod.api.rewards.RewardsCache;
import eu.the5zig.mod.gui.ingame.resource.CapeResource;
import eu.the5zig.mod.gui.ingame.resource.IResourceManager;
import eu.the5zig.mod.gui.ingame.resource.PlayerResource;
import eu.the5zig.mod.util.NetworkPlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Downloads the 5zig capes and keeps them registered with the vanilla {@link TextureManager}.
 * <p>
 * The 1.15 implementation registered textures with {@code func_229263_a_} (now
 * {@link TextureManager#register}) and looked them up with {@code func_229267_b_} (now
 * {@link TextureManager#getTexture}) using {@code NetworkPlayerInfo} (now {@link PlayerInfo}).
 * Texture downloads stay off the render thread: {@link SimpleTickingTexture#setBufferedImage(BufferedImage)}
 * only stores the image, the upload happens on the next client tick.
 */
public class ResourceManager implements IResourceManager {

	private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
			.setNameFormat("5zig Texture Downloader #%d").setDaemon(true).build());
	private static final String BASE_URL = "https://textures.5zigreborn.eu/profile/";
	private static final Gson GSON = new Gson();

	private final GameProfile playerProfile;
	private PlayerResource ownPlayerResource;
	private final Cache<UUID, PlayerResource> playerResources = CacheBuilder.newBuilder()
			.expireAfterAccess(3, TimeUnit.MINUTES).build();

	public ResourceManager(GameProfile playerProfile) {
		this.playerProfile = playerProfile;
	}

	private static TextureManager getTextureManager() {
		return Minecraft.getInstance().getTextureManager();
	}

	@Override
	public void loadPlayerTextures(final GameProfile gameProfile) {
		if (gameProfile == null || gameProfile.getId() == null) {
			return;
		}
		PlayerResource playerResource;
		if (isOwnProfile(gameProfile)) {
			if (ownPlayerResource != null) {
				playerResource = ownPlayerResource;
			} else {
				playerResource = ownPlayerResource = loadPlayerResource(gameProfile);
			}
		} else {
			playerResource = playerResources.getIfPresent(gameProfile.getId());
			if (playerResource != null) {
				MinecraftFactory.getClassProxyCallback().getLogger().debug("Loaded player resource textures from cache for player " + gameProfile.getName());
			} else {
				playerResources.put(gameProfile.getId(), loadPlayerResource(gameProfile));
			}
		}
	}

	private boolean isOwnProfile(GameProfile gameProfile) {
		return playerProfile != null && playerProfile.getName() != null && playerProfile.getName().equals(gameProfile.getName());
	}

	private PlayerResource loadPlayerResource(final GameProfile gameProfile) {
		final MinecraftProfileTexture minecraftProfileTexture = new MinecraftProfileTexture(BASE_URL +
				gameProfile.getId().toString(), new HashMap<String, String>());
		final PlayerResource playerResource = new PlayerResource();

		final ResourceLocation capeLocation = new ResourceLocation("the5zigmod", "capes/" + gameProfile.getId().toString().replace("-", ""));
		final SimpleTickingTexture capeTexture;
		AbstractTexture texture = getTextureManager().getTexture(capeLocation, null);
		if (texture instanceof SimpleTickingTexture) {
			capeTexture = (SimpleTickingTexture) texture;
			capeTexture.setBufferedImage(null);
		} else {
			capeTexture = new SimpleTickingTexture(capeLocation);
			getTextureManager().register(capeLocation, capeTexture);
		}

		EXECUTOR_SERVICE.execute(new Runnable() {
			@Override
			public void run() {
				MinecraftFactory.getClassProxyCallback().getLogger().debug("Loading player resource textures from {} for player {}",
						minecraftProfileTexture.getUrl(), gameProfile.getName());

				HttpURLConnection connection = null;
				BufferedReader reader = null;
				try {
					URLConnection urlConnection = openConnection(minecraftProfileTexture.getUrl());
					if (!(urlConnection instanceof HttpURLConnection)) {
						return;
					}
					connection = (HttpURLConnection) urlConnection;
					connection.addRequestProperty("User-Agent", "5zig/" + Version.VERSION);
					connection.setDoInput(true);
					connection.setDoOutput(false);
					connection.connect();
					if (connection.getResponseCode() != 200) {
						return;
					}
					reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
					String line = reader.readLine();
					if (line == null) {
						return;
					}

					TextureData data = GSON.fromJson(line, TextureData.class);
					if (data == null) {
						return;
					}
					if (data.d != null && !data.d.isEmpty()) {
						try {
							BufferedImage cape = ImageIO.read(new ByteArrayInputStream(Base64.decodeBase64(data.d)));
							if (cape == null) {
								return;
							}
							capeTexture.setBufferedImage(cape);

							playerResource.setCapeResource(new CapeResource(capeLocation, capeTexture));
						} catch (Exception e) {
							MinecraftFactory.getClassProxyCallback().getLogger().error("Could not parse cape for " + gameProfile.getId(), e);
						}
					}
					if (data.r != null) {
						RewardsCache.putReward(gameProfile.getId().toString(), data.r);
					}
				} catch (Exception e) {
					MinecraftFactory.getClassProxyCallback().getLogger().error("Couldn't download http texture", e);
				} finally {
					IOUtils.closeQuietly(reader);
					if (connection != null) {
						connection.disconnect();
					}
				}
			}
		});

		return playerResource;
	}

	private static URLConnection openConnection(String url) throws Exception {
		URL target = new URL(url);
		Proxy proxy = MinecraftFactory.getVars().getProxy();
		return proxy == null ? target.openConnection() : target.openConnection(proxy);
	}

	@Override
	public void updateOwnPlayerTextures() {
		ownPlayerResource = null;
		loadPlayerTextures(MinecraftFactory.getVars().getGameProfile());
	}

	@Override
	public Object getOwnCapeLocation() {
		return getCapeLocation(ownPlayerResource);
	}

	@Override
	public void cleanupTextures() {
		MinecraftFactory.getClassProxyCallback().getLogger().debug("Cleaning up player textures...");
		for (PlayerResource playerResource : playerResources.asMap().values()) {
			if (playerResource.getCapeResource() != null && playerResource.getCapeResource().getSimpleTexture() instanceof SimpleTickingTexture) {
				((SimpleTickingTexture) playerResource.getCapeResource().getSimpleTexture()).setBufferedImage(null);
			}
		}
		playerResources.invalidateAll();
		ownPlayerResource = null;
	}

	@Override
	public Object getCapeLocation(Object player) {
		GameProfile profile = getProfile(player);
		if (profile == null) {
			return null;
		}
		if (isOwnProfile(profile)) {
			return getCapeLocation(ownPlayerResource);
		}
		if (profile.getId() == null) {
			return null;
		}
		return getCapeLocation(playerResources.getIfPresent(profile.getId()));
	}

	/**
	 * 1.15 received a {@code NetworkPlayerInfo}; 1.20.1 callers may hand over the {@link PlayerInfo}, the
	 * {@link GameProfile} or the {@link AbstractClientPlayer} itself, so all three are accepted.
	 */
	private static GameProfile getProfile(Object player) {
		if (player instanceof NetworkPlayerInfo) {
			return ((NetworkPlayerInfo) player).getGameProfile();
		}
		if (player instanceof PlayerInfo) {
			return ((PlayerInfo) player).getProfile();
		}
		if (player instanceof GameProfile) {
			return (GameProfile) player;
		}
		if (player instanceof AbstractClientPlayer) {
			return ((AbstractClientPlayer) player).getGameProfile();
		}
		return null;
	}

	private Object getCapeLocation(PlayerResource playerResource) {
		if (playerResource != null && playerResource.getCapeResource() != null) {
			CapeResource capeResource = playerResource.getCapeResource();
			if (capeResource.getSimpleTexture() instanceof SimpleTickingTexture) {
				return ((SimpleTickingTexture) capeResource.getSimpleTexture()).getCurrentResource();
			}
		}
		return null;
	}

	public boolean renderInPersonMode(Object instance, Object itemStackObject, Object entityPlayerObject, Object cameraTransformTypeObject) {
		return false;
	}

	public boolean renderInInventory(Object instance, Object itemStackObject, int x, int y) {
		return false;
	}
}
