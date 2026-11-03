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

import eu.the5zig.mod.MinecraftFactory;
import eu.the5zig.mod.gui.IOverlay;
import eu.the5zig.util.minecraft.ChatColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

import java.util.List;

/**
 * Draws the 5zig notifications as vanilla toasts.
 * <p>
 * 26.x: toasts are no longer rendered by the toast itself ({@code Toast#render} is gone); the toast
 * manager calls {@link Toast#extractRenderState(GuiGraphicsExtractor, Font, long)} and the toast
 * reports whether it still wants to be shown through {@link Toast#getWantedVisibility()}. The
 * 160x32 toast background sprite is also gone from the GUI atlas, so the panel is drawn with plain
 * fills instead.
 */
public class Overlay implements IOverlay {

	private static final int TOAST_WIDTH = 160;
	private static final int TOAST_HEIGHT = 32;
	private static final int TEXT_X = 8;
	private static final long DISPLAY_TIME = 5000L;

	public Overlay() {
	}

	public static void updateOverlayCount(int count) {
	}

	public static void renderAll() {
	}

	@Override
	public void displayMessage(String title, String subtitle) {
		FiveZigToast.create(ChatColor.YELLOW + title, subtitle, null);
	}

	@Override
	public void displayMessage(String title, String subtitle, Object uniqueReference) {
		FiveZigToast.create(ChatColor.YELLOW + title, subtitle, uniqueReference);
	}

	@Override
	public void displayMessage(String message) {
		displayMessage("The 5zig Mod", message);
	}

	@Override
	public void displayMessage(String message, Object uniqueReference) {
		displayMessage("The 5zig Mod", message, uniqueReference);
	}

	@Override
	public void displayMessageAndSplit(String message) {
		displayMessageAndSplit(message, null);
	}

	@Override
	public void displayMessageAndSplit(String message, Object uniqueReference) {
		List<String> split = MinecraftFactory.getVars().splitStringToWidth(message, 150);
		String title = null;
		String subTitle = null;
		for (int i = 0; i < split.size(); i++) {
			if (i == 0)
				title = split.get(0);
			if (i == 1)
				subTitle = split.get(1);
		}
		displayMessage(title == null ? "" : title, subTitle, uniqueReference);
	}

	private static final class FiveZigToast implements Toast {

		private boolean updateTime = true;
		private long startedTime;
		private final Object uniqueReference;
		private String title;
		private String subTitle;

		private FiveZigToast(Object uniqueReference, String title, String subTitle) {
			this.uniqueReference = uniqueReference;
			this.title = title;
			this.subTitle = subTitle;
		}

		private void update(String title, String subTitle) {
			this.title = title;
			this.subTitle = subTitle;
			this.startedTime = System.currentTimeMillis();
			this.updateTime = true;
		}

		@Override
		public Visibility getWantedVisibility() {
			return System.currentTimeMillis() - startedTime < DISPLAY_TIME ? Visibility.SHOW : Visibility.HIDE;
		}

		@Override
		public void update(ToastManager toastManager, long timeSinceLastVisible) {
			if (updateTime) {
				startedTime = System.currentTimeMillis();
				updateTime = false;
			}
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long timeSinceLastVisible) {
			graphics.fill(0, 0, TOAST_WIDTH, TOAST_HEIGHT, 0xC010131A);
			graphics.outline(0, 0, TOAST_WIDTH, TOAST_HEIGHT, 0x33FFFFFF);
			if (subTitle == null) {
				graphics.text(font, title, TEXT_X, (TOAST_HEIGHT - font.lineHeight) / 2, 0xFFFFFF, false);
			} else {
				graphics.text(font, title, TEXT_X, 7, 0xFFFFFF, false);
				graphics.text(font, subTitle, TEXT_X, 18, 0xFFFFFF, false);
			}
		}

		@Override
		public Object getToken() {
			return uniqueReference == null ? NO_TOKEN : uniqueReference;
		}

		public static void create(String title, String subTitle, Object uniqueReference) {
			Minecraft minecraft = Minecraft.getInstance();
			ToastManager toastManager = minecraft == null ? null : minecraft.getToastManager();
			if (toastManager == null) {
				return;
			}

			if (uniqueReference == null) {
				toastManager.addToast(new FiveZigToast(null, title, subTitle));
			} else {
				FiveZigToast toast = toastManager.getToast(FiveZigToast.class, uniqueReference);
				if (toast == null) {
					toastManager.addToast(new FiveZigToast(uniqueReference, title, subTitle));
				} else {
					toast.update(title, subTitle);
				}
			}
		}
	}
}
