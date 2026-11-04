/*
 * Copyright (c) 2019-2020 5zig Reborn
 * Copyright (c) 2015-2019 5zig
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

import eu.the5zig.mod.util.Texts;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.util.Keybinds;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.gui.Gui;
import eu.the5zig.mod.gui.IOverlay;
import eu.the5zig.mod.gui.IWrappedGui;
import eu.the5zig.mod.gui.elements.*;
import eu.the5zig.mod.gui.ingame.IGui2ndChat;
import eu.the5zig.mod.gui.ingame.ItemStack;
import eu.the5zig.mod.gui.ingame.PotionEffectImpl;
import eu.the5zig.mod.gui.ingame.ScoreboardImpl;
import eu.the5zig.mod.gui.ingame.resource.IResourceManager;
import eu.the5zig.mod.util.*;
import eu.the5zig.mod.util.component.MessageComponent;
import eu.the5zig.mod.util.component.style.MessageAction;
import eu.the5zig.mod.util.component.style.MessageStyle;
import eu.the5zig.util.Callback;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import eu.the5zig.mod.util.GuiContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 1.16.2 (Mojang mappings, Fabric) implementation of {@link IVariables}.
 * <p>
 * Ported from the 1.15.1 implementation. The parts that no longer exist on 1.16.2 (global texture
 * binding, the LWJGL2 keyboard event queue, {@code ScaledResolution}, OptiFine style option objects)
 * are replaced by their modern equivalents; everything that cannot exist at all throws
 * {@link UnsupportedOperationException} with an explanation instead of pretending to work.
 */
public class Variables implements IVariables {

	/**
	 * Maximum size of a plugin channel payload accepted by a 1.16.2 server; larger packets are
	 * rejected by the vanilla encoder with a disconnect.
	 */
	private static final int MAX_PAYLOAD_SIZE = 32767;

	/** The vanilla HUD texture all hardcoded 5zig ingame blits use (food/saturation icons). */
	private static final ResourceLocation GUI_ICONS = new ResourceLocation("textures/gui/icons.png");

	private static final List<PotionEffectImpl> DUMMY_POTIONS = Collections.unmodifiableList(java.util.Arrays.asList(
			new PotionEffectImpl("effect.minecraft.jump_boost", 20, "0:01", 1, 8, true, true, 16646020),
			new PotionEffectImpl("effect.minecraft.speed", 20 * 50, "0:50", 1, 1, true, true, 8171462)));

	/**
	 * The second chat. Created lazily so that constructing {@code Variables} never touches the
	 * (possibly not yet existing) client instance: {@code MinecraftFactory} instantiates this class
	 * from a static initializer during mod construction.
	 */
	private IGui2ndChat gui2ndChat;

	private IResourceManager resourceManager;

	private ISystemPowerStatus batteryStatus;
	private boolean batteryStatusResolved;

	public Variables() {
		// Only handler installation here - no Minecraft state is read during construction.
		Keyboard.init(new Keyboard.KeyboardHandler() {
			@Override
			public boolean isKeyDown(int key) {
				Window window = getWindow();
				return window != null && GLFW.glfwGetKey(window.getWindow(), key) == GLFW.GLFW_PRESS;
			}

			@Override
			public void enableRepeatEvents(boolean repeat) {
				// 1.16.2 has no global GLFW repeat toggle any more; repeat behaviour is decided per
				// screen by the keyboard handler. Nothing to configure here.
			}
		});
		Mouse.init(new Mouse.MouseHandler() {
			@Override
			public boolean isButtonDown(int button) {
				Window window = getWindow();
				return window != null && GLFW.glfwGetMouseButton(window.getWindow(), button) == GLFW.GLFW_PRESS;
			}

			@Override
			public int getX() {
				return mouseX();
			}

			@Override
			public int getY() {
				return mouseY();
			}
		});
		Display.init(new Display.DisplayHandler() {
			@Override
			public boolean isActive() {
				Minecraft minecraft = Minecraft.getInstance();
				return minecraft != null && minecraft.isWindowActive();
			}
		});
	}

	// ------------------------------------------------------------------------------------------
	// Text
	// ------------------------------------------------------------------------------------------

	@Override
	public void drawString(String string, int x, int y, Object... format) {
		drawString(string, x, y, 0xffffff, format);
	}

	@Override
	public void drawString(String string, int x, int y) {
		drawString(string, x, y, 0xffffff);
	}

	@Override
	public void drawCenteredString(String string, int x, int y) {
		drawCenteredString(string, x, y, 0xffffff);
	}

	@Override
	public void drawCenteredString(String string, int x, int y, int color) {
		if (string == null) {
			return;
		}
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		graphics.drawCenteredString(getFontrenderer(), string, x, y, color);
	}

	@Override
	public void drawString(String string, int x, int y, int color, Object... format) {
		drawString(String.format(string, format), x, y, color);
	}

	@Override
	public void drawString(String string, int x, int y, int color) {
		drawString(string, x, y, color, true);
	}

	@Override
	public void drawString(String string, int x, int y, int color, boolean withShadow) {
		if (string == null) {
			return;
		}
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			// Drawing outside of a render pass (e.g. from a background thread) is skipped instead of
			// crashing: there is no GuiContext to draw into.
			return;
		}
		graphics.drawString(getFontrenderer(), string, x, y, color, withShadow);
	}

	@Override
	public List<String> splitStringToWidth(String string, int width) {
		Validate.isTrue(width > 0);
		if (string == null) {
			return Collections.emptyList();
		}
		if (string.isEmpty()) {
			return Collections.singletonList("");
		}
		List<FormattedCharSequence> parts = getFontrenderer().split(Texts.literal(string), width);
		List<String> result = new ArrayList<>(parts.size());
		for (FormattedCharSequence part : parts) {
			result.add(LegacyText.toLegacy(part));
		}
		return result;
	}

	@Override
	public int getStringWidth(String string) {
		return getFontrenderer().width(string);
	}

	@Override
	public String shortenToWidth(String string, int width) {
		if (StringUtils.isEmpty(string)) {
			return string;
		}
		Validate.isTrue(width > 0);

		boolean changed = false;
		if (getStringWidth(string) > width) {
			while (getStringWidth(string + "...") > width && !string.isEmpty()) {
				string = string.substring(0, string.length() - 1);
				changed = true;
			}
		}
		if (changed) {
			string += "...";
		}
		return string;
	}

	// ------------------------------------------------------------------------------------------
	// Widget factories
	// ------------------------------------------------------------------------------------------

	@Override
	public IButton createButton(int id, int x, int y, String label) {
		return new Button(id, x, y, label);
	}

	@Override
	public IButton createButton(int id, int x, int y, String label, boolean enabled) {
		return new Button(id, x, y, label, enabled);
	}

	@Override
	public IButton createButton(int id, int x, int y, int width, int height, String label) {
		return new Button(id, x, y, width, height, label);
	}

	@Override
	public IButton createButton(int id, int x, int y, int width, int height, String label, boolean enabled) {
		return new Button(id, x, y, width, height, label, enabled);
	}

	@Override
	public IButton createStringButton(int id, int x, int y, String label) {
		return new StringButton(id, x, y, label);
	}

	@Override
	public IButton createStringButton(int id, int x, int y, int width, int height, String label) {
		return new StringButton(id, x, y, width, height, label);
	}

	@Override
	public IButton createAudioButton(int id, int x, int y, AudioCallback callback) {
		return new AudioButton(id, x, y, callback);
	}

	@Override
	public IButton createIconButton(IResourceLocation resourceLocation, int u, int v, int id, int x, int y) {
		return new IconButton(resourceLocation, u, v, id, x, y);
	}

	@Override
	public ITextfield createTextfield(int id, int x, int y, int width, int height) {
		return new Textfield(id, x, y, width, height);
	}

	@Override
	public ITextfield createTextfield(int id, int x, int y, int width, int height, int maxStringLength) {
		return new Textfield(id, x, y, width, height, maxStringLength);
	}

	@Override
	public IPlaceholderTextfield createTextfield(String placeholder, int id, int x, int y, int width, int height) {
		return new PlaceholderTextfield(placeholder, id, x, y, width, height);
	}

	@Override
	public IPlaceholderTextfield createTextfield(String placeholder, int id, int x, int y, int width, int height, int maxStringLength) {
		return new PlaceholderTextfield(placeholder, id, x, y, width, height, maxStringLength);
	}

	@Override
	public IWrappedTextfield createWrappedTextfield(Object handle) {
		return new WrappedTextfield((EditBox) handle);
	}

	@Override
	public <E extends Row> IGuiList<E> createGuiList(Clickable<E> clickable, int width, int height, int top, int bottom, int left, int right,
			List<E> rows) {
		return new GuiList<E>(clickable, width, height, top, bottom, left, right, rows);
	}

	@Override
	public <E extends Row> IGuiList<E> createGuiListChat(int width, int height, int top, int bottom, int left, int right, int scrollx, List<E> rows,
			GuiListChatCallback callback) {
		return new GuiListChat<E>(width, height, top, bottom, left, right, scrollx, rows, callback);
	}

	@Override
	public IFileSelector createFileSelector(File currentDir, int width, int height, int left, int right, int top, int bottom, Callback<File> callback) {
		return new FileSelector(currentDir, width, height, left, right, top, bottom, callback);
	}

	@Override
	public IButton createSlider(int id, int x, int y, SliderCallback sliderCallback) {
		return new Slider(id, x, y, sliderCallback);
	}

	@Override
	public IButton createSlider(int id, int x, int y, int width, int height, SliderCallback sliderCallback) {
		return new Slider(id, x, y, width, height, sliderCallback);
	}

	@Override
	public IColorSelector createColorSelector(int id, int x, int y, int width, int height, String label, ColorSelectorCallback callback) {
		return new ColorSelector(id, x, y, width, height, label, callback);
	}

	@Override
	public IOverlay newOverlay() {
		return new Overlay();
	}

	@Override
	public void updateOverlayCount(int count) {
		Overlay.updateOverlayCount(count);
	}

	@Override
	public void renderOverlay() {
		Overlay.renderAll();
	}

	@Override
	public IWrappedGui createWrappedGui(Object lastScreen) {
		return new WrappedGui((Screen) lastScreen);
	}

	@Override
	public IKeybinding createKeybinding(String description, int keyCode, String category) {
		// Fabric only accepts new key mappings while Minecraft#options is still unset, so the timing
		// of the registration is owned by FiveZigKeybinds. The adapter it returns also keeps 1.15's
		// edge triggered callIsPressed() semantics (KeyMapping#isPressed), which the core relies on
		// for toggle bindings.
		return Keybinds.wrap(description, keyCode, category);
	}

	@Override
	public IGui2ndChat get2ndChat() {
		if (gui2ndChat == null) {
			gui2ndChat = new Gui2ndChat();
		}
		return gui2ndChat;
	}

	// ------------------------------------------------------------------------------------------
	// Chat
	// ------------------------------------------------------------------------------------------

	@Override
	public boolean isChatOpened() {
		return getMinecraft().screen instanceof ChatScreen;
	}

	@Override
	public String getChatBoxText() {
		if (!isChatOpened()) {
			return null;
		}
		EditBox chatField = getChatField();
		return chatField == null ? null : chatField.getValue();
	}

	@Override
	public void typeInChatGUI(String text) {
		if (!isChatOpened()) {
			displayScreen(new ChatScreen(text));
			return;
		}
		EditBox chatField = getChatField();
		if (chatField != null) {
			chatField.setValue(chatField.getValue() + text);
			chatField.moveCursorToEnd();
		}
	}

	/**
	 * @return the text field of the currently open chat screen, or {@code null} if it has not been
	 * initialized yet.
	 */
	private EditBox getChatField() {
		Screen screen = getMinecraft().screen;
		if (!(screen instanceof ChatScreen)) {
			return null;
		}
		// ChatScreen#input is protected; 1.16.2 focuses it on init, so the public focus API is used
		// instead of an access widener or reflection.
		return screen.getFocused() instanceof EditBox ? (EditBox) screen.getFocused() : null;
	}

	@Override
	public Object getChatComponentWithPrefix(String prefix, Object originalChatComponent) {
		MutableComponent result = Texts.empty();
		result.append(LegacyText.fromLegacy(prefix));
		if (originalChatComponent instanceof Component) {
			result.append((Component) originalChatComponent);
		}
		return result;
	}

	@Override
	public boolean isSignGUIOpened() {
		return getMinecraft().screen instanceof SignEditScreen;
	}

	@Override
	public void registerKeybindings(List<IKeybinding> keybindings) {
		boolean changed = false;
		for (IKeybinding keybinding : keybindings) {
			if (keybinding instanceof KeyMapping) {
				// Fallback for mappings that were not created through FiveZigKeybinds. Fabric's
				// registry appends them to Options#keyMappings (the array itself is final on 1.16.2,
				// so the old "rewrite the array" approach is impossible).
				Keybinds.registerFallback((KeyMapping) keybinding);
				changed = true;
			}
		}
		Minecraft minecraft = getMinecraft();
		if (minecraft != null && minecraft.options != null && (changed || !keybindings.isEmpty())) {
			// Applies the key the user bound in a previous session to the freshly registered mapping
			// (1.15 called GameSettings#loadOptions() for the same reason).
			minecraft.options.load();
		}
	}

	// ------------------------------------------------------------------------------------------
	// Sound
	// ------------------------------------------------------------------------------------------

	@Override
	public void playSound(String sound, float pitch) {
		playSound("minecraft", sound, pitch);
	}

	@Override
	public void playSound(String domain, String sound, float pitch) {
		try {
			SoundEvent event = new SoundEvent(new ResourceLocation(domain, sound));
			getMinecraft().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
		} catch (Exception e) {
			// A missing/unknown sound resource must never take the HUD or chat down.
			The5zigMod.logger.warn("Unable to play sound " + domain + ":" + sound, e);
		}
	}

	@Override
	public int getFontHeight() {
		return getFontrenderer().lineHeight;
	}

	// ------------------------------------------------------------------------------------------
	// Server
	// ------------------------------------------------------------------------------------------

	public ServerData getServerData() {
		return getMinecraft().getCurrentServer();
	}

	@Override
	public void resetServer() {
		// 1.16.2 keeps the server data on the active ClientPacketListener; getServer() always reads
		// the live value, so this implementation has no cached state that could go stale.
	}

	@Override
	public String getServer() {
		ServerData serverData = getServerData();
		return serverData == null ? null : serverData.ip;
	}

	@Override
	public List<NetworkPlayerInfo> getServerPlayers() {
		List<NetworkPlayerInfo> result = Lists.newArrayList();
		ClientPacketListener connection = getMinecraft().getConnection();
		if (connection == null) {
			return result;
		}
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			result.add(new WrappedNetworkPlayerInfo(info));
		}
		return result;
	}

	@Override
	public boolean isPlayerListShown() {
		return getGameSettings().keyPlayerList.isDown() && (!getMinecraft().isLocalServer() || getServerPlayers().size() > 1);
	}

	@Override
	public void setFOV(float fov) {
		// 1.16.2 stores the FOV as a plain field; the options screen clamps it to 30..110.
		getGameSettings().fov = fov;
	}

	@Override
	public float getFOV() {
		return (float) getGameSettings().fov;
	}

	@Override
	public void setSmoothCamera(boolean smoothCamera) {
		getGameSettings().smoothCamera = smoothCamera;
	}

	@Override
	public boolean isSmoothCamera() {
		return getGameSettings().smoothCamera;
	}

	@Override
	public String translate(String location, Object... values) {
		return Texts.translatable(location, values).getString();
	}

	@Override
	public void displayScreen(Gui gui) {
		if (gui == null) {
			displayScreen((Object) null);
		} else {
			displayScreen(gui.getHandle());
		}
	}

	@Override
	public void displayScreen(Object gui) {
		getMinecraft().setScreen((Screen) gui);
	}

	@Override
	public void joinServer(String host, int port) {
		Minecraft minecraft = getMinecraft();
		MinecraftFactory.getClassProxyCallback().resetServer();
		ServerData serverData = new ServerData(host, host + ":" + port, false);
		// 1.16.2's ConnectScreen connects from its constructor; there is no static entry point yet.
		new ConnectScreen(minecraft.screen, minecraft, host, port);
	}

	@Override
	public void joinServer(Object parentScreen, Object serverData) {
		if (!(serverData instanceof ServerData)) {
			return;
		}
		Minecraft minecraft = getMinecraft();
		ServerData data = (ServerData) serverData;
		MinecraftFactory.getClassProxyCallback().resetServer();
		new ConnectScreen((Screen) parentScreen, minecraft, data);
	}

	@Override
	public void disconnectFromWorld() {
		Minecraft minecraft = getMinecraft();
		boolean singleplayer = minecraft.hasSingleplayerServer();
		boolean realms = minecraft.isConnectedToRealms();
		if (minecraft.level != null) {
			minecraft.level.disconnect();
		}
		minecraft.clearLevel();
		if (singleplayer || realms) {
			minecraft.setScreen(new TitleScreen());
		} else {
			minecraft.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
		}
	}

	@Override
	public IServerPinger getServerPinger() {
		return new ServerPinger();
	}

	@Override
	public long getSystemTime() {
		return System.currentTimeMillis();
	}

	// ------------------------------------------------------------------------------------------
	// Client accessors
	// ------------------------------------------------------------------------------------------

	public Minecraft getMinecraft() {
		return Minecraft.getInstance();
	}

	public Font getFontrenderer() {
		return getMinecraft().font;
	}

	public Options getGameSettings() {
		return getMinecraft().options;
	}

	public LocalPlayer getPlayer() {
		return getMinecraft().player;
	}

	public ClientLevel getWorld() {
		return getMinecraft().level;
	}

	public Window getWindow() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft == null ? null : minecraft.getWindow();
	}

	/**
	 * @return the entity the camera is attached to (the player itself, or the entity being spectated).
	 */
	public Entity getSpectatingEntity() {
		return getMinecraft().getCameraEntity();
	}

	@Override
	public boolean isSpectatingSelf() {
		return getSpectatingEntity() instanceof Player;
	}

	@Override
	public PlayerGameMode getGameMode() {
		if (getMinecraft().gameMode == null) {
			return PlayerGameMode.SURVIVAL;
		}
		GameType gameType = getMinecraft().gameMode.getPlayerMode();
		PlayerGameMode[] values = PlayerGameMode.values();
		int id = gameType == null ? 0 : gameType.getId();
		return id >= 0 && id < values.length ? values[id] : PlayerGameMode.SURVIVAL;
	}

	@Override
	public String getOpenContainerTitle() {
		Screen screen = getMinecraft().screen;
		if (screen instanceof AbstractContainerScreen) {
			return ((AbstractContainerScreen<?>) screen).getTitle().getString();
		}
		return null;
	}

	@Override
	public void closeContainer() {
		LocalPlayer player = getPlayer();
		if (player != null) {
			player.closeContainer();
		}
	}

	@Override
	public String getSession() {
		return getMinecraft().getUser().getAccessToken();
	}

	@Override
	public String getUsername() {
		return getMinecraft().getUser().getName();
	}

	@Override
	public Proxy getProxy() {
		return getMinecraft().getProxy();
	}

	@Override
	public GameProfile getGameProfile() {
		return getMinecraft().getUser().getGameProfile();
	}

	@Override
	public String getFPS() {
		// 1.16.2 has no Minecraft#getFps(); the counter only exists as the leading field of the
		// debug overlay string ("120 fps T: ...").
		String fpsString = getMinecraft().fpsString;
		if (fpsString == null || fpsString.isEmpty()) {
			return "0";
		}
		int end = fpsString.indexOf(' ');
		return end < 0 ? fpsString : fpsString.substring(0, end);
	}

	@Override
	public boolean isPlayerNull() {
		return getPlayer() == null;
	}

	@Override
	public boolean isTerrainLoading() {
		return getMinecraft().screen instanceof ReceivingLevelScreen;
	}

	// ------------------------------------------------------------------------------------------
	// Player position / rotation
	// ------------------------------------------------------------------------------------------

	@Override
	public double getPlayerPosX() {
		Entity entity = getSpectatingEntity();
		return entity == null ? 0 : entity.xo;
	}

	@Override
	public double getPlayerPosY() {
		Entity entity = getSpectatingEntity();
		return entity == null ? 0 : entity.yo;
	}

	@Override
	public double getPlayerPosZ() {
		Entity entity = getSpectatingEntity();
		return entity == null ? 0 : entity.zo;
	}

	@Override
	public float getPlayerRotationYaw() {
		Entity entity = getSpectatingEntity();
		return entity == null ? 0 : entity.yRot;
	}

	@Override
	public float getPlayerRotationPitch() {
		Entity entity = getSpectatingEntity();
		return entity == null ? 0 : entity.xRot;
	}

	private BlockPos playerBlockPos() {
		return new BlockPos((int) Math.floor(getPlayerPosX()), (int) Math.floor(getPlayerPosY()), (int) Math.floor(getPlayerPosZ()));
	}

	@Override
	public int getPlayerChunkX() {
		return playerBlockPos().getX() >> 4;
	}

	@Override
	public int getPlayerChunkY() {
		return playerBlockPos().getY() >> 4;
	}

	@Override
	public int getPlayerChunkZ() {
		return playerBlockPos().getZ() >> 4;
	}

	@Override
	public int getPlayerChunkRelX() {
		return playerBlockPos().getX() & 15;
	}

	@Override
	public int getPlayerChunkRelY() {
		return playerBlockPos().getY() & 15;
	}

	@Override
	public int getPlayerChunkRelZ() {
		return playerBlockPos().getZ() & 15;
	}

	// ------------------------------------------------------------------------------------------
	// World / target
	// ------------------------------------------------------------------------------------------

	@Override
	public boolean hasTargetBlock() {
		return getMinecraft().hitResult != null && getMinecraft().hitResult.getType() == HitResult.Type.BLOCK;
	}

	@Override
	public int getTargetBlockX() {
		return getTargetBlockPos().getX();
	}

	@Override
	public int getTargetBlockY() {
		return getTargetBlockPos().getY();
	}

	@Override
	public int getTargetBlockZ() {
		return getTargetBlockPos().getZ();
	}

	private BlockPos getTargetBlockPos() {
		HitResult hitResult = getMinecraft().hitResult;
		if (hitResult instanceof BlockHitResult) {
			return ((BlockHitResult) hitResult).getBlockPos();
		}
		return playerBlockPos();
	}

	@Override
	public IResourceLocation getTargetBlockName() {
		if (!hasTargetBlock() || getWorld() == null) {
			return null;
		}
		BlockPos pos = ((BlockHitResult) getMinecraft().hitResult).getBlockPos();
		net.minecraft.resources.ResourceLocation key = Registry.BLOCK.getKey(getWorld().getBlockState(pos).getBlock());
		return new ResourceLocation(key.getNamespace(), key.getPath());
	}

	@Override
	public boolean isFancyGraphicsEnabled() {
		return getGameSettings().graphicsMode != GraphicsStatus.FAST;
	}

	@Override
	public String getBiome() {
		ClientLevel level = getWorld();
		if (level == null) {
			return null;
		}
		BlockPos pos = playerBlockPos();
		if (!level.isLoaded(pos)) {
			return null;
		}
		Biome biome = level.getBiome(pos);
		net.minecraft.resources.ResourceLocation location = level.registryAccess()
				.registryOrThrow(Registry.BIOME_REGISTRY).getKey(biome);
		if (location == null) {
			return null;
		}
		String translationKey = "biome." + location.getNamespace() + "." + location.getPath();
		String translated = Texts.translatable(translationKey).getString();
		return translated.equals(translationKey) ? location.getPath() : translated;
	}

	@Override
	public int getLightLevel() {
		ClientLevel level = getWorld();
		if (level == null) {
			return 0;
		}
		return level.getMaxLocalRawBrightness(playerBlockPos());
	}

	@Override
	public String getEntityCount() {
		if (getWorld() == null) {
			return "No world";
		}
		// "E: <rendered>/<loaded>, B: <culled>, SD: <simulation distance>"
		String statistics = getMinecraft().levelRenderer.getEntityStatistics();
		int start = statistics.indexOf("E: ");
		if (start < 0) {
			return "0/0";
		}
		start += 3;
		int end = statistics.indexOf(',', start);
		return end < 0 ? statistics.substring(start) : statistics.substring(start, end);
	}

	@Override
	public boolean isRidingEntity() {
		Entity entity = getSpectatingEntity();
		return entity != null && entity.isPassenger();
	}

	// ------------------------------------------------------------------------------------------
	// Player stats
	// ------------------------------------------------------------------------------------------

	/**
	 * @return the player that should be used for stat lookups: the spectated player if one is being
	 * spectated, otherwise the local player.
	 */
	private Player getStatPlayer() {
		Entity entity = getSpectatingEntity();
		if (entity instanceof Player) {
			return (Player) entity;
		}
		return getPlayer();
	}

	@Override
	public int getFoodLevel() {
		Player player = getStatPlayer();
		return player == null ? 0 : player.getFoodData().getFoodLevel();
	}

	@Override
	public float getSaturation() {
		Player player = getStatPlayer();
		return player == null ? 0 : player.getFoodData().getSaturationLevel();
	}

	@Override
	public float getHealth(Object entity) {
		if (!(entity instanceof LivingEntity)) {
			return -1;
		}
		return ((LivingEntity) entity).getHealth();
	}

	@Override
	public float getPlayerHealth() {
		LocalPlayer player = getPlayer();
		return player == null ? 0 : player.getHealth();
	}

	@Override
	public float getPlayerMaxHealth() {
		LocalPlayer player = getPlayer();
		return player == null ? 0 : player.getMaxHealth();
	}

	@Override
	public int getPlayerArmor() {
		LocalPlayer player = getPlayer();
		return player == null ? 0 : player.getArmorValue();
	}

	@Override
	public int getAir() {
		LocalPlayer player = getPlayer();
		return player == null ? 0 : player.getAirSupply();
	}

	@Override
	public boolean isPlayerInsideWater() {
		Entity entity = getSpectatingEntity();
		return entity != null && entity.isInWater();
	}

	@Override
	public float getResistanceFactor() {
		LocalPlayer player = getPlayer();
		if (player == null) {
			return 0;
		}
		float referenceDamage = 100;
		int i1 = 25 - player.getArmorValue();
		referenceDamage = referenceDamage * (float) i1 / 25.0F;

		MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		if (resistance != null) {
			int resistanceAmplifier = (resistance.getAmplifier() + 1) * 5;
			int i = 25 - resistanceAmplifier;
			referenceDamage = referenceDamage * (float) i / 25.0F;
		}

		if (referenceDamage <= 0.0F) {
			return 100;
		}
		ClientLevel level = getWorld();
		if (level != null) {
			int enchantmentModifierDamage = net.minecraft.world.item.enchantment.EnchantmentHelper
					.getDamageProtection(player.getArmorSlots(), net.minecraft.world.damagesource.DamageSource.GENERIC);
			if (enchantmentModifierDamage > 20) {
				enchantmentModifierDamage = 20;
			}
			if (enchantmentModifierDamage > 0) {
				int k = 25 - enchantmentModifierDamage;
				referenceDamage = referenceDamage * (float) k / 25.0F;
			}
		}
		return 100 - referenceDamage;
	}

	// ------------------------------------------------------------------------------------------
	// Potion effects
	// ------------------------------------------------------------------------------------------

	@Override
	public PotionEffectImpl getPotionForVignette() {
		Collection<MobEffectInstance> effects = getActivePlayerPotionEffects();
		for (MobEffectInstance effect : effects) {
			return wrapPotionEffect(effect);
		}
		return null;
	}

	@Override
	public List<eu.the5zig.mod.gui.ingame.PotionEffect> getActivePotionEffects() {
		Collection<MobEffectInstance> effects = getActivePlayerPotionEffects();
		List<eu.the5zig.mod.gui.ingame.PotionEffect> result = new ArrayList<>(effects.size());
		for (MobEffectInstance effect : effects) {
			result.add(wrapPotionEffect(effect));
		}
		Collections.sort(result);
		return result;
	}

	@Override
	public List<? extends eu.the5zig.mod.gui.ingame.PotionEffect> getDummyPotionEffects() {
		return DUMMY_POTIONS;
	}

	private PotionEffectImpl wrapPotionEffect(MobEffectInstance potionEffect) {
		MobEffect effect = potionEffect.getEffect();
		return new PotionEffectImpl(effect.getDescriptionId(), potionEffect.getDuration(),
				MobEffectUtil.formatDuration(potionEffect, 1.0F), potionEffect.getAmplifier() + 1,
				MobEffect.getId(effect), effect.isBeneficial(), potionEffect.isVisible(), effect.getColor());
	}

	@Override
	public int getPotionEffectIndicatorHeight() {
		LocalPlayer player = getPlayer();
		if (player == null) {
			return 0;
		}
		Screen screen = getMinecraft().screen;
		if (screen instanceof EffectRenderingInventoryScreen
				&& ((EffectRenderingInventoryScreen<?>) screen).doRenderEffects) {
			// The inventory is drawing the effect icons itself (1.16.2 has doRenderEffects public,
			// and the access widener keeps it that way), so the HUD indicator steps aside.
			return 0;
		}
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (effect.showIcon()) {
				// The vanilla 1.16.2 HUD draws one row of 24x24 icons at the top right corner.
				return 26;
			}
		}
		return 0;
	}

	@Override
	public boolean isHungerPotionActive() {
		LocalPlayer player = getPlayer();
		return player != null && player.hasEffect(MobEffects.HUNGER);
	}

	public Collection<MobEffectInstance> getActivePlayerPotionEffects() {
		Player player = getStatPlayer();
		return player == null ? Collections.emptyList() : player.getActiveEffects();
	}

	// ------------------------------------------------------------------------------------------
	// Items
	// ------------------------------------------------------------------------------------------

	@Override
	public ItemStack getItemInMainHand() {
		LocalPlayer player = getPlayer();
		if (player == null || player.getMainHandItem().isEmpty()) {
			return null;
		}
		return new WrappedItemStack(player.getMainHandItem());
	}

	@Override
	public ItemStack getItemInOffHand() {
		LocalPlayer player = getPlayer();
		if (player == null || player.getOffhandItem().isEmpty()) {
			return null;
		}
		return new WrappedItemStack(player.getOffhandItem());
	}

	@Override
	public ItemStack getItemInArmorSlot(int slot) {
		LocalPlayer player = getPlayer();
		if (player == null || slot < 0 || slot > 3) {
			return null;
		}
		net.minecraft.world.item.ItemStack stack = player.inventory.getArmor(slot);
		return stack.isEmpty() ? null : new WrappedItemStack(stack);
	}

	@Override
	public ItemStack getItemByName(String resourceName) {
		return getItemByName(resourceName, 1);
	}

	@Override
	public ItemStack getItemByName(String resourceName, int amount) {
		net.minecraft.world.item.Item item = Registry.ITEM.get(toResourceLocation(resourceName));
		return new WrappedItemStack(new net.minecraft.world.item.ItemStack(item, amount));
	}

	@Override
	public int getItemCount(String key) {
		LocalPlayer player = getPlayer();
		if (player == null) {
			return 0;
		}
		int count = 0;
		for (net.minecraft.world.item.ItemStack itemStack : player.inventory.items) {
			if (itemStack.isEmpty()) {
				continue;
			}
			if (key.equals(Registry.ITEM.getKey(itemStack.getItem()).toString())) {
				count += itemStack.getCount();
			}
		}
		return count;
	}

	@Override
	public int getSelectedHotbarSlot() {
		LocalPlayer player = getPlayer();
		return player == null ? 0 : player.inventory.selected;
	}

	@Override
	public void setSelectedHotbarSlot(int slot) {
		LocalPlayer player = getPlayer();
		if (player == null) {
			return;
		}
		player.inventory.selected = slot;
		ClientPacketListener connection = getMinecraft().getConnection();
		if (connection != null) {
			connection.send(new ServerboundSetCarriedItemPacket(slot));
		}
	}

	@Override
	public void onRightClickMouse() {
		LocalPlayer player = getPlayer();
		Minecraft minecraft = getMinecraft();
		if (player == null || minecraft.gameMode == null || minecraft.level == null || minecraft.gameMode.isDestroying()) {
			return;
		}
		InteractionHand hand = InteractionHand.MAIN_HAND;
		HitResult hitResult = minecraft.hitResult;
		if (hitResult instanceof BlockHitResult) {
			InteractionResult result = minecraft.gameMode.useItemOn(player, minecraft.level, hand, (BlockHitResult) hitResult);
			if (result.consumesAction()) {
				if (result.shouldSwing()) {
					player.swing(hand);
				}
				return;
			}
			if (result == InteractionResult.FAIL) {
				return;
			}
		} else if (hitResult instanceof EntityHitResult) {
			EntityHitResult entityHit = (EntityHitResult) hitResult;
			InteractionResult result = minecraft.gameMode.interactAt(player, entityHit.getEntity(), entityHit, hand);
			if (!result.consumesAction()) {
				result = minecraft.gameMode.interact(player, entityHit.getEntity(), hand);
			}
			if (result.consumesAction()) {
				if (result.shouldSwing()) {
					player.swing(hand);
				}
				return;
			}
		}
		if (!player.getMainHandItem().isEmpty()) {
			InteractionResult result = minecraft.gameMode.useItem(player, minecraft.level, hand);
			if (result.consumesAction() && result.shouldSwing()) {
				player.swing(hand);
			}
		}
	}

	// ------------------------------------------------------------------------------------------
	// Resolution
	// ------------------------------------------------------------------------------------------

	@Override
	public void updateScaledResolution() {
		// 1.16.2 has no ScaledResolution object any more: the Window always carries the current
		// GUI scale, so the values below are read live and there is no cached state to refresh.
	}

	@Override
	public int getWidth() {
		Window window = getWindow();
		return window == null ? 0 : window.getWidth();
	}

	@Override
	public int getHeight() {
		Window window = getWindow();
		return window == null ? 0 : window.getHeight();
	}

	@Override
	public int getScaledWidth() {
		Window window = getWindow();
		return window == null ? 0 : window.getGuiScaledWidth();
	}

	@Override
	public int getScaledHeight() {
		Window window = getWindow();
		return window == null ? 0 : window.getGuiScaledHeight();
	}

	@Override
	public int getScaleFactor() {
		Window window = getWindow();
		return window == null ? 0 : (int) window.getGuiScale();
	}

	// ------------------------------------------------------------------------------------------
	// HUD
	// ------------------------------------------------------------------------------------------

	@Override
	public boolean showDebugScreen() {
		return getGameSettings().renderDebug;
	}

	@Override
	public boolean isPlayerSpectating() {
		Minecraft minecraft = getMinecraft();
		if (minecraft.player == null) {
			return true;
		}
		return minecraft.player.isSpectator() || minecraft.getCameraEntity() != minecraft.player;
	}

	@Override
	public boolean shouldDrawHUD() {
		return !getGameSettings().hideGui;
	}

	@Override
	public String[] getHotbarKeys() {
		String[] result = new String[9];
		KeyMapping[] hotbarBindings = getGameSettings().keyHotbarSlots;
		for (int i = 0; i < Math.min(result.length, hotbarBindings.length); i++) {
			result[i] = hotbarBindings[i].getTranslatedKeyMessage().getString();
		}
		return result;
	}

	@Override
	public String getKeyDisplayStringShort(int key) {
		if (key < 0) {
			// 5zig encodes mouse buttons as -100..-97.
			return "M" + (key + 101);
		}
		if (key < 256) {
			String name = GLFW.glfwGetKeyName(key, -1);
			if (name == null) {
				name = GLFW.glfwGetKeyName(-1, key);
			}
			if (name != null) {
				return name.toUpperCase(Locale.ROOT);
			}
			return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
		}
		return String.valueOf((char) (key - 256)).toUpperCase(Locale.ROOT);
	}

	@Override
	public void drawIngameTexturedModalRect(int x, int y, int u, int v, int width, int height) {
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		// The only callers draw hunger/saturation icons, which live in the vanilla icons atlas.
		graphics.blit(GUI_ICONS, x, y, u, v, width, height);
	}

	// ------------------------------------------------------------------------------------------
	// Messages / networking
	// ------------------------------------------------------------------------------------------

	@Override
	public void messagePlayer(String message) {
		Component component = LegacyText.fromLegacy(message);
		ClassProxyCallback callback = MinecraftFactory.getClassProxyCallback();
		if (callback != null && callback.shouldCancelChatMessage(component.getString(), component)) {
			return;
		}
		Minecraft minecraft = getMinecraft();
		if (minecraft == null || minecraft.gui == null) {
			return;
		}
		minecraft.gui.getChat().addMessage(component);
	}

	@Override
	public void sendMessage(String message) {
		LocalPlayer player = getPlayer();
		if (player != null) {
			// 1.16.2 has no client-side signing: Player#chat(String) builds the ServerboundChatPacket
			// directly and hands it to the connection.
			player.chat(message);
		}
	}

	@Override
	public boolean hasNetworkManager() {
		return getMinecraft().getConnection() != null;
	}

	@Override
	public void sendCustomPayload(String channel, ByteBuf payload) {
		ClientPacketListener connection = getMinecraft().getConnection();
		if (connection == null || payload == null) {
			return;
		}
		if (payload.readableBytes() > MAX_PAYLOAD_SIZE) {
			The5zigMod.logger.warn("Refusing to send {} bytes on plugin channel {}: 1.16.2 servers reject payloads larger than {} bytes",
					payload.readableBytes(), channel, MAX_PAYLOAD_SIZE);
			return;
		}
		connection.send(new ServerboundCustomPayloadPacket(new ResourceLocation(channel), new FriendlyByteBuf(payload)));
	}

	@Override
	public boolean isLocalWorld() {
		return getMinecraft().isLocalServer();
	}

	// ------------------------------------------------------------------------------------------
	// Screens
	// ------------------------------------------------------------------------------------------

	@Override
	public Gui getCurrentScreen() {
		Screen screen = getMinecraft().screen;
		if (!(screen instanceof GuiHandle)) {
			return null;
		}
		return ((GuiHandle) screen).getChild();
	}

	@Override
	public Object getMinecraftScreen() {
		return getMinecraft().screen;
	}

	// ------------------------------------------------------------------------------------------
	// Resources / textures
	// ------------------------------------------------------------------------------------------

	@Override
	public Object loadDynamicImage(String name, BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
			DynamicTexture texture = new DynamicTexture(nativeImage);
			// TextureManager#register builds "dynamic/<name>_<n>"; sanitize so that a name coming
			// from a chat plugin cannot blow up ResourceLocation's character validation.
			String safeName = name == null ? "image" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
			return getTextureManager().register(safeName, texture);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public IResourceLocation createResourceLocation(String resourcePath) {
		return new ResourceLocation(resourcePath);
	}

	@Override
	public IResourceLocation createResourceLocation(String resourceDomain, String resourcePath) {
		return new ResourceLocation(resourceDomain, resourcePath);
	}

	@Override
	public void bindTexture(Object resourceLocation) {
		net.minecraft.resources.ResourceLocation location = toTextureLocation(resourceLocation);
		if (location == null) {
			return;
		}
		BoundTexture.bind(location);
	}

	@Override
	public void deleteTexture(Object resourceLocation) {
		if (resourceLocation instanceof net.minecraft.resources.ResourceLocation) {
			getTextureManager().release((net.minecraft.resources.ResourceLocation) resourceLocation);
		}
	}

	@Override
	public Object createDynamicImage(Object resourceLocation, int width, int height) {
		DynamicTexture texture = new DynamicTexture(width, height, false);
		getTextureManager().register((net.minecraft.resources.ResourceLocation) resourceLocation, texture);
		return texture;
	}

	@Override
	public Object getTexture(Object resourceLocation) {
		return getTextureManager().getTexture((net.minecraft.resources.ResourceLocation) resourceLocation);
	}

	@Override
	public void fillDynamicImage(Object dynamicImage, BufferedImage image) {
		if (!(dynamicImage instanceof DynamicTexture)) {
			return;
		}
		DynamicTexture texture = (DynamicTexture) dynamicImage;
		NativeImage pixels = texture.getPixels();
		if (pixels == null) {
			return;
		}
		int width = Math.min(image.getWidth(), pixels.getWidth());
		int height = Math.min(image.getHeight(), pixels.getHeight());
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = image.getRGB(x, y);
				// BufferedImage is ARGB, NativeImage wants ABGR.
				int abgr = (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
				pixels.setPixelRGBA(x, y, abgr);
			}
		}
		texture.upload();
	}

	@Override
	public void renderPotionIcon(int index) {
		MobEffect effect = MobEffect.byId(index);
		if (effect == null) {
			return;
		}
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		TextureAtlasSprite sprite = getMinecraft().getMobEffectTextures().get(effect);
		graphics.blit(0, 0, 0, 18, 18, sprite);
	}

	@Override
	public void renderTextureOverlay(int x1, int x2, int y1, int y2) {
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		RenderSystem.color4f(0.25F, 0.25F, 0.25F, 1.0F);
		graphics.blit(Screen.BACKGROUND_LOCATION, x1, y1, (float) x1, (float) y1, x2, y2, 32, 32);
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
	}

	public TextureManager getTextureManager() {
		return getMinecraft().getTextureManager();
	}

	@Override
	public void setIngameFocus() {
		// setScreen(null) also re-grabs the mouse and resumes the sound engine.
		getMinecraft().setScreen(null);
	}

	// ------------------------------------------------------------------------------------------
	// World ray trace / scoreboard
	// ------------------------------------------------------------------------------------------

	@Override
	public MouseOverObject calculateMouseOverDistance(double maxDistance) {
		Entity entity = getSpectatingEntity();
		if (entity == null || getWorld() == null) {
			return null;
		}
		Vec3 eyes = entity.getEyePosition(1.0F);
		HitResult blockHit = entity.pick(maxDistance, 1.0F, false);
		double blockDistance = blockHit.getType() == HitResult.Type.MISS ? maxDistance : blockHit.getLocation().distanceTo(eyes);

		Vec3 view = entity.getViewVector(1.0F);
		Vec3 end = eyes.add(view.scale(maxDistance));
		AABB searchBox = entity.getBoundingBox().expandTowards(view.scale(maxDistance)).inflate(1.0D);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(entity, eyes, end, searchBox,
				candidate -> !candidate.isSpectator() && candidate.isPickable(), blockDistance * blockDistance);
		if (entityHit != null) {
			return new MouseOverObject(ObjectType.ENTITY, entityHit.getEntity(), Math.sqrt(entityHit.getLocation().distanceToSqr(eyes)));
		}
		if (blockHit.getType() == HitResult.Type.BLOCK) {
			return new MouseOverObject(ObjectType.BLOCK, blockHit, blockDistance);
		}
		return new MouseOverObject(ObjectType.MISS, null, maxDistance);
	}

	@Override
	public ScoreboardImpl getScoreboard() {
		ClientLevel level = getWorld();
		if (level == null) {
			return null;
		}
		Scoreboard scoreboard = level.getScoreboard();
		if (scoreboard == null) {
			return null;
		}
		Objective objective = scoreboard.getDisplayObjective(1);
		if (objective == null) {
			return null;
		}
		String displayName = objective.getDisplayName().getString();
		Collection<Score> scores = scoreboard.getPlayerScores(objective);
		HashMap<String, Integer> lines = Maps.newHashMap();
		for (Score score : scores) {
			lines.put(score.getOwner(), score.getScore());
		}
		return new ScoreboardImpl(displayName, lines);
	}

	@Override
	public IResourceManager getResourceManager() {
		if (resourceManager == null) {
			try {
				resourceManager = new ResourceManager(getGameProfile());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return resourceManager;
	}

	@Override
	public ISystemPowerStatus getBatteryStatus() {
		if (!batteryStatusResolved) {
			batteryStatusResolved = true;
			batteryStatus = SystemPowerStatus.get();
		}
		return batteryStatus;
	}

	@Override
	public InputStream getMinecraftIcon() throws Exception {
		// 1.16.2 does not ship the window icons as a resource any more (they are compiled into the
		// client), so the vanilla pack icon is used, falling back to the mod's own icon.
		InputStream icon = Variables.class.getResourceAsStream("/pack.png");
		if (icon == null) {
			icon = Variables.class.getResourceAsStream("/images/5zig-icon.png");
		}
		if (icon == null) {
			throw new FileNotFoundException("No Minecraft icon available in the client classpath");
		}
		return icon;
	}

	@Override
	public boolean isMainThread() {
		return getMinecraft().isSameThread();
	}

	@Override
	public File getMinecraftDataDirectory() {
		return getMinecraft().gameDirectory;
	}

	@Override
	public void dispatchKeypresses() {
		// 1.15 drained the LWJGL2 keyboard queue here and fired a KeyPressEvent per press. 1.16.2
		// has no queue, so MixinKeyboardHandler records the presses and this per-tick entry point
		// delivers them (and runs the state-based key handling, i.e. the menu key).
		KeyDispatch.dispatch();
	}

	@Override
	public void shutdown() {
		getMinecraft().stop();
	}

	@Override
	public void setSession(String name, String uuid, String token, String userType) {
		User.Type type = User.Type.MOJANG;
		if (userType != null) {
			try {
				type = User.Type.valueOf(userType.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
				// Unknown type (e.g. "msa" written by a future version): keep the Mojang default.
			}
		}
		User user = new User(name, uuid, token, type.name().toLowerCase(Locale.ROOT));
		// Minecraft#user is private and has no setter. The field is located by type so that the
		// lookup also works in a production environment where members are obfuscated.
		for (Field field : Minecraft.class.getDeclaredFields()) {
			if (field.getType() == User.class) {
				try {
					field.setAccessible(true);
					field.set(Minecraft.getInstance(), user);
					return;
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException("Unable to replace the client session", e);
				}
			}
		}
		throw new UnsupportedOperationException("Minecraft 1.16.2 exposes no user field that could be replaced");
	}

	@Override
	public void sendChatComponent(MessageComponent component, boolean secondChat) {
		Component result = toComponent(component);
		if (secondChat) {
			get2ndChat().printChatMessage(result);
			return;
		}
		Minecraft minecraft = getMinecraft();
		if (minecraft != null && minecraft.gui != null) {
			minecraft.gui.getChat().addMessage(result);
		}
	}

	// ------------------------------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------------------------------

	private Component toComponent(MessageComponent component) {
		if (component == null) {
			return Texts.empty();
		}
		MutableComponent result = Texts.literal(component.getText() == null ? "" : component.getText());
		MessageStyle messageStyle = component.getStyle();
		if (messageStyle != null) {
			Style style = Style.EMPTY;
			MessageAction click = messageStyle.getOnClick();
			if (click != null) {
				ClickEvent clickEvent = toClickEvent(click);
				if (clickEvent != null) {
					style = style.withClickEvent(clickEvent);
				}
			}
			MessageAction hover = messageStyle.getOnHover();
			if (hover != null && hover.getComponent() != null) {
				style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, toComponent(hover.getComponent())));
			}
			result.withStyle(style);
		}
		for (MessageComponent sibling : component.getSiblings()) {
			result.append(toComponent(sibling));
		}
		return result;
	}

	private static ClickEvent toClickEvent(MessageAction action) {
		if (action.getString() == null || action.getAction() == null) {
			return null;
		}
		switch (action.getAction()) {
			case OPEN_URL:
				return new ClickEvent(ClickEvent.Action.OPEN_URL, action.getString());
			case OPEN_FILE:
				return new ClickEvent(ClickEvent.Action.OPEN_FILE, action.getString());
			case RUN_COMMAND:
				return new ClickEvent(ClickEvent.Action.RUN_COMMAND, action.getString());
			case SUGGEST_COMMAND:
				return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, action.getString());
			default:
				return null;
		}
	}

	private static net.minecraft.resources.ResourceLocation toResourceLocation(String resourceName) {
		int separator = resourceName.indexOf(':');
		if (separator < 0) {
			return new ResourceLocation("minecraft", resourceName);
		}
		return new ResourceLocation(resourceName.substring(0, separator), resourceName.substring(separator + 1));
	}

	private static net.minecraft.resources.ResourceLocation toTextureLocation(Object resourceLocation) {
		if (resourceLocation instanceof net.minecraft.resources.ResourceLocation) {
			return (net.minecraft.resources.ResourceLocation) resourceLocation;
		}
		if (resourceLocation instanceof IResourceLocation) {
			IResourceLocation location = (IResourceLocation) resourceLocation;
			return new ResourceLocation(location.callGetResourceDomain(), location.callGetResourcePath());
		}
		return null;
	}

	private int mouseX() {
		Minecraft minecraft = getMinecraft();
		Window window = getWindow();
		if (minecraft == null || window == null || window.getScreenWidth() == 0) {
			return 0;
		}
		return (int) (minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
	}

	private int mouseY() {
		Minecraft minecraft = getMinecraft();
		Window window = getWindow();
		if (minecraft == null || window == null || window.getScreenHeight() == 0) {
			return 0;
		}
		// 5zig expects a bottom-left origin like LWJGL2's Mouse.getY().
		return (int) ((window.getScreenHeight() - minecraft.mouseHandler.ypos()) * window.getGuiScaledHeight() / window.getScreenHeight());
	}

}
