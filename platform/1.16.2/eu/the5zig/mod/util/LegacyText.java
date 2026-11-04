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

package eu.the5zig.mod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/**
 * Conversion between the legacy {@code '\u00a7'}-coded strings the 5zig core works with and the
 * {@link Component} based text model of modern Minecraft.
 * <p>
 * The core builds every chat line, module label and overlay text as a plain {@link String} that may
 * contain section-sign formatting codes. Modern Minecraft only interprets those codes when a raw
 * {@code String} is rendered; as soon as the text is wrapped into a {@link Component} (chat, toast,
 * tooltip) the codes would show up literally. Both directions are therefore implemented here.
 */
public final class LegacyText {

	private LegacyText() {
	}

	/**
	 * Parses a {@code '\u00a7'}-coded string into a component tree.
	 *
	 * @param text the legacy text, may be {@code null}.
	 * @return a component that renders exactly like the input string did on 1.15.
	 */
	public static Component fromLegacy(String text) {
		MutableComponent root = Texts.empty();
		if (text == null || text.isEmpty()) {
			return root;
		}
		StringBuilder buffer = new StringBuilder();
		Style style = Style.EMPTY;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\u00a7' && i + 1 < text.length()) {
				ChatFormatting formatting = ChatFormatting.getByCode(text.charAt(i + 1));
				if (formatting != null) {
					if (buffer.length() > 0) {
						root.append(Texts.literal(buffer.toString()).withStyle(style));
						buffer.setLength(0);
					}
					if (formatting == ChatFormatting.RESET) {
						style = Style.EMPTY;
					} else if (formatting.isFormat()) {
						style = style.applyFormat(formatting);
					} else {
						style = style.withColor(formatting);
					}
					i++;
					continue;
				}
			}
			buffer.append(c);
		}
		if (buffer.length() > 0) {
			root.append(Texts.literal(buffer.toString()).withStyle(style));
		}
		return root;
	}

	/**
	 * Serializes a laid out text run back into a {@code '\u00a7'}-coded string, so that the core can
	 * measure, shorten and re-render wrapped text with its existing string based helpers.
	 *
	 * @param sequence the text run produced by {@code Font#split}.
	 * @return the legacy representation of the run.
	 */
	public static String toLegacy(FormattedCharSequence sequence) {
		StringBuilder out = new StringBuilder();
		Style[] current = {Style.EMPTY};
		sequence.accept((index, style, codePoint) -> {
			if (!current[0].equals(style)) {
				// Reset first so that a dropped colour/format cannot leak into the next run.
				out.append(ChatFormatting.RESET);
				appendCodes(out, style);
				current[0] = style;
			}
			out.appendCodePoint(codePoint);
			return true;
		});
		return out.toString();
	}

	private static void appendCodes(StringBuilder out, Style style) {
		if (style.getColor() != null) {
			ChatFormatting color = legacyColor(style.getColor());
			if (color != null) {
				out.append(color);
			}
		}
		if (style.isObfuscated()) {
			out.append(ChatFormatting.OBFUSCATED);
		}
		if (style.isBold()) {
			out.append(ChatFormatting.BOLD);
		}
		if (style.isStrikethrough()) {
			out.append(ChatFormatting.STRIKETHROUGH);
		}
		if (style.isUnderlined()) {
			out.append(ChatFormatting.UNDERLINE);
		}
		if (style.isItalic()) {
			out.append(ChatFormatting.ITALIC);
		}
	}

	private static ChatFormatting legacyColor(TextColor color) {
		for (ChatFormatting formatting : ChatFormatting.values()) {
			if (formatting.isColor() && formatting.getColor() != null && formatting.getColor() == color.getValue()) {
				return formatting;
			}
		}
		return null;
	}
}
