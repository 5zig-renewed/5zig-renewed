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

import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Cape hook.
 * <p>
 * 1.15-1.20.1 replaced {@code AbstractClientPlayer#getCloakTextureLocation()} with the cape the 5zig
 * resource manager resolved for a player. 26.x removed that method: a player's skin is a
 * {@code PlayerSkin} record ({@code skin.body()}, {@code skin.capeTexture()}, ...) built from the
 * profile's textures, so there is no per-player "which cape texture" hook left to inject into.
 * <p>
 * The mixin is kept (and stays listed in the mixin config) as the anchor for the port: drawing 5zig
 * capes on 26.x needs a {@code PlayerSkin} override instead, which is not implemented in this build.
 * No injectors means the class is a legal, inert mixin.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {
}
