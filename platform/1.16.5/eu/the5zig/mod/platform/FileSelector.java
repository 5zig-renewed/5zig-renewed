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
import com.mojang.blaze3d.platform.Window;
import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.gui.elements.IFileSelector;
import eu.the5zig.mod.util.RenderContext;
import eu.the5zig.util.Callback;
import net.minecraft.client.Minecraft;
import eu.the5zig.mod.util.GuiContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import org.apache.commons.io.FilenameUtils;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by 5zig.
 * All rights reserved © 2015
 * <p>
 * 1.16.5 port. The 1.15 implementation drew its background, selection and scrollbar through the
 * global tessellator; everything here is {@link GuiContext#fill}/{@link GuiContext#blitRepeating}
 * on the frame's {@link RenderContext}. Input could use {@code org.lwjgl.input.Mouse} in 1.15; GLFW
 * has no global event queue any more, so the current state is polled from {@link net.minecraft.client.MouseHandler}
 * (the core calls {@link #handleMouseInput()} once per frame from the file selector screen) and scroll
 * deltas are fed in through {@link #mouseScrolled(double)}.
 */
public class FileSelector implements IFileSelector {

	private final FileSystemView fsv = FileSystemView.getFileSystemView();
	private final Callback<File> callback;

	private List<String> allowedExtensions = new ArrayList<>();

	private File currentDir;
	private int selectedFile;
	private List<File> files = Lists.newArrayList();

	private int width, height;

	private int left;
	private int right;
	private int top;
	private int bottom;
	private int columnCount;

	private int mouseX, mouseY;

	private final int selectionBoxWidth = 100;

	private float amountScrolled;

	private float initialClickY = -1, scrollMultiplier = -1;
	private long lastClicked;

	private boolean leftMouseDown;
	private double pendingScroll;

	public FileSelector(File currentDir, int width, int height, int left, int right, int top, int bottom, Callback<File> callback) {
		updateDir(currentDir);
		this.width = width;
		this.height = height;
		this.left = left;
		this.right = right;
		this.top = top;
		this.bottom = bottom;
		this.callback = callback;

		columnCount = (int) Math.floor((double) (right - left) / (double) selectionBoxWidth);
		if (columnCount < 1) {
			columnCount = 1;
		}
	}

	@Override
	public void goUp() {
		if (currentDir == null)
			return;
		File parent = currentDir.getParentFile();
		if (parent == null) {
			currentDir = null;
			files.clear();
			File[] a = File.listRoots();
			if (a == null)
				return;
			for (File file : a) {
				if (!fsv.isDrive(file) || fsv.getSystemDisplayName(file).isEmpty())
					continue;
				if (!isExtensionAllowed(file)) continue;
				files.add(file);
			}
			selectedFile = files.isEmpty() ? -1 : 0;
			return;
		}
		updateDir(parent);
	}

	@Override
	public void updateDir(File dir) {
		currentDir = dir;
		files.clear();
		if (dir == null) {
			File[] a = File.listRoots();
			if (a == null)
				return;
			for (File file : a) {
				if (!fsv.isDrive(file) || fsv.getSystemDisplayName(file).isEmpty())
					continue;
				if (!isExtensionAllowed(file)) continue;
				files.add(file);
			}
			selectedFile = files.isEmpty() ? -1 : 0;
			return;
		}
		File[] a = dir.listFiles();
		if (a == null)
			return;
		for (File file : a) {
			if (file.isHidden())
				continue;
			if (!isExtensionAllowed(file)) continue;
			files.add(file);
		}
		selectedFile = files.isEmpty() ? -1 : 0;
	}

	@Override
	public void setAllowedExtensions(String... extensions) {
		allowedExtensions.addAll(java.util.Arrays.asList(extensions));
		updateDir(currentDir);
	}

	private boolean isExtensionAllowed(File file) {
		if (allowedExtensions.size() == 0) return true;
		if (file.isDirectory()) return true;
		String ext = FilenameUtils.getExtension(file.getName());
		return allowedExtensions.contains(ext);
	}

	@Override
	public void draw(int mouseX, int mouseY, float partialTicks) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;

		GuiContext graphics = RenderContext.get();
		if (graphics == null) {
			return;
		}

		int scrollX0 = getScrollBarX();
		int scrollX1 = scrollX0 + 6;
		bindAmountScrolled();

		// background
		graphics.setColor(0.125F, 0.125F, 0.125F, 1.0F);
		graphics.blitRepeating(Screen.BACKGROUND_LOCATION, left, top, right - left, bottom - top,
				0, (int) amountScrolled, 32, 32);
		graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

		int selectionX0 = left;
		int selectionY0 = top + 4 - (int) amountScrolled;
		if (right > left && bottom > top) {
			graphics.enableScissor(left, top, right, bottom);
			drawSelectionBox(graphics, selectionX0, selectionY0, mouseX, mouseY);
			graphics.disableScissor();
		}

		overlayBackground(graphics, 0, top, 255, 255);
		overlayBackground(graphics, bottom, height, 255, 255);

		// Schatten
		graphics.fillGradient(left, top, right, top + 4, 0xFF000000, 0x00000000, 0);
		graphics.fillGradient(left, bottom - 4, right, bottom, 0x00000000, 0xFF000000, 0);

		int maxScroll = getMaxScroll();
		if (maxScroll > 0) {
			int thumbHeight = (bottom - top) * (bottom - top) / Math.max(1, getContentHeight());
			thumbHeight = Mth.clamp(thumbHeight, 32, Math.max(32, bottom - top - 8));
			int thumbY = (int) amountScrolled * (bottom - top - thumbHeight) / maxScroll + top;
			if (thumbY < top) {
				thumbY = top;
			}

			graphics.fill(scrollX0, top, scrollX1, bottom, 0xFF000000);
			graphics.fill(scrollX0, thumbY, scrollX1, thumbY + thumbHeight, 0xFF808080);
			graphics.fill(scrollX0, thumbY, scrollX1 - 1, thumbY + thumbHeight - 1, 0xFFC0C0C0);
		}

		// draw hover if slot text is too long
		int x = mouseX - left;
		int y = mouseY - top + (int) amountScrolled - 4;
		int idX = x / selectionBoxWidth;
		int idY = y / getSlotHeight();
		int id = idX + idY * columnCount;
		if (id < files.size() && id >= 0 && mouseX >= 0 && mouseY >= 0 && idX < columnCount) {
			elementHovered(id, mouseX, mouseY);
		}
	}

	protected void drawSelectionBox(GuiContext graphics, int x, int y, int mouseX, int mouseY) {
		int rows = (int) Math.ceil((double) files.size() / (double) columnCount);

		for (int i = 0; i < rows; ++i) {
			int ny = y + i * getSlotHeight();
			int padding = getSlotHeight() - 4;
			for (int j = 0; j < columnCount; j++) {
				int nx = x + j * selectionBoxWidth;
				int nx1 = nx + selectionBoxWidth;

				int id = j + i * columnCount;
				if (isSelected(id)) {
					graphics.fill(nx, ny - 2, nx1, ny + padding + 2, 0xFF808080);
					graphics.fill(nx + 1, ny - 1, nx1 - 1, ny + padding + 1, 0xFF000000);
				}
				drawSlot(id, nx, ny, padding, mouseX, mouseY);
			}
		}
	}

	protected void drawSlot(int id, int x, int y, int padding, int mouseX, int mouseY) {
		File file = getFile(id);
		if (file == null)
			return;
		String name = file.getName();
		if (currentDir == null) {
			name = fsv.getSystemDisplayName(file);
		}
		MinecraftFactory.getVars().drawString(MinecraftFactory.getVars().shortenToWidth(name, selectionBoxWidth - 18), x + 2, y + 2);
	}

	public boolean isSelected(int id) {
		return selectedFile == id;
	}

	public File getFile(int i) {
		if (i < 0 || i >= files.size())
			return null;
		return files.get(i);
	}

	@Override
	public File getSelectedFile() {
		return getFile(selectedFile);
	}

	protected void overlayBackground(GuiContext graphics, int y0, int y1, int alphaTop, int alphaBottom) {
		int base = 0x404040;
		graphics.fillGradient(left, y0, right, y1,
				(alphaTop << 24) | base, (alphaBottom << 24) | base, 0);
	}

	public int getContentHeight() {
		return (int) Math.ceil((double) files.size() / (double) columnCount) * getSlotHeight();
	}

	public int getSlotHeight() {
		return 18;
	}

	protected void bindAmountScrolled() {
		amountScrolled = Mth.clamp(amountScrolled, 0.0F, (float) getMaxScroll());
	}

	/**
	 * @return the amount of pixels the content can be scrolled, named {@code m()} in the 1.15 source.
	 */
	public int getMaxScroll() {
		return Math.max(0, getContentHeight() - (this.bottom - this.top - 4));
	}

	public boolean isMouseWithinSlotBounds() {
		return mouseY >= top && mouseY <= bottom && mouseX >= left && mouseX <= right;
	}

	public int getScrollBarX() {
		return right - 10;
	}

	/**
	 * Feeds a scroll wheel delta (positive = wheel up) into the selector. The 1.15 implementation read
	 * {@code Mouse.getEventDWheel()}; a 1.16.5 screen has to forward {@code mouseScrolled} here.
	 */
	public void mouseScrolled(double delta) {
		this.pendingScroll -= delta;
	}

	@Override
	public void handleMouseInput() {
		Minecraft minecraft = Minecraft.getInstance();
		boolean leftDown = false;
		if (minecraft.mouseHandler != null && minecraft.getWindow() != null) {
			Window window = minecraft.getWindow();
			double scaleX = (double) window.getGuiScaledWidth() / (double) Math.max(1, window.getScreenWidth());
			double scaleY = (double) window.getGuiScaledHeight() / (double) Math.max(1, window.getScreenHeight());
			this.mouseX = (int) (minecraft.mouseHandler.xpos() * scaleX);
			this.mouseY = (int) (minecraft.mouseHandler.ypos() * scaleY);
			leftDown = minecraft.mouseHandler.isLeftPressed();
		}

		if (leftDown && !leftMouseDown) {
			mousePressed(mouseX, mouseY);
		} else if (leftDown) {
			mouseDragged(mouseX, mouseY);
		} else {
			initialClickY = -1;
		}
		leftMouseDown = leftDown;

		if (pendingScroll != 0) {
			amountScrolled += (float) (pendingScroll * getSlotHeight() / 2);
			pendingScroll = 0;
			bindAmountScrolled();
		}
	}

	private void mousePressed(int mouseX, int mouseY) {
		if (!isMouseWithinSlotBounds())
			return;

		int x = mouseX - left;
		int y = mouseY - top + (int) amountScrolled - 4;
		int idY = y / getSlotHeight();
		int idX = x / selectionBoxWidth;
		int id = idX + idY * columnCount;
		boolean validSlot = id < files.size() && id >= 0 && x >= 0 && y >= 0 && idX < columnCount;

		if (validSlot) {
			boolean doubleClick = id == selectedFile && MinecraftFactory.getVars().getSystemTime() - lastClicked < 250L;
			this.selectedFile = id;
			elementClicked(id, doubleClick, mouseX, mouseY);
			this.lastClicked = MinecraftFactory.getVars().getSystemTime();
		}

		if (mouseX >= getScrollBarX() && mouseX <= getScrollBarX() + 6) {
			scrollMultiplier = -1.0F;
			int maxScroll = getMaxScroll();
			if (maxScroll < 1) {
				maxScroll = 1;
			}
			int thumbHeight = (bottom - top) * (bottom - top) / Math.max(1, getContentHeight());
			thumbHeight = Mth.clamp(thumbHeight, 32, Math.max(32, bottom - top - 8));
			scrollMultiplier /= (float) (bottom - top - thumbHeight) / (float) maxScroll;
		} else {
			scrollMultiplier = 1.0F;
		}

		initialClickY = validSlot ? mouseY : -2;
	}

	private void mouseDragged(int mouseX, int mouseY) {
		if (initialClickY >= 0) {
			this.amountScrolled -= (mouseY - initialClickY) * scrollMultiplier;
			bindAmountScrolled();
			initialClickY = mouseY;
		}
	}

	protected void elementClicked(int id, boolean doubleClick, int mouseX, int mouseY) {
		if (doubleClick && getSelectedFile() != null && getSelectedFile().isDirectory()) {
			updateDir(getSelectedFile());
		} else if (doubleClick && getSelectedFile() != null) {
			callback.call(getSelectedFile());
		}
	}

	protected void elementHovered(int id, int mouseX, int mouseY) {
		File file = getFile(id);
		if (file == null)
			return;
		String name = file.getName();
		if (currentDir == null) {
			name = fsv.getSystemDisplayName(file);
		}
		if (MinecraftFactory.getVars().getStringWidth(name) > selectionBoxWidth - 18 - MinecraftFactory.getVars().getStringWidth("...")) {
			MinecraftFactory.getVars().getCurrentScreen().drawHoveringText(MinecraftFactory.getVars().splitStringToWidth(name, 200), mouseX, mouseY);
		}
	}

	@Override
	public File getCurrentDir() {
		return currentDir;
	}
}
