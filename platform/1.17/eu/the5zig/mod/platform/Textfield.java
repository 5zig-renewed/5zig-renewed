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

import com.mojang.blaze3d.vertex.PoseStack;
import eu.the5zig.mod.gui.elements.ITextfield;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import eu.the5zig.mod.util.GuiContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Created by 5zig.
 * All rights reserved © 2015
 * <p/>
 * Textfield class is a Simple Wrapper for default Minecraft Textfields.
 * <p>
 * 1.17 port: {@code EditBox} is close, but not close enough — the core's config GUI relies on
 * 1.15 semantics ({@code setText} moving the cursor to the end, a selection that is not reset by
 * every cursor move, the {@code maxStringLength} clamp, the {@code Predicate<String>} validator and
 * an explicitly controllable focus). So this is a self-contained {@link AbstractWidget} that
 * reimplements those semantics instead of delegating to {@code EditBox}.
 */
public class Textfield extends AbstractWidget implements ITextfield {

	private static final int BORDER_COLOR_FOCUSED = -1;
	private static final int BORDER_COLOR = -6250336;
	private static final int BACKGROUND_COLOR = -16777216;
	private static final int CURSOR_COLOR = -3092272;
	private static final int HIGHLIGHT_COLOR = -16776961;
	private static final int TEXT_COLOR = 14737632;
	private static final int TEXT_COLOR_DISABLED = 7368816;

	private final Font font;
	private final int id;

	private String text = "";
	private int maxStringLength = 32;
	private int cursorCounter;
	private boolean canLoseFocus = true;
	private boolean enabled = true;
	private boolean password;

	private int cursorPosition;
	private int selectionPos;
	private int displayPos;

	private Predicate<String> validator = Objects::nonNull;

	public Textfield(int id, int x, int y, int width, int height, int maxStringLength) {
		super(x, y, width, height, new TextComponent(""));
		this.id = id;
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
		return isFocused();
	}

	@Override
	public void callSetFocused(boolean focused) {
		setFocused(focused);
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
		setText(string);
	}

	/**
	 * 1.15 {@code TextFieldWidget#setText}: validates, clamps to the max length, moves the cursor to
	 * the end and collapses the selection there. Does not fire any responder.
	 */
	public void setText(String string) {
		if (!this.validator.test(string)) {
			return;
		}
		this.text = string.length() > this.maxStringLength ? string.substring(0, this.maxStringLength) : string;
		this.cursorPosition = this.text.length();
		setSelectionPos(this.cursorPosition);
	}

	public void setValidator(Predicate<String> validator) {
		this.validator = validator;
	}

	public Predicate<String> getValidator() {
		return validator;
	}

	@Override
	public int getX() {
		return this.x;
	}

	@Override
	public void setX(int x) {
		this.x = x;
	}

	@Override
	public int getY() {
		return this.y;
	}

	@Override
	public void setY(int y) {
		this.y = y;
	}

	@Override
	public void callMouseClicked(int x, int y, int button) {
		if (!this.visible) {
			return;
		}
		boolean inside = x >= getX() && x < getX() + getWidth() && y >= getY() && y < getY() + getHeight();
		if (this.canLoseFocus) {
			setFocused(inside);
		}
		if (isFocused() && inside && button == 0) {
			int i = x - getX();
			// A bordered field starts its text 4px in (see renderWidget).
			i -= 4;
			String visible = this.font.plainSubstrByWidth(this.text.substring(this.displayPos), getInnerWidth());
			setCursorPosition(this.font.plainSubstrByWidth(visible, i).length() + this.displayPos);
			setSelectionPos(this.cursorPosition);
		}
	}

	@Override
	public boolean callKeyTyped(char character, int key) {
		return charTyped(character, key);
	}

	@Override
	public void callTick() {
		tick();
	}

	@Override
	public void callDraw() {
		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}
		render(graphics.pose(), 0, 0, 0.0F);
	}

	@Override
	public void setIsPassword(boolean password) {
		this.password = password;
	}

	public boolean isPassword() {
		return password;
	}

	public void tick() {
		this.cursorCounter++;
	}

	@Override
	public boolean charTyped(char character, int modifiers) {
		if (!canConsumeInput()) {
			return false;
		}
		if (SharedConstants.isAllowedChatCharacter(character)) {
			insertText(Character.toString(character));
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!canConsumeInput()) {
			return false;
		}

		if (Screen.isSelectAll(keyCode)) {
			moveCursorToEnd();
			setSelectionPos(0);
			return true;
		}

		if (Screen.isCopy(keyCode)) {
			Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
			return true;
		}
		if (Screen.isPaste(keyCode)) {
			insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
			return true;
		}
		if (Screen.isCut(keyCode)) {
			Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
			insertText("");
			return true;
		}

		switch (keyCode) {
			case 259: // backspace
				deleteText(-1);
				return true;
			case 261: // delete
				deleteText(1);
				return true;
			case 262: // right
				if (Screen.hasControlDown()) {
					moveCursorTo(getWordPosition(1));
				} else {
					moveCursor(1);
				}
				return true;
			case 263: // left
				if (Screen.hasControlDown()) {
					moveCursorTo(getWordPosition(-1));
				} else {
					moveCursor(-1);
				}
				return true;
			case 268: // home
				moveCursorToStart();
				return true;
			case 269: // end
				moveCursorToEnd();
				return true;
			default:
				return false;
		}
	}

	public boolean canConsumeInput() {
		return this.visible && isFocused() && this.enabled;
	}

	public void insertText(String input) {
		int start = Math.min(this.cursorPosition, this.selectionPos);
		int end = Math.max(this.cursorPosition, this.selectionPos);
		int allowed = this.maxStringLength - this.text.length() - (start - end);
		String filtered = SharedConstants.filterText(input);
		int length = filtered.length();
		if (allowed < length) {
			filtered = filtered.substring(0, allowed);
			length = allowed;
		}
		String result = new StringBuilder(this.text).replace(start, end, filtered).toString();
		if (this.validator.test(result)) {
			this.text = result;
			setCursorPosition(start + length);
			setSelectionPos(this.cursorPosition);
		}
	}

	private void deleteText(int direction) {
		if (Screen.hasControlDown()) {
			deleteWords(direction);
		} else {
			deleteChars(direction);
		}
	}

	public void deleteWords(int direction) {
		if (this.text.isEmpty()) {
			return;
		}
		if (this.selectionPos != this.cursorPosition) {
			insertText("");
		} else {
			deleteChars(getWordPosition(direction) - this.cursorPosition);
		}
	}

	public void deleteChars(int direction) {
		if (this.text.isEmpty()) {
			return;
		}
		if (this.selectionPos != this.cursorPosition) {
			insertText("");
		} else {
			int target = getCursorPos(direction);
			int start = Math.min(target, this.cursorPosition);
			int end = Math.max(target, this.cursorPosition);
			if (start != end) {
				String result = new StringBuilder(this.text).delete(start, end).toString();
				if (this.validator.test(result)) {
					this.text = result;
					moveCursorTo(start);
				}
			}
		}
	}

	public int getWordPosition(int count) {
		return getWordPosition(count, this.cursorPosition);
	}

	private int getWordPosition(int count, int from) {
		int pos = from;
		boolean backwards = count < 0;
		int repetitions = Math.abs(count);

		for (int i = 0; i < repetitions; i++) {
			if (!backwards) {
				int length = this.text.length();
				pos = this.text.indexOf(32, pos);
				if (pos == -1) {
					pos = length;
				} else {
					while (pos < length && this.text.charAt(pos) == ' ') {
						pos++;
					}
				}
			} else {
				while (pos > 0 && this.text.charAt(pos - 1) == ' ') {
					pos--;
				}
				while (pos > 0 && this.text.charAt(pos - 1) != ' ') {
					pos--;
				}
			}
		}
		return pos;
	}

	public void moveCursor(int direction) {
		moveCursorTo(getCursorPos(direction));
	}

	private int getCursorPos(int direction) {
		return Util.offsetByCodepoints(this.text, this.cursorPosition, direction);
	}

	public void moveCursorTo(int position) {
		setCursorPosition(position);
		if (!Screen.hasShiftDown()) {
			setSelectionPos(this.cursorPosition);
		}
	}

	public void setCursorPosition(int position) {
		this.cursorPosition = Mth.clamp(position, 0, this.text.length());
	}

	public int getCursorPosition() {
		return this.cursorPosition;
	}

	public String getSelectedText() {
		int start = Math.min(this.cursorPosition, this.selectionPos);
		int end = Math.max(this.cursorPosition, this.selectionPos);
		return this.text.substring(start, end);
	}

	public void setSelectionPos(int position) {
		int length = this.text.length();
		this.selectionPos = Mth.clamp(position, 0, length);

		if (this.displayPos > length) {
			this.displayPos = length;
		}
		int innerWidth = getInnerWidth();
		String visible = this.font.plainSubstrByWidth(this.text.substring(this.displayPos), innerWidth);
		int visibleEnd = visible.length() + this.displayPos;
		if (this.selectionPos == this.displayPos) {
			this.displayPos -= this.font.plainSubstrByWidth(this.text, innerWidth, true).length();
		}
		if (this.selectionPos > visibleEnd) {
			this.displayPos += this.selectionPos - visibleEnd;
		} else if (this.selectionPos <= this.displayPos) {
			this.displayPos -= this.displayPos - this.selectionPos;
		}
		this.displayPos = Mth.clamp(this.displayPos, 0, length);
	}

	public void moveCursorToStart() {
		moveCursorTo(0);
	}

	public void moveCursorToEnd() {
		moveCursorTo(this.text.length());
	}

	public int getInnerWidth() {
		return getWidth() - 8;
	}

	@Override
	public void setFocused(boolean focused) {
		if (this.canLoseFocus || focused) {
			super.setFocused(focused);
			if (focused) {
				this.cursorCounter = 0;
			}
		}
	}

	public void setCanLoseFocus(boolean canLoseFocus) {
		this.canLoseFocus = canLoseFocus;
	}

	public boolean isVisible() {
		return this.visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		if (!this.visible) {
			return;
		}
		GuiContext graphics = new GuiContext(poseStack);

		int border = isFocused() ? BORDER_COLOR_FOCUSED : BORDER_COLOR;
		graphics.fill(getX() - 1, getY() - 1, getX() + getWidth() + 1, getY() + getHeight() + 1, border);
		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), BACKGROUND_COLOR);

		// Password mode never mutates the real text; it swaps in an equal-length mask for drawing.
		String display = this.password ? "*".repeat(this.text.length()) : this.text;
		int color = this.enabled ? TEXT_COLOR : TEXT_COLOR_DISABLED;

		int relativeCursor = this.cursorPosition - this.displayPos;
		int relativeSelection = this.selectionPos - this.displayPos;
		String visible = this.font.plainSubstrByWidth(display.substring(this.displayPos), getInnerWidth());
		boolean cursorInView = relativeCursor >= 0 && relativeCursor <= visible.length();
		boolean blink = isFocused() && this.cursorCounter / 6 % 2 == 0 && cursorInView;
		int textX = getX() + 4;
		int textY = getY() + (getHeight() - 8) / 2;
		int drawX = textX;

		if (relativeSelection > visible.length()) {
			relativeSelection = visible.length();
		}
		relativeSelection = Math.max(0, relativeSelection);

		if (!visible.isEmpty()) {
			String before = cursorInView ? visible.substring(0, relativeCursor) : visible;
			drawX = graphics.drawString(this.font, before, drawX, textY, color);
		}

		boolean cursorAtEnd = this.cursorPosition < display.length() || display.length() >= this.maxStringLength;
		int cursorX = drawX;
		if (!cursorInView) {
			cursorX = relativeCursor > 0 ? textX + getWidth() : textX;
		} else if (cursorAtEnd) {
			cursorX--;
			drawX--;
		}

		if (!visible.isEmpty() && cursorInView && relativeCursor < visible.length()) {
			graphics.drawString(this.font, visible.substring(relativeCursor), drawX, textY, color);
		}

		if (blink) {
			if (cursorAtEnd) {
				graphics.fill(cursorX, textY - 1, cursorX + 1, textY + 1 + 9, CURSOR_COLOR);
			} else {
				graphics.drawString(this.font, "_", cursorX, textY, color);
			}
		}

		if (relativeSelection != relativeCursor) {
			int selectionX = textX + this.font.width(visible.substring(0, relativeSelection));
			renderHighlight(graphics, cursorX, textY - 1, selectionX - 1, textY + 1 + 9);
		}
	}

	private void renderHighlight(GuiContext graphics, int x1, int y1, int x2, int y2) {
		int left = Math.min(x1, x2);
		int right = Math.max(x1, x2);
		int top = Math.min(y1, y2);
		int bottom = Math.max(y1, y2);
		right = Math.min(right, getX() + getWidth());
		left = Math.min(left, getX() + getWidth());
		graphics.fill(left, top, right, bottom, HIGHLIGHT_COLOR);
	}

	@Override
	public void updateNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, new TextComponent(this.text));
	}
}
