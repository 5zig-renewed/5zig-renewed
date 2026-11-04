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
 * 1.19.2 still signs and sends chat inside {@code LocalPlayer} ({@code chatSigned} for chat,
 * {@code commandSigned} for commands); {@code ClientPacketListener#sendChat} only appears in
 * 1.19.3. Both entry points are hooked, because the 1.15 build's {@code sendChatMessage} covered
 * chat and commands alike and the core's server-switch commands rely on it.
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

	@Inject(method = "chatSigned(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
	private void the5zig$sendChat(String message, Component component, CallbackInfo callbackInfo) {
		the5zig$intercept(message, callbackInfo);
	}

	@Inject(method = "commandSigned(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
	private void the5zig$sendCommand(String command, Component component, CallbackInfo callbackInfo) {
		// The hook receives the command without its leading slash, exactly like the signed send.
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
