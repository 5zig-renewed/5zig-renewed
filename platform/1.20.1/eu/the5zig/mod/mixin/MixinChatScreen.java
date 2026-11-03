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

import eu.the5zig.mod.BytecodeHook;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.fabric.FiveZigFabric;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port of 1.15's {@code MixinGuiChat}: the second (5zig) chat gets the hover, scroll and key
 * events of the vanilla chat screen.
 */
@Mixin(ChatScreen.class)
public abstract class MixinChatScreen {

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"))
	private void the5zig$drawComponentHover(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
		if (!FiveZigFabric.isCoreReady()) {
			return;
		}
		try {
			The5zigMod.getVars().get2ndChat().drawComponentHover(mouseX, mouseY);
		} catch (Throwable ignored) {
			// The second chat is an optional feature.
		}
	}

	@Inject(method = "keyPressed(III)Z", at = @At("HEAD"))
	private void the5zig$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> callbackInfo) {
		if (!FiveZigFabric.isCoreReady()) {
			return;
		}
		try {
			The5zigMod.getVars().get2ndChat().keyTyped(keyCode);
		} catch (Throwable ignored) {
			// The second chat is an optional feature.
		}
	}

	/**
	 * 1.15 cancelled with {@code false} so that a click consumed by the 5zig chat never reaches the
	 * vanilla chat or the screen below it; keep that behaviour.
	 */
	@Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
	private void the5zig$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> callbackInfo) {
		if (!FiveZigFabric.isCoreReady()) {
			return;
		}
		try {
			if (BytecodeHook.onChatMouseClicked(mouseX, mouseY, button)) {
				callbackInfo.setReturnValue(false);
			}
		} catch (Throwable ignored) {
			// Let vanilla handle the click if the 5zig chat is broken.
		}
	}
}
