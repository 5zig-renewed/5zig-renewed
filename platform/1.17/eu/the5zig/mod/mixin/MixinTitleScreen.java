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

package eu.the5zig.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.util.CoreState;
import eu.the5zig.mod.Version;
import eu.the5zig.mod.util.RenderContext;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of 1.15's {@code MixinGuiMainMenu#drawScreen}: prints the mod version in the top left corner
 * of the main menu.
 * <p>
 * The "last server" button of the 1.15 build is not ported yet: 1.17 builds its button grid in
 * {@code TitleScreen#init} through {@code Button.builder(...)}, which needs the platform's
 * {@code ButtonFactory} first.
 */
@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen {

	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V", at = @At("TAIL"))
	private void the5zig$drawVersion(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		boolean owned = !RenderContext.isAvailable();
		if (owned) {
			RenderContext.set(poseStack);
		}
		try {
			The5zigMod.getVars().drawString(Version.getVersionDisplay(), 2, 2);
		} catch (Throwable ignored) {
			// Cosmetic only.
		} finally {
			if (owned) {
				RenderContext.clear();
			}
		}
	}
}
