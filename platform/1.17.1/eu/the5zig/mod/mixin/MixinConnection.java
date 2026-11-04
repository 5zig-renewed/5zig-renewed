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
import eu.the5zig.mod.event.PacketEvent;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of 1.15's {@code MixinNetworkManager}.
 * <p>
 * {@code NetworkManager} is called {@code Connection} in 1.17.1 and still exposes the same two
 * points: {@code send} for outgoing packets and {@code channelRead0} for incoming ones. Both run
 * on the netty event loop, exactly as they did in 1.15, so listeners must not touch world state
 * without scheduling.
 */
@Mixin(Connection.class)
public abstract class MixinConnection {

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void the5zig$sendPacket(Packet<?> packet, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || packet == null) {
			return;
		}
		try {
			The5zigMod.getListener().fireEvent(new PacketEvent(packet, false));
		} catch (Throwable ignored) {
			// A failing listener must never break the connection.
		}
	}

	@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void the5zig$receivePacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || packet == null) {
			return;
		}
		try {
			The5zigMod.getListener().fireEvent(new PacketEvent(packet, true));
		} catch (Throwable ignored) {
			// A failing listener must never break the connection.
		}
	}
}
