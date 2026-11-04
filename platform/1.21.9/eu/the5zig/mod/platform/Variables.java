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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ARGB;
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
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
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
import java.util.UUID;
import java.util.Optional;

/**
 * 1.20.1 (Mojang mappings, Fabric) implementation of {@link IVariables}.
 * <p>
 * Ported from the 1.15.1 implementation. The parts that no longer exist on 1.20.1 (global texture
 * binding, the LWJGL2 keyboard event queue, {@code ScaledResolution}, OptiFine style option objects)
 * are replaced by their modern equivalents; everything that cannot exist at all throws
 * {@link UnsupportedOperationException} with an explanation instead of pretending to work.
 */
public class Variables implements IVariables {

	/**
	 * Maximum size of a plugin channel payload accepted by a 1.20.1 server; larger packets are
	 * rejected by the vanilla encoder with a disconnect.
	 */
	private static final int MAX_PAYLOAD_SIZE = 32767;

	/** The vanilla HUD texture all hardcoded 5zig ingame blits use (food/saturation icons). */
	private static final ResourceLocation GUI_ICONS = new ResourceLocation("textures/gui/icons.png");

	/** Set once when a legacy plugin-channel message had to be dropped; see {@link #sendCustomPayload}. */
	private boolean customPayloadWarningLogged;

	/** Slot order of the old {@code Inventory#getArmor(int)}: feet, legs, chest, head. */
	private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
			net.minecraft.world.entity.EquipmentSlot.FEET,
			net.minecraft.world.entity.EquipmentSlot.LEGS,
			net.minecraft.world.entity.EquipmentSlot.CHEST,
			net.minecraft.world.entity.EquipmentSlot.HEAD,
	};

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
				return window != null && GLFW.glfwGetKey(window.handle(), key) == GLFW.GLFW_PRESS;
			}

			@Override
			public void enableRepeatEvents(boolean repeat) {
				// 1.20.1 has no global GLFW repeat toggle any more; repeat behaviour is decided per
				// screen by the keyboard handler. Nothing to configure here.
			}
		});
		Mouse.init(new Mouse.MouseHandler() {
			@Override
			public boolean isButtonDown(int button) {
				Window window = getWindow();
				return window != null && GLFW.glfwGetMouseButton(window.handle(), button) == GLFW.GLFW_PRESS;
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
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		graphics.drawCenteredString(getFontrenderer(), string, x, y, forceOpaque(color));
	}

	@Override
	public void drawString(String string, int x, int y, int color, Object... format) {
		drawString(String.format(string, format), x, y, color);
	}

	/**
	 * 1.21.9+ treats text colours as strict ARGB, so a legacy {@code 0xRRGGBB} value (alpha 0)
	 * renders nothing. The core passes colours without an alpha byte, so restore the missing alpha.
	 */
	private static int forceOpaque(int color) {
		return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
	}

	public void drawString(String string, int x, int y, int color) {
		drawString(string, x, y, color, true);
	}

	@Override
	public void drawString(String string, int x, int y, int color, boolean withShadow) {
		if (string == null) {
			return;
		}
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			// Drawing outside of a render pass (e.g. from a background thread) is skipped instead of
			// crashing: there is no GuiGraphics to draw into.
			return;
		}
		graphics.drawString(getFontrenderer(), string, x, y, forceOpaque(color), withShadow);
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
		List<FormattedCharSequence> parts = getFontrenderer().split(Component.literal(string), width);
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
			displayScreen(new ChatScreen(text, false));
			return;
		}
		EditBox chatField = getChatField();
		if (chatField != null) {
			chatField.setValue(chatField.getValue() + text);
			chatField.moveCursorToEnd(true);
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
		// ChatScreen#input is protected; 1.20.1 focuses it on init, so the public focus API is used
		// instead of an access widener or reflection.
		return screen.getFocused() instanceof EditBox ? (EditBox) screen.getFocused() : null;
	}

	@Override
	public Object getChatComponentWithPrefix(String prefix, Object originalChatComponent) {
		MutableComponent result = Component.empty();
		result.append(LegacyText.fromLegacy(prefix));
		if (originalChatComponent instanceof Component) {
			result.append((Component) originalChatComponent);
		}
		return result;
	}

	@Override
	public boolean isSignGUIOpened() {
		return getMinecraft().screen instanceof AbstractSignEditScreen;
	}

	@Override
	public void registerKeybindings(List<IKeybinding> keybindings) {
		boolean changed = false;
		for (IKeybinding keybinding : keybindings) {
			if (keybinding instanceof KeyMapping) {
				// Fallback for mappings that were not created through FiveZigKeybinds. Fabric's
				// registry appends them to Options#keyMappings (the array itself is final on 1.20.1,
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
			SoundEvent event = SoundEvent.createVariableRangeEvent(new ResourceLocation(domain, sound).toResourceLocation());
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
		// 1.20.1 keeps the server data on the active ClientPacketListener; getServer() always reads
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
		// OptionInstance clamps to the vanilla slider range (30..110 on 1.20.1).
		getGameSettings().fov().set((int) fov);
	}

	@Override
	public float getFOV() {
		return getGameSettings().fov().get();
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
		return Component.translatable(location, values).getString();
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
		ServerData serverData = new ServerData(host, host + ":" + port, ServerData.Type.OTHER);
		ConnectScreen.startConnecting(minecraft.screen, minecraft, new ServerAddress(host, port), serverData, false, null);
	}

	@Override
	public void joinServer(Object parentScreen, Object serverData) {
		if (!(serverData instanceof ServerData)) {
			return;
		}
		Minecraft minecraft = getMinecraft();
		ServerData data = (ServerData) serverData;
		MinecraftFactory.getClassProxyCallback().resetServer();
		ConnectScreen.startConnecting((Screen) parentScreen, minecraft, ServerAddress.parseString(data.ip), data, false, null);
	}

	@Override
	public void disconnectFromWorld() {
		Minecraft minecraft = getMinecraft();
		boolean singleplayer = minecraft.hasSingleplayerServer();
		// isConnectedToRealms() was removed from Minecraft in 1.21.9; Realms disconnects now go
		// through the same path as any other server.
		boolean realms = false;
		if (minecraft.level != null) {
			minecraft.level.disconnect(net.minecraft.network.chat.Component.empty());
		}
		// Minecraft#clearLevel no longer exists; the connection owns the level teardown now
		// (ClientPacketListener#clearLevel), so dropping the screen is all that is left here.
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
		// User no longer exposes a GameProfile (the launcher owns the session); rebuild the
		// profile from its two remaining fields.
		User user = getMinecraft().getUser();
		return new GameProfile(user.getProfileId(), user.getName());
	}

	@Override
	public String getFPS() {
		return Integer.toString(getMinecraft().getFps());
	}

	@Override
	public boolean isPlayerNull() {
		return getPlayer() == null;
	}

	@Override
	public boolean isTerrainLoading() {
		// 1.21.9 folded ReceivingLevelScreen into LevelLoadingScreen (used for both the initial
		// join and dimension changes).
		return getMinecraft().screen instanceof LevelLoadingScreen;
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
		return entity == null ? 0 : entity.getYRot();
	}

	@Override
	public float getPlayerRotationPitch() {
		Entity entity = getSpectatingEntity();
		return entity == null ? 0 : entity.getXRot();
	}

	private BlockPos playerBlockPos() {
		return BlockPos.containing(getPlayerPosX(), getPlayerPosY(), getPlayerPosZ());
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
		net.minecraft.resources.ResourceLocation key = BuiltInRegistries.BLOCK.getKey(getWorld().getBlockState(pos).getBlock());
		return new ResourceLocation(key.getNamespace(), key.getPath());
	}

	@Override
	public boolean isFancyGraphicsEnabled() {
		// 1.21.11 replaced the FAST/FANCY/FABULOUS GraphicsStatus with a GraphicsPreset option that
		// also has a CUSTOM value once individual video settings are tweaked. 1.21.9 still has the
		// three-valued enum.
		GraphicsStatus status = getGameSettings().graphicsMode().get();
		return status == GraphicsStatus.FANCY || status == GraphicsStatus.FABULOUS;
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
		Holder<Biome> biome = level.getBiome(pos);
		Optional<ResourceKey<Biome>> key = biome.unwrapKey();
		if (!key.isPresent()) {
			return null;
		}
		net.minecraft.resources.ResourceLocation location = key.get().location();
		String translationKey = "biome." + location.getNamespace() + "." + location.getPath();
		String translated = Component.translatable(translationKey).getString();
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

		MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
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
			// 1.21.11's EnchantmentHelper#getDamageProtection needs a ServerLevel and the full
			// damage pipeline; the client cannot evaluate it. Protection is therefore not folded
			// into the displayed resistance factor.
			int enchantmentModifierDamage = 0;
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
		// MobEffectInstance#getEffect returns a Holder since 1.21.
		MobEffect effect = potionEffect.getEffect().value();
		return new PotionEffectImpl(effect.getDescriptionId(), potionEffect.getDuration(),
				MobEffectUtil.formatDuration(potionEffect, 1.0F, 20.0F).getString(), potionEffect.getAmplifier() + 1,
				BuiltInRegistries.MOB_EFFECT.getId(effect), effect.isBeneficial(), potionEffect.isVisible(), effect.getColor());
	}

	@Override
	public int getPotionEffectIndicatorHeight() {
		LocalPlayer player = getPlayer();
		if (player == null) {
			return 0;
		}
		Screen screen = getMinecraft().screen;
		if (screen != null && screen.showsActiveEffects()) {
			// The inventory renders the active effects next to itself; the HUD must not double up.
			return 0;
		}
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (effect.showIcon()) {
				// The vanilla 1.20.1 HUD draws one row of 24x24 icons at the top right corner.
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
		// 1.15's Inventory#getArmor(0..3) was feet/legs/chest/head; Inventory no longer exposes the
		// armour list, so go through the equipment slots explicitly.
		net.minecraft.world.item.ItemStack stack = player.getItemBySlot(ARMOR_SLOTS[slot]);
		return stack.isEmpty() ? null : new WrappedItemStack(stack);
	}

	@Override
	public ItemStack getItemByName(String resourceName) {
		return getItemByName(resourceName, 1);
	}

	@Override
	public ItemStack getItemByName(String resourceName, int amount) {
		// Registry#get returns an Optional<Holder.Reference<T>> since 1.21.
		net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(toResourceLocation(resourceName))
				.map(net.minecraft.core.Holder::value).orElse(null);
		if (item == null) {
			return null;
		}
		return new WrappedItemStack(new net.minecraft.world.item.ItemStack(item, amount));
	}

	@Override
	public int getItemCount(String key) {
		LocalPlayer player = getPlayer();
		if (player == null) {
			return 0;
		}
		int count = 0;
		for (net.minecraft.world.item.ItemStack itemStack : player.getInventory().getNonEquipmentItems()) {
			if (itemStack.isEmpty()) {
				continue;
			}
			if (key.equals(BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString())) {
				count += itemStack.getCount();
			}
		}
		return count;
	}

	@Override
	public int getSelectedHotbarSlot() {
		LocalPlayer player = getPlayer();
		return player == null ? 0 : player.getInventory().getSelectedSlot();
	}

	@Override
	public void setSelectedHotbarSlot(int slot) {
		LocalPlayer player = getPlayer();
		if (player == null) {
			return;
		}
		player.getInventory().setSelectedSlot(slot);
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
			InteractionResult result = minecraft.gameMode.useItemOn(player, hand, (BlockHitResult) hitResult);
			if (result.consumesAction()) {
				if (shouldSwing(result)) {
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
				if (shouldSwing(result)) {
					player.swing(hand);
				}
				return;
			}
		}
		if (!player.getMainHandItem().isEmpty()) {
			InteractionResult result = minecraft.gameMode.useItem(player, hand);
			if (result.consumesAction() && shouldSwing(result)) {
				player.swing(hand);
			}
		}
	}

	/**
	 * 1.21.11 replaced {@code InteractionResult#shouldSwing()} with {@code Success#swingSource()}.
	 */
	private static boolean shouldSwing(InteractionResult result) {
		return result instanceof InteractionResult.Success
				&& ((InteractionResult.Success) result).swingSource() != InteractionResult.SwingSource.NONE;
	}

	// ------------------------------------------------------------------------------------------
	// Resolution
	// ------------------------------------------------------------------------------------------

	@Override
	public void updateScaledResolution() {
		// 1.20.1 has no ScaledResolution object any more: the Window always carries the current
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
		// Options#renderDebug became the debug overlay's own toggle in 1.21.
		return getMinecraft().getDebugOverlay().showDebugScreen();
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
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		// 1.21.x has no textures/gui/icons.png any more: the HUD food/saturation icons are GUI
		// atlas sprites. The core passes the 1.8-era (u, v) offsets of that sheet, so translate the
		// handful it actually uses to their modern sprite names.
		net.minecraft.resources.ResourceLocation sprite = foodSprite(u, v);
		if (sprite == null) {
			return;
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
	}

	/**
	 * Maps the 1.8 {@code icons.png} food/saturation icon offsets to the 1.21 GUI-atlas sprites.
	 *
	 * @return the sprite, or {@code null} for an offset this port does not know.
	 */
	private static net.minecraft.resources.ResourceLocation foodSprite(int u, int v) {
		if (v != 27) {
			return null;
		}
		switch (u) {
			case 16:
				return net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/food_empty");
			case 52:
				return net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/food_full");
			case 61:
				return net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/food_half");
			case 88:
				return net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/food_full_hunger");
			case 133:
				return net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/food_empty_hunger");
			case 97:
				return net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/food_half_hunger");
			default:
				return null;
		}
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
		ClientPacketListener connection = getMinecraft().getConnection();
		if (connection != null) {
			// 1.20.1 signs chat messages client side; sending the raw packet would be rejected.
			connection.sendChat(message);
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
			The5zigMod.logger.warn("Refusing to send {} bytes on plugin channel {}: servers reject payloads larger than {} bytes",
					payload.readableBytes(), channel, MAX_PAYLOAD_SIZE);
			return;
		}
		// 1.21.6+ replaced free-form plugin channels with typed CustomPacketPayloads: the packet
		// codec only knows payload types registered with PayloadTypeRegistry, and its fallback
		// (DiscardedPayload) writes *no* bytes at all. Arbitrary legacy "5zig" plugin messages
		// therefore cannot be put on the wire any more; dropping them with a single warning is
		// preferable to throwing out of a HUD or chat callback every frame.
		if (!customPayloadWarningLogged) {
			customPayloadWarningLogged = true;
			The5zigMod.logger.warn("Plugin channel {} is not supported on 1.21.11: free-form payloads were replaced by " +
					"registered CustomPacketPayload types. Server-side 5zig features are unavailable.", channel);
		}
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
			DynamicTexture texture = new DynamicTexture(() -> "5zig dynamic image", nativeImage);
			// TextureManager#register no longer derives a "dynamic/<name>_<n>" id itself; build a
			// sanitized one in the mod's own namespace.
			String safeName = name == null ? "image" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
			net.minecraft.resources.ResourceLocation identifier = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("the5zigmod", "dynamic/" + safeName);
			getTextureManager().register(identifier, texture);
			return identifier;
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
		net.minecraft.resources.ResourceLocation location = toTextureLocation(resourceLocation);
		if (location != null) {
			getTextureManager().release(location);
		}
	}

	@Override
	public Object createDynamicImage(Object resourceLocation, int width, int height) {
		DynamicTexture texture = new DynamicTexture(() -> "5zig dynamic image", width, height, false);
		net.minecraft.resources.ResourceLocation location = toTextureLocation(resourceLocation);
		if (location == null) {
			return texture;
		}
		getTextureManager().register(location, texture);
		return texture;
	}

	@Override
	public Object getTexture(Object resourceLocation) {
		// 1.21.9 made ResourceLocation final, so the platform wrapper (IResourceLocation) can no
		// longer be one. The settings GUI draws the fetched player skin through here on its first
		// frame; the raw cast threw a ClassCastException instead (the "dead" Right Shift binding).
		net.minecraft.resources.ResourceLocation location = toTextureLocation(resourceLocation);
		return location == null ? null : getTextureManager().getTexture(location);
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
				pixels.setPixelABGR(x, y, abgr);
			}
		}
		texture.upload();
	}

	@Override
	public void renderPotionIcon(int index) {
		// 1.21.11 has no numeric MobEffect ids; the core's icon index is mapped to a registry entry
		// and that entry's sprite is drawn, exactly like the vanilla HUD does.
		MobEffect effect = BuiltInRegistries.MOB_EFFECT.byId(index);
		if (effect == null) {
			return;
		}
		Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Gui.getMobEffectSprite(holder), 0, 0, 18, 18);
	}

	@Override
	public void renderTextureOverlay(int x1, int x2, int y1, int y2) {
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		// 1.20.1's Screen.BACKGROUND_LOCATION is gone; the in-world menu background is what the
		// chat-symbol panel imitates. The 0.25 grey shader colour became a per-draw ARGB tint.
		graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.MENU_BACKGROUND,
				x1, y1, 0, 0, Math.max(1, x2 - x1), Math.max(1, y2 - y1), 32, 32,
				ARGB.colorFromFloat(1.0f, 0.25f, 0.25f, 0.25f));
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
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) {
			return null;
		}
		String displayName = objective.getDisplayName().getString();
		// 1.21.11 replaced getPlayerScores/getOwner/getScore with PlayerScoreEntry records.
		Collection<PlayerScoreEntry> scores = scoreboard.listPlayerScores(objective);
		HashMap<String, Integer> lines = Maps.newHashMap();
		for (PlayerScoreEntry score : scores) {
			lines.put(score.owner(), score.value());
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
		// 1.20.1 does not ship the window icons as a resource any more (they are compiled into the
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
		// There is no LWJGL2 keyboard event queue to poll on 1.20.1. Key presses are pushed into
		// MinecraftFactory.getClassProxyCallback().fireKeyPressEvent(...) by the keyboard hook while
		// the event is handled, so this tick hook has nothing left to do.
	}

	@Override
	public void shutdown() {
		getMinecraft().stop();
	}

	@Override
	public void setSession(String name, String uuid, String token, String userType) {
		// 1.21.11's User is (name, uuid, accessToken, clientId, xuid); the account type field was
		// dropped together with the legacy Yggdrasil session handling.
		// Offline/legacy accounts pass their own name in the uuid field, so fall back to an offline UUID.
		UUID profileId;
		try {
			profileId = UUID.fromString(uuid);
		} catch (IllegalArgumentException e) {
			profileId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		User user = new User(name, profileId, token, Optional.empty(), Optional.empty());
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
		throw new UnsupportedOperationException("Minecraft 1.21.9 exposes no user field that could be replaced");
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
			return Component.empty();
		}
		MutableComponent result = Component.literal(component.getText() == null ? "" : component.getText());
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
				style = style.withHoverEvent(new HoverEvent.ShowText(toComponent(hover.getComponent())));
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
		// 1.21.11 turned ClickEvent into a sealed interface of records.
		switch (action.getAction()) {
			case OPEN_URL:
				try {
					return new ClickEvent.OpenUrl(new java.net.URI(action.getString()));
				} catch (java.net.URISyntaxException e) {
					The5zigMod.logger.warn("Invalid click URL {}", action.getString(), e);
					return null;
				}
			case OPEN_FILE:
				return new ClickEvent.OpenFile(action.getString());
			case RUN_COMMAND:
				return new ClickEvent.RunCommand(action.getString());
			case SUGGEST_COMMAND:
				return new ClickEvent.SuggestCommand(action.getString());
			default:
				return null;
		}
	}

	private static net.minecraft.resources.ResourceLocation toResourceLocation(String resourceName) {
		int separator = resourceName.indexOf(':');
		if (separator < 0) {
			return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", new ResourceLocation("minecraft", resourceName).callGetResourcePath());
		}
		return new ResourceLocation(resourceName.substring(0, separator), resourceName.substring(separator + 1)).toResourceLocation();
	}

	private static net.minecraft.resources.ResourceLocation toTextureLocation(Object resourceLocation) {
		if (resourceLocation instanceof net.minecraft.resources.ResourceLocation) {
			return (net.minecraft.resources.ResourceLocation) resourceLocation;
		}
		if (resourceLocation instanceof IResourceLocation) {
			IResourceLocation location = (IResourceLocation) resourceLocation;
			return new ResourceLocation(location.callGetResourceDomain(), location.callGetResourcePath()).toResourceLocation();
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
