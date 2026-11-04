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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.gui.ingame.IGui2ndChat;
import eu.the5zig.mod.util.LegacyText;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.Minecraft;
import eu.the5zig.mod.util.GuiContext;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * The mod's second chat window (the one that is anchored to the top right of the screen instead of
 * the bottom left).
 * <p>
 * Ported from the 1.15.1 implementation. 1.15 drew through a global tessellator, wrapped messages
 * with {@code RenderComponentsUtil#splitText} and stored {@code TextComponent}s; 1.17 hands a
 * {@link GuiContext} to every draw call, wraps with
 * {@link ComponentRenderUtils#wrapComponents} (which yields one {@link FormattedCharSequence} per
 * rendered line) and has no legacy text components, so hover/click resolution walks the sequence's
 * per-character styles instead of the component tree.
 */
public class Gui2ndChat implements IGui2ndChat {

	private static final org.apache.logging.log4j.Logger LOG4J = LogManager.getLogger();

	private final List<String> sentMessages = Lists.newArrayList();
	private final List<GuiChatLine> chatLines = Lists.newArrayList();
	private final List<GuiChatLine> singleChatLines = Lists.newArrayList();
	private int scrollPos;
	private boolean isScrolled;

	public Gui2ndChat() {
	}

	@Override
	public void draw(int updateCounter) {
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		if (!MinecraftFactory.getClassProxyCallback().is2ndChatVisible()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		int lineDisplayCount = this.getLineCount();
		int chatLineCount = this.singleChatLines.size();
		if (chatLineCount == 0) {
			return;
		}
		boolean chatOpened = this.isChatOpened();
		int totalChatLines = 0;
		float opacity = MinecraftFactory.getClassProxyCallback().get2ndChatOpacity() * 0.9F + 0.1F;
		float chatScale = this.getChatScale();
		if (chatScale <= 0.0F) {
			return;
		}
		int width = Mth.ceil((float) this.getChatWidth() / chatScale);
		boolean leftbound = MinecraftFactory.getClassProxyCallback().is2ndChatTextLeftbound();
		boolean transparentBackground = MinecraftFactory.getClassProxyCallback().isChatBackgroundTransparent();

		PoseStack pose = graphics.pose();
		pose.pushPose();
		try {
			pose.translate(MinecraftFactory.getVars().getScaledWidth() - this.getChatWidth() - 6.0F * chatScale, 20.0F, 0.0F);
			pose.scale(chatScale, chatScale, 1.0F);

			for (int lineIndex = 0; lineIndex + this.scrollPos < this.singleChatLines.size() && lineIndex < lineDisplayCount; ++lineIndex) {
				GuiChatLine chatLine = this.singleChatLines.get(lineIndex + this.scrollPos);
				if (chatLine == null) {
					continue;
				}
				int ticksPassed = updateCounter - chatLine.getUpdatedCounter();
				if (ticksPassed >= 200 && !chatOpened) {
					continue;
				}
				double fade = (double) ticksPassed / 200.0D;
				fade = 1.0D - fade;
				fade *= 10.0D;
				fade = Mth.clamp(fade, 0.0D, 1.0D);
				fade *= fade;
				int alpha = (int) (255.0D * fade);
				if (chatOpened) {
					alpha = 255;
				}
				alpha = (int) ((float) alpha * opacity);
				++totalChatLines;
				if (alpha <= 3) {
					continue;
				}

				int x = 0;
				int y = -lineIndex * 9;
				int lineWidth = width + Mth.floor(4.0F * chatScale);
				if (!transparentBackground) {
					graphics.fill(x, y - 9, x + lineWidth, y, alpha / 2 << 24);
				}
				String text = chatLine.getPlainText();
				this.highlightChatLine(graphics, minecraft, text, x, y - 9, alpha);
				int textWidth = minecraft.font.width(chatLine.getVisual());
				int textX = leftbound ? x : x + width - textWidth;
				graphics.drawString(minecraft.font, chatLine.getVisual(), textX, y - 8, 16777215 + (alpha << 24));
			}

			if (chatOpened && totalChatLines != chatLineCount) {
				int fontHeight = minecraft.font.lineHeight;
				int visibleLineHeight = chatLineCount * fontHeight + chatLineCount;
				int totalLineHeight = totalChatLines * fontHeight + totalChatLines;
				int scrollOffset = this.scrollPos * totalLineHeight / chatLineCount;
				int scrollHeight = totalLineHeight * totalLineHeight / visibleLineHeight;
				int scrollAlpha = scrollOffset > 0 ? 170 : 96;
				int color = this.isScrolled ? 13382451 : 3355562;
				graphics.fill(width + 6, -scrollOffset, width + 8, -scrollOffset - scrollHeight, color + (scrollAlpha << 24));
				graphics.fill(width + 8, -scrollOffset, width + 7, -scrollOffset - scrollHeight, 13421772 + (scrollAlpha << 24));
			}
		} finally {
			pose.popPose();
		}
	}

	/**
	 * Highlights every occurrence of the configured highlight words in a chat line.
	 * <p>
	 * 1.15 walked the component tree and re-measured the accumulated legacy string; the 1.17
	 * equivalent is to measure the plain line text, which is exactly what is drawn.
	 */
	private void highlightChatLine(GuiContext graphics, Minecraft minecraft, String text, int x, int y, int alpha) {
		List<String> highlightWords;
		boolean onlyWordMatch;
		String chatSearchText = MinecraftFactory.getClassProxyCallback().getChatSearchText();
		if (!Strings.isNullOrEmpty(chatSearchText)) {
			onlyWordMatch = false;
			highlightWords = ImmutableList.of(chatSearchText);
		} else {
			onlyWordMatch = true;
			highlightWords = MinecraftFactory.getClassProxyCallback().getHighlightWords();
		}
		if (highlightWords.isEmpty()) {
			return;
		}
		String lowerText = text.toLowerCase(Locale.ROOT);
		for (String search : highlightWords) {
			search = search.replace("%player%", MinecraftFactory.getVars().getGameProfile().getName()).toLowerCase(Locale.ROOT);
			if (search.isEmpty()) {
				continue;
			}
			for (int index = lowerText.indexOf(search); index != -1; index = lowerText.indexOf(search, index + search.length())) {
				if (onlyWordMatch) {
					if (index > 0 && isWordCharacter(lowerText.charAt(index - 1))) {
						continue;
					}
					int end = index + search.length();
					if (end < lowerText.length() && isWordCharacter(lowerText.charAt(end))) {
						continue;
					}
				}
				int offset = minecraft.font.width(text.substring(0, index));
				int wordWidth = minecraft.font.width(text.substring(index, index + search.length()));
				graphics.fill(x + offset, y, x + offset + wordWidth, y + minecraft.font.lineHeight,
						MinecraftFactory.getClassProxyCallback().getHighlightWordsColor() + (Math.min(0x80, alpha) << 24));
			}
		}
	}

	private static boolean isWordCharacter(char character) {
		return (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9');
	}

	@Override
	public void printChatMessage(String message) {
		this.printChatMessage(LegacyText.fromLegacy(message));
	}

	@Override
	public void printChatMessage(Object chatComponent) {
		if (chatComponent instanceof Component) {
			this.printChatMessage((Component) chatComponent);
		} else if (chatComponent instanceof String) {
			this.printChatMessage((String) chatComponent);
		} else {
			throw new IllegalArgumentException((chatComponent == null ? "null" : chatComponent.getClass().getName()) + " != " + Component.class.getName());
		}
	}

	public void printChatMessage(Component chatComponent) {
		this.printChatMessage(chatComponent, 0);
	}

	public void printChatMessage(Component chatComponent, int id) {
		LOG4J.info("[CHAT2] {}", chatComponent.getString());
		this.setChatLine(chatComponent, id, Minecraft.getInstance().gui.getGuiTicks(), false);
	}

	private void setChatLine(Component chatComponent, int id, int currentUpdateCounter, boolean refresh) {
		if (!refresh && MinecraftFactory.getClassProxyCallback().isShowTimeBeforeChatMessage()) {
			Object withTime = MinecraftFactory.getClassProxyCallback().getChatComponentWithTime(chatComponent);
			if (withTime instanceof Component) {
				chatComponent = (Component) withTime;
			} else if (withTime instanceof String) {
				chatComponent = LegacyText.fromLegacy((String) withTime);
			}
		}
		if (id != 0) {
			this.deleteChatLine(id);
		}

		Minecraft minecraft = Minecraft.getInstance();
		int lineWidth = Mth.floor((float) this.getChatWidth() / this.getChatScale());
		List<FormattedCharSequence> lines = ComponentRenderUtils.wrapComponents(chatComponent, lineWidth, minecraft.font);
		boolean chatOpened = this.isChatOpened();

		for (FormattedCharSequence line : lines) {
			if (chatOpened && this.scrollPos > 0) {
				this.isScrolled = true;
				this.scroll(1);
			}
			this.singleChatLines.add(0, new GuiChatLine(currentUpdateCounter, chatComponent, line, id));
		}

		while (this.singleChatLines.size() > MinecraftFactory.getClassProxyCallback().getMaxChatLines()) {
			this.singleChatLines.remove(this.singleChatLines.size() - 1);
		}

		if (!refresh) {
			this.chatLines.add(0, new GuiChatLine(currentUpdateCounter, chatComponent, id));

			while (this.chatLines.size() > MinecraftFactory.getClassProxyCallback().getMaxChatLines()) {
				this.chatLines.remove(this.chatLines.size() - 1);
			}
		}
	}

	@Override
	public void clear() {
		this.sentMessages.clear();
		this.singleChatLines.clear();
		this.chatLines.clear();
		this.resetScroll();
	}

	public void clearChatMessages() {
		this.clear();
	}

	@Override
	public void refreshChat() {
		this.singleChatLines.clear();
		this.resetScroll();

		for (int i = this.chatLines.size() - 1; i >= 0; --i) {
			GuiChatLine chatLine = this.chatLines.get(i);
			this.setChatLine(chatLine.getChatComponent(), chatLine.getChatLineID(), chatLine.getUpdatedCounter(), true);
		}
	}

	public List<String> getSentMessages() {
		return this.sentMessages;
	}

	public void addToSentMessages(String message) {
		if (this.sentMessages.isEmpty() || !this.sentMessages.get(this.sentMessages.size() - 1).equals(message)) {
			this.sentMessages.add(message);
		}
	}

	@Override
	public void resetScroll() {
		this.scrollPos = 0;
		this.isScrolled = false;
	}

	@Override
	public void scroll(int scroll) {
		this.scrollPos += scroll;
		int chatLines = this.singleChatLines.size();
		if (this.scrollPos > chatLines - this.getLineCount()) {
			this.scrollPos = chatLines - this.getLineCount();
		}

		if (this.scrollPos <= 0) {
			this.scrollPos = 0;
			this.isScrolled = false;
		}
	}

	@Override
	public void drawComponentHover(int mouseX, int mouseY) {
		GuiContext graphics = RenderContext.get();
		Style style = this.getStyleAt(mouseX, mouseY);
		if (graphics == null || style == null) {
			return;
		}
		graphics.renderComponentHoverEffect(Minecraft.getInstance().font, style, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(int mouseX, int mouseY, int button) {
		if (button != 0) {
			return false;
		}
		Style style = this.getStyleAt(mouseX, mouseY);
		if (style == null || style.getClickEvent() == null) {
			return false;
		}
		ChatScreen screen = Minecraft.getInstance().screen instanceof ChatScreen ? (ChatScreen) Minecraft.getInstance().screen : null;
		if (screen == null) {
			return false;
		}
		return screen.handleComponentClicked(style);
	}

	@Override
	public void keyTyped(int code) {
		// LWJGL page up / page down
		if (code == 201) {
			this.scroll(this.getLineCount() - 1);
		} else if (code == 209) {
			this.scroll(-this.getLineCount() + 1);
		}
	}

	/**
	 * Resolves the style of the character under the mouse.
	 *
	 * @return the style, or {@code null} if the mouse is not over a chat line.
	 */
	private Style getStyleAt(int mouseX, int mouseY) {
		if (!this.isChatOpened()) {
			return null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		int resolutionScaleFactor = (int) minecraft.getWindow().getGuiScale();
		if (resolutionScaleFactor <= 0) {
			return null;
		}
		float chatScale = this.getChatScale();
		int x = mouseX / resolutionScaleFactor - MinecraftFactory.getVars().getScaledWidth() + this.getChatWidth() + 6;
		int y = mouseY / resolutionScaleFactor - 27;
		x = Mth.floor((float) x / chatScale);
		y = Mth.floor((float) y / chatScale);
		if (x < 0 || y < 0) {
			return null;
		}
		int lineCount = Math.min(this.getLineCount(), this.singleChatLines.size());
		if (x > Mth.floor((float) this.getChatWidth() / this.getChatScale() + 3 / chatScale) || y >= minecraft.font.lineHeight * lineCount) {
			return null;
		}
		int lineId = y / minecraft.font.lineHeight + this.scrollPos;
		if (lineId < 0 || lineId >= this.singleChatLines.size()) {
			return null;
		}
		GuiChatLine chatLine = this.singleChatLines.get(lineId);
		int widthCounter = MinecraftFactory.getClassProxyCallback().is2ndChatTextLeftbound() ? 0
				: (int) ((this.getChatWidth() - minecraft.font.width(chatLine.getVisual()) * chatScale) / chatScale);
		return getStyleAt(chatLine.getVisual(), x - widthCounter, minecraft);
	}

	private static Style getStyleAt(FormattedCharSequence sequence, int targetX, Minecraft minecraft) {
		int[] width = {0};
		Style[] result = {null};
		sequence.accept((index, style, codePoint) -> {
			if (result[0] != null) {
				return false;
			}
			if (width[0] > targetX) {
				return false;
			}
			result[0] = style;
			width[0] += minecraft.font.width(new String(Character.toChars(codePoint)));
			return true;
		});
		return result[0];
	}

	public boolean isChatOpened() {
		return Minecraft.getInstance().screen instanceof ChatScreen;
	}

	/**
	 * Finds and deletes a chat line by id.
	 */
	public void deleteChatLine(int id) {
		Iterator<GuiChatLine> iterator = this.singleChatLines.iterator();
		while (iterator.hasNext()) {
			if (iterator.next().getChatLineID() == id) {
				iterator.remove();
			}
		}

		iterator = this.chatLines.iterator();
		while (iterator.hasNext()) {
			if (iterator.next().getChatLineID() == id) {
				iterator.remove();
				break;
			}
		}
	}

	public int getChatWidth() {
		return Mth.floor(MinecraftFactory.getClassProxyCallback().get2ndChatWidth());
	}

	public int getChatHeight() {
		return Mth.floor(this.isChatOpened() ? MinecraftFactory.getClassProxyCallback().get2ndChatHeightFocused()
				: MinecraftFactory.getClassProxyCallback().get2ndChatHeightUnfocused());
	}

	public float getChatScale() {
		return MinecraftFactory.getClassProxyCallback().get2ndChatScale();
	}

	@Override
	public int getLineCount() {
		return this.getChatHeight() / 9;
	}
}
