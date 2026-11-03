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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Draws the 5zig notifications as vanilla toasts.
 * <p>
 * 1.21.9 reshaped the {@link Toast} contract: instead of {@code render(...)} returning the
 * visibility, the toast now reports {@link Toast#getWantedVisibility()} and decides it in
 * {@link Toast#update(ToastManager, long)} from the manager's monotonic clock, while
 * {@code render(GuiGraphics, Font, long)} only draws. The background is a GUI-atlas sprite
 * ({@code minecraft:toast/system}) because {@code textures/gui/toasts.png} no longer exists.
 */
public class Overlay implements IOverlay {

	private static final ResourceLocation TOAST_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("toast/system");
	private static final int TOAST_WIDTH = 160;
	private static final int TOAST_HEIGHT = 32;
	private static final int TEXT_X = 18;
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
		List<String> split = MinecraftFactory.getVars().splitStringToWidth(message, MinecraftFactory.getClassProxyCallback().getOverlayTexture() == 2 ? 142 : 150);
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

		/** Set when the content changed so that the display timer restarts on the next update. */
		private boolean changed = true;
		private long lastChanged;
		private final Object uniqueReference;
		private String title;
		private String subTitle;
		private Visibility wantedVisibility = Visibility.HIDE;

		private FiveZigToast(Object uniqueReference, String title, String subTitle) {
			this.uniqueReference = uniqueReference;
			this.title = title;
			this.subTitle = subTitle;
		}

		private void reset(String title, String subTitle) {
			this.title = title;
			this.subTitle = subTitle;
			this.changed = true;
		}

		@Override
		public Visibility getWantedVisibility() {
			return wantedVisibility;
		}

		@Override
		public void update(ToastManager toastManager, long timeSinceLastVisible) {
			if (changed) {
				lastChanged = timeSinceLastVisible;
				changed = false;
			}
			double displayTime = DISPLAY_TIME * toastManager.getNotificationDisplayTimeMultiplier();
			wantedVisibility = timeSinceLastVisible - lastChanged < displayTime ? Visibility.SHOW : Visibility.HIDE;
		}

		@Override
		public void render(GuiGraphics graphics, Font font, long timeSinceLastVisible) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TOAST_BACKGROUND_SPRITE, 0, 0, TOAST_WIDTH, TOAST_HEIGHT);
			if (subTitle == null) {
				graphics.drawString(font, title, TEXT_X, (TOAST_HEIGHT - font.lineHeight) / 2, 0xFFFFFF, false);
			} else {
				graphics.drawString(font, title, TEXT_X, 7, 0xFFFFFF, false);
				graphics.drawString(font, subTitle, TEXT_X, 18, 0xFFFFFF, false);
			}
		}

		@Override
		public Object getToken() {
			return uniqueReference == null ? NO_TOKEN : uniqueReference;
		}

		public static void create(String title, String subTitle, Object uniqueReference) {
			ToastManager toastManager = Minecraft.getInstance().getToastManager();

			if (uniqueReference == null) {
				toastManager.addToast(new FiveZigToast(null, title, subTitle));
			} else {
				FiveZigToast toast = toastManager.getToast(FiveZigToast.class, uniqueReference);
				if (toast == null) {
					toastManager.addToast(new FiveZigToast(uniqueReference, title, subTitle));
				} else {
					toast.reset(title, subTitle);
				}
			}
		}
	}
}
