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
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Outgoing chat hook.
 * <p>
 * 1.19 has no signed-chat split yet: {@code LocalPlayer#chat(String, Component)} and
 * {@code LocalPlayer#command(String, Component)} are the terminal senders (the one-argument
 * overloads delegate to them) and both build their own {@code ServerboundChatPacket}. They are
 * hooked rather than the packet constructor because the 1.15 build's {@code sendChatMessage}
 * covered chat and commands alike and the core's server-switch commands rely on it. 1.19.1 renamed
 * these to {@code chatSigned}/{@code commandSigned} when signing moved in, and 1.19.3 moved them
 * again to {@code ClientPacketListener#sendChat/sendCommand}.
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

	@Inject(method = "chat(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
	private void the5zig$sendChat(String message, Component component, CallbackInfo callbackInfo) {
		the5zig$intercept(message, callbackInfo);
	}

	@Inject(method = "command(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
	private void the5zig$sendCommand(String command, Component component, CallbackInfo callbackInfo) {
		// The hook receives the command without its leading slash, exactly like the 1.19.1 send.
		the5zig$intercept("/" + command, callbackInfo);
	}

	private static void the5zig$intercept(String message, CallbackInfo callbackInfo) {
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
