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

import eu.the5zig.mod.platform.HudFrame;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.gui.overlay.ExtendedGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD bracket for NeoForge 1.20.2 - 1.20.6.
 * <p>
 * NeoForge instantiates {@code net.neoforged.neoforge.client.gui.overlay.ExtendedGui}, which
 * {@code extends Gui} and overrides {@code Gui#render}. The platform's {@code @Mixin(Gui)} hook
 * therefore never runs on this loader — the HUD is silently absent while everything else works.
 * Bracket the overriding method instead and delegate to the same {@link HudFrame} logic the
 * Fabric and Forge builds use.
 */
@Mixin(ExtendedGui.class)
public abstract class MixinExtendedGui {

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("HEAD"))
	private void the5zig$beginFrame(GuiGraphics graphics, float partialTick, CallbackInfo callbackInfo) {
		HudFrame.begin(graphics);
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("RETURN"))
	private void the5zig$endFrame(GuiGraphics graphics, float partialTick, CallbackInfo callbackInfo) {
		HudFrame.end();
	}
}
