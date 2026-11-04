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
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Outgoing chat hook.
 * <p>
 * 1.18.1 has a single entry point: {@code LocalPlayer#chat(String)}, which prefixes commands with
 * {@code /} itself and then sends a {@code ServerboundChatPacket}. Unlike 1.19.2 there is no
 * {@code chatSigned}/{@code commandSigned} pair (chat signing arrives with 1.19) and there is no
 * {@code ClientPacketListener#sendChat} either (that is 1.19.3), so one injection covers chat and
 * commands alike — exactly the shape of the 1.15 build's {@code sendChatMessage} hook.
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

	@Inject(method = "chat(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
	private void the5zig$sendChat(String message, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || message == null) {
			return;
		}
		try {
			if (The5zigMod.getListener().onSendChatMessage(message)) {
				callbackInfo.cancel();
			}
		} catch (Throwable ignored) {
			// Send the message unmodified if a listener fails.
		}
	}
}
