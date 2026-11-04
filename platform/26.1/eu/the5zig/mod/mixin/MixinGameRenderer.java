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

import eu.the5zig.mod.util.CombatRangeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the distance from the camera to the block or entity under the crosshair.
 * <p>
 * 1.15 injected at the {@code Minecraft.pointedEntity} field write inside {@code getMouseOver}.
 * 26.x moved the picking code from {@code GameRenderer#pick} onto {@code Minecraft#pick}, which is
 * where this hook now lives (a RETURN hook is far less brittle than pinning an instruction inside
 * the picking code).
 */
@Mixin(Minecraft.class)
public abstract class MixinGameRenderer {

	@Inject(method = "pick(F)V", at = @At("RETURN"))
	private void the5zig$recordCombatRange(float partialTick, CallbackInfo callbackInfo) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getCameraEntity() == null) {
			return;
		}
		HitResult hitResult = minecraft.hitResult;
		if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
			return;
		}
		try {
			Vec3 eyePosition = minecraft.getCameraEntity().getEyePosition(partialTick);
			CombatRangeUtil.lastPoint = hitResult.getLocation().distanceTo(eyePosition);
		} catch (Throwable ignored) {
			// Combat range is an optional module item.
		}
	}
}
