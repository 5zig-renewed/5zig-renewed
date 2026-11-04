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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port of 1.15's {@code MixinAbstractClientPlayer}: the "static FOV" option and 5zig's capes.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {

	/**
	 * 1.15 injected into {@code getFovModifier}; 1.16.5 calls it {@code getFieldOfViewModifier}.
	 */
	@Inject(method = "getFieldOfViewModifier()F", at = @At("HEAD"), cancellable = true)
	private void the5zig$staticFov(CallbackInfoReturnable<Float> callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			if (!The5zigMod.getConfig().getBool("staticFov")) {
				return;
			}
			AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
			callbackInfo.setReturnValue(the5zig$computeStaticFov(player));
		} catch (Throwable ignored) {
			// Keep the vanilla value.
		}
	}

	/**
	 * Replaces {@code AbstractClientPlayer#getCloakTextureLocation} with the cape the 5zig resource
	 * manager resolved for this player, if any.
	 */
	@Inject(method = "getCloakTextureLocation()Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true)
	private void the5zig$cape(CallbackInfoReturnable<ResourceLocation> callbackInfo) {
		if (!CoreState.isReady() || The5zigMod.getVars().getResourceManager() == null) {
			return;
		}
		try {
			Object cape = The5zigMod.getVars().getResourceManager().getCapeLocation((Object) this);
			if (cape instanceof ResourceLocation) {
				callbackInfo.setReturnValue((ResourceLocation) cape);
			}
		} catch (Throwable ignored) {
			// Fall back to the vanilla cape.
		}
	}

	@Unique
	private static float the5zig$computeStaticFov(AbstractClientPlayer player) {
		Abilities abilities = player.abilities;
		float modifier = 1.0F;
		if (abilities.flying) {
			modifier *= 1.1F;
		}
		double staticSpeed = 0.10000000149011612D;
		if (player.isSprinting()) {
			staticSpeed = 0.13000000312924387D;
		}
		modifier *= (float) ((staticSpeed / abilities.getWalkingSpeed() + 1.0D) / 2.0D);
		if (abilities.getWalkingSpeed() == 0.0F || Float.isNaN(modifier) || Float.isInfinite(modifier)) {
			modifier = 1.0F;
		}
		if (player.getUseItem().getItem() == Items.BOW) {
			float useSeconds = player.getTicksUsingItem() / 20.0F;
			if (useSeconds > 1.0F) {
				useSeconds = 1.0F;
			} else {
				useSeconds *= useSeconds;
			}
			modifier *= 1.0F - useSeconds * 0.15F;
		}
		return modifier;
	}
}
