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

import com.google.common.collect.Lists;
import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.gui.Gui;
import eu.the5zig.mod.gui.elements.Clickable;
import eu.the5zig.mod.gui.elements.IGuiList;
import eu.the5zig.mod.gui.elements.IButton;
import eu.the5zig.mod.gui.elements.Row;
import eu.the5zig.mod.gui.elements.RowExtended;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Created by 5zig.
 * All rights reserved © 2015
 * <p>
 * 1.20.1 port. 1.15 extended {@code AbstractList}; 1.20.1's {@code AbstractSelectionList} is
 * generic over its own {@code Entry} type and cannot hold the core's {@link Row} implementations,
 * so this class carries its own scroll state, scrollbar, culling and scissor logic (which the 1.15
 * version overrode almost entirely anyway).
 * <p>
 * Drawing uses the frame's {@link RenderContext} {@code GuiGraphics}; rows draw themselves through
 * the core (which resolves {@code IVariables} against the same context).
 */
public class GuiList<E extends Row> implements IGuiList<E> {

	protected final List<E> rows;
	private final Clickable<E> clickable;

	private int rowWidth = 95;
	private int bottomPadding;
	private boolean leftbound = false;
	private int scrollX;

	private boolean hasSelected = false;

	protected int mouseX, mouseY;

	private int selected;
	private IButton selectedButton;

	private String header;
	private int headerHeight;

	private boolean drawDefaultBackground = false;
	private Object backgroundTexture;
	private int backgroundWidth, backgroundHeight;

	private long lastClicked;

	protected List<Integer> heightMap = Lists.newArrayList();

	protected int width;
	protected int height;
	protected int x0, x1, y0, y1;

	protected int itemHeight = 18;
	private double scrollAmount;
	private boolean renderSelection = true;
	protected boolean visible = true;

	public GuiList(Clickable<E> clickable, int width, int height, int top, int bottom, int left, int right, List<E> rows) {
		this.width = width;
		this.height = height;
		this.y0 = top;
		this.y1 = bottom;
		this.x0 = 0;
		this.x1 = width;

		this.rows = rows;
		this.clickable = clickable;
		this.x0 = left;
		this.x1 = right;
	}

	/**
	 * @return the size of all rows
	 */
	public int getItemCount() {
		synchronized (rows) {
			return rows.size();
		}
	}

	/**
	 * 1.15 exposed this as the list's total content height.
	 */
	public int getItemHeight() {
		return callGetContentHeight();
	}

	public boolean selectItem(int id, int button, double mouseX, double mouseY) {
		int last = selected;
		selected = id;
		boolean valid = (this.selected >= 0) && (this.selected < getItemCount());
		if (valid && clickable != null) {
			synchronized (rows) {
				boolean doubleClick = id == last && System.currentTimeMillis() - this.lastClicked < 250L;
				onSelect(id, rows.get(id), doubleClick);
				this.lastClicked = System.currentTimeMillis();
				return true;
			}
		}
		return false;
	}

	@Override
	public void onSelect(int id, E row, boolean doubleClick) {
		setSelectedId(id);
		if (clickable != null && row != null)
			clickable.onSelect(id, row, doubleClick);
	}

	@Override
	public boolean callIsSelected(int id) {
		return selected == id;
	}

	public boolean isSelectedItem(int id) {
		return selected == id;
	}

	@Override
	public int callGetRowWidth() {
		return getRowWidth();
	}

	public int getRowWidth() {
		return rowWidth;
	}

	@Override
	public void setRowWidth(int rowWidth) {
		this.rowWidth = rowWidth;
	}

	protected void renderItem(int id, int x, int y, int slotHeight, int mouseX, int mouseY, float partialTicks) {
		_drawSlot(id, x, y, slotHeight, mouseX, mouseY, partialTicks);
	}

	protected void _drawSlot(int id, int x, int y, int slotHeight, int mouseX, int mouseY, float partialTicks) {
		synchronized (rows) {
			if (id < 0 || id >= rows.size())
				return;
			Row row = rows.get(id);
			row.draw(x, y);
			if (row instanceof RowExtended) {
				((RowExtended) row).draw(x, y, slotHeight, mouseX, mouseY);
			}
		}
	}

	@Override
	public void callDrawScreen(int mouseX, int mouseY, float partialTicks) {
		drawScreen(mouseX, mouseY, partialTicks);
	}

	/**
	 * Draw Screen
	 */
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		calculateHeightMap();
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		if (!this.visible) {
			return;
		}
		GuiGraphics graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}

		int scrollbar = getScrollbarPosition();
		int scrollbarRight = scrollbar + 6;
		capYPosition();

		drawBackground(graphics);

		int rowX = this.x0 + this.width / 2 - getRowWidth() / 2 + 2;
		int rowY = this.y0 + 4 - (int) getScroll();

		if (getRight() > getLeft() && getBottom() > getTop()) {
			graphics.enableScissor(getLeft(), getTop(), getRight(), getBottom());
			try {
				if (getHeaderPadding() > 0) {
					drawHeader(graphics, rowX, rowY);
				}
				renderList(graphics, rowX, rowY, mouseX, mouseY, partialTicks);
			} finally {
				graphics.disableScissor();
			}
		}

		int maxScroll = getMaxScroll();
		if (drawDefaultBackground || Minecraft.getInstance().player == null || maxScroll > 0) {
			// Top and bottom fade ("Schatten" in the 1.15 code).
			graphics.fillGradient(RenderType.guiOverlay(), this.x0, this.y0, this.x1, this.y0 + 4, 0xFF000000, 0x00000000, 0);
			graphics.fillGradient(RenderType.guiOverlay(), this.x0, this.y1 - 4, this.x1, this.y1, 0x00000000, 0xFF000000, 0);
		}

		if (maxScroll > 0) {
			int listHeight = this.y1 - this.y0;
			int thumbHeight = (int) ((float) (listHeight * listHeight) / Math.max(1, getItemHeight()));
			thumbHeight = Mth.clamp(thumbHeight, 32, Math.max(32, listHeight - 8));
			int thumbY = (int) getScroll() * (listHeight - thumbHeight) / maxScroll + this.y0;
			if (thumbY < this.y0) {
				thumbY = this.y0;
			}

			graphics.fill(scrollbar, this.y0, scrollbarRight, this.y1, 0xFF000000);
			graphics.fill(scrollbar, thumbY, scrollbarRight, thumbY + thumbHeight, 0xFF808080);
			graphics.fill(scrollbar, thumbY, scrollbarRight - 1, thumbY + thumbHeight - 1, 0xFFC0C0C0);
		}

		getSelectedRow();
	}

	protected void drawBackground(GuiGraphics graphics) {
		int listWidth = getRight() - getLeft();
		int listHeight = getBottom() - getTop();

		if (backgroundTexture instanceof ResourceLocation) {
			GuiCompat.blitScaled(graphics, (ResourceLocation) backgroundTexture, getLeft(), getTop(), listWidth, listHeight,
					0.0F, 0.0F, this.backgroundWidth, this.backgroundHeight, this.backgroundWidth, this.backgroundHeight);
		} else if (backgroundTexture != null) {
			// Dynamic textures (the user's chat background image) are not ResourceLocations; keep the
			// 1.15 route through bindTexture + Gui#drawModalRectWithCustomSizedTexture.
			MinecraftFactory.getVars().bindTexture(backgroundTexture);
			Gui.drawModalRectWithCustomSizedTexture(getLeft(), getTop(), 0.0F, 0.0F, listWidth, listHeight,
					this.backgroundWidth, this.backgroundHeight);
		} else if (drawDefaultBackground || Minecraft.getInstance().player == null) {
			// 1.21.4 has no GuiGraphics#setColor: the 0.125 grey tint is passed to each draw call.
			GuiCompat.blitRepeating(graphics, Screen.MENU_BACKGROUND, getLeft(), getTop(), listWidth, listHeight,
					0, (int) getScroll(), 32, 32, GuiCompat.TINT_DARKENED);
		}
	}

	protected void renderList(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTicks) {
		if (leftbound) {
			x = getLeft() + 2;
		}

		synchronized (rows) {
			int count = Math.min(heightMap.size(), rows.size());
			for (int rowIndex = 0; rowIndex < count; ++rowIndex) {
				int newY = y + heightMap.get(rowIndex) + getHeaderPadding();
				int slotHeight = rows.get(rowIndex).getLineHeight() - 4;
				if ((newY > getBottom()) || (newY + slotHeight < getTop())) {
					continue;
				}
				if (isDrawSelection() && isSelectedItem(rowIndex)) {
					int x1, x2;
					if (leftbound) {
						x1 = getLeft();
						x2 = getLeft() + getRowWidth();
					} else {
						x1 = getLeft() + (getWidth() / 2 - getRowWidth() / 2);
						x2 = getLeft() + getWidth() / 2 + getRowWidth() / 2;
					}
					graphics.fill(x1, newY - 2, x2, newY + slotHeight + 2, 0xFF808080);
					graphics.fill(x1 + 1, newY - 1, x2 - 1, newY + slotHeight + 1, 0xFF000000);
				}
				renderItem(rowIndex, x, newY, slotHeight, mouseX, mouseY, partialTicks);
			}
		}
	}

	@Override
	public boolean callMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (isVisible() && button == 0 && this.hasSelected && mouseX > getScrollbarPosition() && mouseX < getScrollbarPosition() + 6) {
			if (mouseY < (double) this.y0) {
				scrollTo(0);
			} else if (mouseY > (double) this.y1) {
				scrollTo(getMaxScroll());
			} else {
				double maxScroll = getMaxScroll();
				if (maxScroll < 1.0D) {
					maxScroll = 1.0D;
				}

				int listHeight = this.y1 - this.y0;
				int thumbHeight = (int) ((float) (listHeight * listHeight) / Math.max(1, getItemHeight()));
				thumbHeight = Mth.clamp(thumbHeight, 32, Math.max(32, listHeight - 8));
				double scale = maxScroll / (double) Math.max(1, listHeight - thumbHeight);
				if (scale < 1.0D) {
					scale = 1.0D;
				}

				scrollTo((float) (getScroll() + deltaY * scale));
				capYPosition();
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean callMouseScrolled(double amount) {
		if (!isVisible() || !isMouseInList(this.mouseX, this.mouseY)) {
			return false;
		}
		scrollTo((float) (getScroll() - amount * this.itemHeight / 2.0D));
		return true;
	}

	@Override
	public void mouseClicked(int mouseX, int mouseY) {
		this.hasSelected = isMouseInList(mouseX, mouseY);

		E item = getHoverItem(mouseX, mouseY);
		if (item != null) {
			synchronized (rows) {
				int id = rows.indexOf(item);
				if (id >= 0) {
					setSelectedId(id);
					selectItem(id, 0, mouseX, mouseY);
				}
			}
		}

		if (this.hasSelected) {
			synchronized (rows) {
				for (int rowIndex = 0; rowIndex < heightMap.size() && rowIndex < rows.size(); ++rowIndex) {
					int newY = (int) (getTop() + heightMap.get(rowIndex) + getHeaderPadding() - getCurrentScroll());
					int slotHeight = rows.get(rowIndex).getLineHeight() - 4;
					if ((newY <= getBottom()) && (newY + slotHeight >= getTop())) {
						Row row = rows.get(rowIndex);
						if (row instanceof RowExtended) {
							IButton pressed = ((RowExtended) row).mousePressed(mouseX, mouseY);
							if (pressed != null) {
								if (selectedButton != null && pressed != selectedButton)
									selectedButton.mouseClicked(mouseX, mouseY);
								selectedButton = pressed;
								return;
							}
						}
					}
				}
			}
		}
	}

	/**
	 * @return x-coordinate of the scroll bar
	 */
	public int getScrollbarPosition() {
		return scrollX > 0 ? scrollX : this.width / 2 + 124;
	}

	@Override
	public void mouseReleased(int mouseX, int mouseY, int state) {
		if (this.selectedButton != null && state == 0) {
			this.selectedButton.callMouseReleased(mouseX, mouseY);
			this.selectedButton = null;
		}
	}

	@Override
	public void callHandleMouseInput() {
	}

	@Override
	public void scrollToBottom() {
		scrollTo(getMaxScroll());
	}

	@Override
	public float getCurrentScroll() {
		return (float) getScroll();
	}

	@Override
	public void scrollTo(float to) {
		this.scrollAmount = Mth.clamp(to, 0.0D, getMaxScroll());
	}

	protected double getScroll() {
		return this.scrollAmount;
	}

	protected void scroll(int amount) {
		this.scrollAmount += amount;
	}

	public int getMaxScroll() {
		return Math.max(0, getItemHeight() - (this.y1 - this.y0 - 4));
	}

	protected void capYPosition() {
		this.scrollAmount = Mth.clamp(this.scrollAmount, 0.0D, (double) getMaxScroll());
	}

	@Override
	public int callGetContentHeight() {
		int height = bottomPadding + (getHeaderPadding() > 0 ? (getHeaderPadding() + 8) : 0);
		synchronized (rows) {
			List<E> snapshot = Lists.newArrayList(rows);
			for (Row row : snapshot) {
				height += row.getLineHeight();
			}
		}
		return height;
	}

	@Override
	public void calculateHeightMap() {
		heightMap.clear();

		int curHeight = getHeaderPadding();
		synchronized (rows) {
			List<E> snapshot = Lists.newArrayList(rows);
			for (Row row : snapshot) {
				heightMap.add(curHeight);
				curHeight += row.getLineHeight();
			}
		}
	}

	@Override
	public int getSelectedId() {
		synchronized (rows) {
			if (selected < 0 || selected >= rows.size())
				selected = 0;
		}
		return selected;
	}

	@Override
	public int setSelectedId(int selected) {
		if (selected < 0 || selected >= Math.max(1, rows.size()))
			selected = 0;
		this.selected = selected;
		return selected;
	}

	@Override
	public E getSelectedRow() {
		synchronized (rows) {
			if (rows.isEmpty())
				return null;
			if (selected < 0) {
				selected = 0;
				return rows.get(0);
			}
			while (selected >= rows.size()) {
				selected--;
			}
			return rows.get(selected);
		}
	}

	@Override
	public int getWidth() {
		return this.width;
	}

	@Override
	public void setWidth(int width) {
		this.width = width;
	}

	@Override
	public int getHeight() {
		return this.height;
	}

	@Override
	public void setHeight(int height) {
		this.height = height;
	}

	@Override
	public int getHeight(int id) {
		if (id < 0 || id >= heightMap.size())
			return 0;
		return heightMap.get(id);
	}

	@Override
	public int getTop() {
		return this.y0;
	}

	@Override
	public void setTop(int top) {
		this.y0 = top;
	}

	@Override
	public int getBottom() {
		return this.y1;
	}

	@Override
	public void setBottom(int bottom) {
		this.y1 = bottom;
	}

	@Override
	public int getLeft() {
		return this.x0;
	}

	@Override
	public void setLeft(int left) {
		this.x0 = left;
	}

	@Override
	public int getRight() {
		return this.x1;
	}

	@Override
	public void setRight(int right) {
		this.x1 = right;
	}

	@Override
	public int getScrollX() {
		return this.scrollX;
	}

	@Override
	public void setScrollX(int scrollX) {
		this.scrollX = scrollX;
	}

	@Override
	public boolean isLeftbound() {
		return this.leftbound;
	}

	@Override
	public void setLeftbound(boolean leftbound) {
		this.leftbound = leftbound;
	}

	@Override
	public boolean isDrawSelection() {
		return this.renderSelection;
	}

	@Override
	public void setDrawSelection(boolean drawSelection) {
		this.renderSelection = drawSelection;
	}

	@Override
	public int getHeaderPadding() {
		return this.headerHeight;
	}

	@Override
	public void callSetHeaderPadding(int headerPadding) {
		this.headerHeight = Math.max(0, headerPadding);
	}

	@Override
	public String getHeader() {
		return this.header;
	}

	@Override
	public void setHeader(String header) {
		this.header = header;
	}

	protected void drawHeader(GuiGraphics graphics, int x, int y) {
		if (this.header == null) {
			return;
		}
		String clean = stripFormatting(this.header);
		if (clean.isEmpty()) {
			return;
		}
		Component text = Component.literal(clean).withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BOLD);
		graphics.drawCenteredString(Minecraft.getInstance().font, text,
				getLeft() + (getRight() - getLeft()) / 2, Math.min(getTop() + 5, y), 0xFFFFFF);
	}

	private static String stripFormatting(String input) {
		StringBuilder result = new StringBuilder(input.length());
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '\u00a7' && i + 1 < input.length()) {
				i++;
				continue;
			}
			result.append(c);
		}
		return result.toString();
	}

	@Override
	public int getBottomPadding() {
		return this.bottomPadding;
	}

	@Override
	public void setBottomPadding(int bottomPadding) {
		this.bottomPadding = bottomPadding;
	}

	@Override
	public E getHoverItem(int mouseX, int mouseY) {
		calculateHeightMap();
		int x1, x2;
		if (leftbound) {
			x1 = getLeft();
			x2 = getLeft() + getRowWidth();
		} else {
			x1 = getLeft() + (getWidth() / 2 - getRowWidth() / 2);
			x2 = getLeft() + getWidth() / 2 + getRowWidth() / 2;
		}
		if (mouseX >= x1 && mouseX <= x2) {
			synchronized (rows) {
				int count = Math.min(heightMap.size(), rows.size());
				for (int i = 0; i < count; i++) {
					int y = heightMap.get(i) + getTop() + getHeaderPadding() - (int) getScroll();
					E element = rows.get(i);
					if (mouseY >= y && mouseY <= y + element.getLineHeight()) {
						return element;
					}
				}
			}
		}
		return null;
	}

	@Override
	public boolean isDrawDefaultBackground() {
		return this.drawDefaultBackground;
	}

	@Override
	public void setDrawDefaultBackground(boolean drawDefaultBackground) {
		this.drawDefaultBackground = drawDefaultBackground;
	}

	@Override
	public Object getBackgroundTexture() {
		return this.backgroundTexture;
	}

	@Override
	public void setBackgroundTexture(Object backgroundTexture, int imageWidth, int imageHeight) {
		this.backgroundTexture = backgroundTexture;

		if (backgroundTexture != null) {
			double w = imageWidth;
			double h = imageHeight;
			int listWidth = getRight() - getLeft();
			int listHeight = getBottom() - getTop();

			while (w > listWidth && h > listHeight) {
				w -= 1;
				h -= h / w;
			}
			while (w < listWidth || h < listHeight) {
				w += 1;
				h += h / w;
			}
			this.backgroundWidth = (int) w;
			this.backgroundHeight = (int) h;
		}
	}

	@Override
	public List<E> getRows() {
		return this.rows;
	}

	public boolean isVisible() {
		return this.visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	protected boolean isMouseInList(int mouseX, int mouseY) {
		return mouseX >= getLeft() && mouseX <= getRight() && mouseY >= getTop() && mouseY <= getBottom();
	}
}
