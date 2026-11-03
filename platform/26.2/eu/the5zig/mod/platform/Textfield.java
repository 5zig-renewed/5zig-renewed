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

import eu.the5zig.mod.gui.elements.ITextfield;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Created by 5zig.
 * All rights reserved © 2015
 * <p/>
 * Textfield class is a Simple Wrapper for default Minecraft Textfields.
 * <p>
 * 26.x port: this is a standalone widget rather than an {@code AbstractWidget} subclass. The core's
 * config GUI relies on 1.15 semantics ({@code setText} moving the cursor to the end, a selection that
 * is not reset by every cursor move, the {@code maxStringLength} clamp, the {@code Predicate<String>}
 * validator and explicitly controllable focus), and 26.x changed both the widget base class
 * ({@code extractWidgetRenderState}) and the input event types, so implementing {@link ITextfield}
 * directly is both smaller and closer to the original behaviour.
 */
public class Textfield implements ITextfield {

	private static final int BORDER_COLOR_FOCUSED = 0xFFFFFFFF;
	private static final int BORDER_COLOR = 0xFFA0A0A0;
	private static final int BACKGROUND_COLOR = 0xFF000000;
	private static final int CURSOR_COLOR = 0xFFD0D0D0;
	private static final int HIGHLIGHT_COLOR = 0xFF0000FF;
	private static final int TEXT_COLOR = 14737632;
	private static final int TEXT_COLOR_DISABLED = 7368816;

	private final Font font;
	private final int id;

	private int x;
	private int y;
	private int width;
	private int height;

	private String text = "";
	private int maxStringLength = 32;
	private int cursorCounter;
	private boolean canLoseFocus = true;
	private boolean enabled = true;
	private boolean focused;
	private boolean password;

	private int cursorPosition;
	private int selectionPos;
	private int displayPos;

	private Predicate<String> validator = Objects::nonNull;

	public Textfield(int id, int x, int y, int width, int height, int maxStringLength) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.font = Minecraft.getInstance().font;
		this.maxStringLength = maxStringLength;
	}

	public Textfield(int id, int x, int y, int width, int height) {
		this(id, x, y, width, height, 32);
	}

	@Override
	public int callGetId() {
		return id;
	}

	@Override
	public void setSelected(boolean selected) {
		setFocused(selected);
	}

	@Override
	public boolean callIsFocused() {
		return focused;
	}

	@Override
	public void callSetFocused(boolean focused) {
		setFocused(focused);
	}

	public void setFocused(boolean focused) {
		if (focused && !this.focused) {
			this.cursorCounter = 0;
		}
		this.focused = focused;
	}

	public boolean isFocused() {
		return focused;
	}

	public void setCanLoseFocus(boolean canLoseFocus) {
		this.canLoseFocus = canLoseFocus;
	}

	@Override
	public boolean isBackgroundDrawing() {
		// 1.15's 5zig Textfield hard-coded this to false (the vanilla field was never touched).
		return false;
	}

	/**
	 * Retained for API parity with 1.15's {@code TextFieldWidget#setBackgroundDrawing}.
	 */
	public void setBackgroundDrawing(boolean ignored) {
	}

	@Override
	public int callGetMaxStringLength() {
		return this.maxStringLength;
	}

	@Override
	public void callSetMaxStringLength(int length) {
		this.maxStringLength = length;
		if (this.text.length() > length) {
			this.text = this.text.substring(0, length);
		}
		this.cursorPosition = Math.min(this.cursorPosition, this.text.length());
		this.selectionPos = Math.min(this.selectionPos, this.text.length());
	}

	@Override
	public String callGetText() {
		return this.text;
	}

	@Override
	public void callSetText(String string) {
		if (string == null) {
			string = "";
		}
		if (validator.test(string)) {
			if (string.length() > maxStringLength) {
				this.text = string.substring(0, maxStringLength);
			} else {
				this.text = string;
			}
			moveCursorToEnd();
		}
	}

	public void setValidator(Predicate<String> validator) {
		this.validator = validator == null ? Objects::nonNull : validator;
	}

	private void moveCursorToEnd() {
		this.cursorPosition = this.text.length();
		this.selectionPos = this.cursorPosition;
		this.displayPos = Math.min(this.displayPos, this.text.length());
	}

	@Override
	public int getX() {
		return x;
	}

	@Override
	public void setX(int x) {
		this.x = x;
	}

	@Override
	public int getY() {
		return y;
	}

	@Override
	public void setY(int y) {
		this.y = y;
	}

	@Override
	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public void setIsPassword(boolean b) {
		this.password = b;
	}

	@Override
	public void callTick() {
		++this.cursorCounter;
	}

	@Override
	public void callDraw() {
		GuiGraphicsExtractor graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		int border = focused ? BORDER_COLOR_FOCUSED : BORDER_COLOR;
		graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, border);
		graphics.fill(x, y, x + width, y + height, BACKGROUND_COLOR);

		int textColor = enabled ? TEXT_COLOR : TEXT_COLOR_DISABLED;
		int innerWidth = width - 8;
		String visible = password ? "*".repeat(text.length()) : text;
		String shown = font.plainSubstrByWidth(visible.substring(Math.min(displayPos, visible.length())), innerWidth);
		boolean cursorVisible = focused && (cursorCounter / 6) % 2 == 0;
		int textY = y + (height - 8) / 2;

		int cursorOffset = font.width(visible.substring(Math.min(displayPos, Math.min(cursorPosition, visible.length())),
				Math.min(cursorPosition, visible.length())));
		if (cursorVisible) {
			graphics.fill(x + 4 + cursorOffset, textY - 1, x + 4 + cursorOffset + 1, textY + 9, CURSOR_COLOR);
		}
		graphics.text(font, shown, x + 4, textY, textColor, false);

		if (selectionPos != cursorPosition) {
			int start = Math.min(selectionPos, cursorPosition);
			int end = Math.max(selectionPos, cursorPosition);
			int selStart = x + 4 + font.width(visible.substring(Math.min(displayPos, start), start));
			int selEnd = x + 4 + font.width(visible.substring(Math.min(displayPos, end), end));
			graphics.fill(selStart, textY - 1, selEnd, textY + 9, HIGHLIGHT_COLOR);
		}
	}

	@Override
	public void callMouseClicked(int mouseX, int mouseY, int button) {
		if (!enabled) {
			return;
		}
		boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		setFocused(inside);
		if (inside && button == 0) {
			String visible = password ? "*".repeat(text.length()) : text;
			int relative = mouseX - (x + 4) + font.width(visible.substring(0, Math.min(displayPos, visible.length())));
			int position = font.plainSubstrByWidth(visible, Math.max(0, relative)).length();
			this.cursorPosition = Mth.clamp(position, 0, text.length());
			this.selectionPos = this.cursorPosition;
		}
	}

	@Override
	public boolean callKeyTyped(char character, int key) {
		if (!enabled || !focused) {
			return false;
		}
		if (character >= 32 && character != 127) {
			insert(String.valueOf(character));
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (!enabled || !focused) {
			return false;
		}
		boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
		switch (key) {
			case GLFW.GLFW_KEY_BACKSPACE:
				if (selectionPos != cursorPosition) {
					deleteSelection();
				} else if (cursorPosition > 0) {
					text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition);
					cursorPosition--;
					selectionPos = cursorPosition;
				}
				return true;
			case GLFW.GLFW_KEY_DELETE:
				if (selectionPos != cursorPosition) {
					deleteSelection();
				} else if (cursorPosition < text.length()) {
					text = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1);
				}
				return true;
			case GLFW.GLFW_KEY_LEFT:
				cursorPosition = Math.max(0, cursorPosition - 1);
				if (!shift) {
					selectionPos = cursorPosition;
				}
				return true;
			case GLFW.GLFW_KEY_RIGHT:
				cursorPosition = Math.min(text.length(), cursorPosition + 1);
				if (!shift) {
					selectionPos = cursorPosition;
				}
				return true;
			case GLFW.GLFW_KEY_HOME:
				cursorPosition = 0;
				if (!shift) {
					selectionPos = cursorPosition;
				}
				return true;
			case GLFW.GLFW_KEY_END:
				cursorPosition = text.length();
				if (!shift) {
					selectionPos = cursorPosition;
				}
				return true;
			case GLFW.GLFW_KEY_A:
				if (control) {
					selectionPos = 0;
					cursorPosition = text.length();
					return true;
				}
				return false;
			case GLFW.GLFW_KEY_C:
				if (control && selectionPos != cursorPosition) {
					Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
					return true;
				}
				return false;
			case GLFW.GLFW_KEY_X:
				if (control && selectionPos != cursorPosition) {
					Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
					deleteSelection();
					return true;
				}
				return false;
			case GLFW.GLFW_KEY_V:
				if (control) {
					insert(Minecraft.getInstance().keyboardHandler.getClipboard());
					return true;
				}
				return false;
			default:
				return false;
		}
	}

	private String selectedText() {
		int start = Math.min(selectionPos, cursorPosition);
		int end = Math.max(selectionPos, cursorPosition);
		return text.substring(start, end);
	}

	private void deleteSelection() {
		int start = Math.min(selectionPos, cursorPosition);
		int end = Math.max(selectionPos, cursorPosition);
		text = text.substring(0, start) + text.substring(end);
		cursorPosition = start;
		selectionPos = start;
	}

	private void insert(String value) {
		if (value == null || value.isEmpty()) {
			return;
		}
		if (selectionPos != cursorPosition) {
			deleteSelection();
		}
		int room = maxStringLength - text.length();
		if (room <= 0) {
			return;
		}
		String addition = value.length() > room ? value.substring(0, room) : value;
		String candidate = text.substring(0, cursorPosition) + addition + text.substring(cursorPosition);
		if (!validator.test(candidate)) {
			return;
		}
		text = candidate;
		cursorPosition += addition.length();
		selectionPos = cursorPosition;
	}

	/**
	 * @return true while the field is focused, mirroring 1.15's {@code isFocused()} for the core's
	 * text-field registry.
	 */
	public boolean isActive() {
		return focused;
	}
}
