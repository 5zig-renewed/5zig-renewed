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
import net.minecraft.client.resources.PlayerSkin;
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
	 * 1.15 injected into {@code getFovModifier}; 1.20.1 calls it {@code getFieldOfViewModifier}.
	 */
	@Inject(method = "getFieldOfViewModifier(ZF)F", at = @At("HEAD"), cancellable = true)
	private void the5zig$staticFov(boolean scoping, float partialTick, CallbackInfoReturnable<Float> callbackInfo) {
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
	 * Replaces the 5zig cape.
	 * <p>
	 * 1.20.1 could override {@code AbstractClientPlayer#getCloakTextureLocation}; 1.20.5+ folded the
	 * cape into the {@link PlayerSkin} record returned by {@code getSkin()}, so the resolved cape is
	 * spliced into that record instead.
	 */
	@Inject(method = "getSkin()Lnet/minecraft/client/resources/PlayerSkin;", at = @At("RETURN"), cancellable = true)
	private void the5zig$cape(CallbackInfoReturnable<PlayerSkin> callbackInfo) {
		if (!CoreState.isReady() || The5zigMod.getVars().getResourceManager() == null) {
			return;
		}
		try {
			Object cape = The5zigMod.getVars().getResourceManager().getCapeLocation((Object) this);
			ResourceLocation capeLocation;
			if (cape instanceof eu.the5zig.mod.platform.ResourceLocation) {
				// The 5zig wrapper type is not a vanilla resource location any more in 1.21.1.
				capeLocation = ((eu.the5zig.mod.platform.ResourceLocation) cape).toMinecraft();
			} else if (cape instanceof ResourceLocation) {
				capeLocation = (ResourceLocation) cape;
			} else {
				return;
			}
			PlayerSkin skin = callbackInfo.getReturnValue();
			if (skin == null || capeLocation.equals(skin.capeTexture())) {
				return;
			}
			callbackInfo.setReturnValue(new PlayerSkin(skin.texture(), skin.textureUrl(), capeLocation,
					skin.elytraTexture(), skin.model(), skin.secure()));
		} catch (Throwable ignored) {
			// Fall back to the vanilla skin.
		}
	}

	@Unique
	private static float the5zig$computeStaticFov(AbstractClientPlayer player) {
		Abilities abilities = player.getAbilities();
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
		if (player.getUseItem().is(Items.BOW)) {
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
