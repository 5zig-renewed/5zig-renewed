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

import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.util.CoreState;
import eu.the5zig.mod.util.CombatRangeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-loop hooks that used to live in 1.15's {@code MixinMinecraft}.
 * <p>
 * Ticking is handled by {@code ClientTickEvents} in {@code FiveZigFabric}; the per-click counters
 * and the combat-range bookkeeping have no Fabric event, so they stay mixins.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

	/**
	 * 1.15 cleared the text field registry whenever a screen was opened. The mod itself is
	 * initialised from {@code ClientLifecycleEvents}, not from here, so that a failure can never
	 * take down {@code Minecraft#setScreen}.
	 */
	@Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
	private void the5zig$setScreen(Screen screen, CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			The5zigMod.getKeybindingManager().flushTextfields();
		} catch (Throwable ignored) {
			// Text field bookkeeping is best effort.
		}
	}

	/**
	 * Left click: CPS counter and the range of the last attack.
	 * <p>
	 * 1.15 injected into {@code clickMouse}; 1.16.2 renamed that to {@code startAttack}, which is
	 * still {@code void} here (it only gains a {@code boolean} return in 1.19).
	 */
	@Inject(method = "startAttack()V", at = @At("HEAD"))
	private void the5zig$startAttack(CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			CombatRangeUtil.lastRange = CombatRangeUtil.lastPoint;
			CombatRangeUtil.lastAttack = System.currentTimeMillis();
			The5zigMod.getDataManager().getCpsManager().getLeftClickCounter().incrementCount();
		} catch (Throwable ignored) {
			// Stats only.
		}
	}

	/**
	 * Right click: CPS counter. 1.15 hooked {@code processKeyBinds} right before
	 * {@code rightClickMouse()}; 1.16.2 splits that into {@code startUseItem}.
	 */
	@Inject(method = "startUseItem()V", at = @At("HEAD"))
	private void the5zig$startUseItem(CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			The5zigMod.getDataManager().getCpsManager().getRightClickCounter().incrementCount();
		} catch (Throwable ignored) {
			// Stats only.
		}
	}
}
