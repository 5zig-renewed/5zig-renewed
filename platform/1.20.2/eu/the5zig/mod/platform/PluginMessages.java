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

package eu.the5zig.mod.platform;

import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.util.CoreState;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Bridges the core's raw plugin-channel API onto 1.20.4's typed payload system.
 * <p>
 * 1.20.1 delivered {@code (channel, byte[])} packets straight to the packet listener. Since 1.20.2 the
 * wire format is a typed {@link net.minecraft.network.protocol.common.custom.CustomPacketPayload}:
 * the client decodes the payload <em>before</em> the listener runs and turns every channel it does not
 * know into a {@code DiscardedPayload} without the body. Fabric API still exposes the pre-1.20.5 raw
 * channel API ({@code registerGlobalReceiver(ResourceLocation, …)} / {@code send(ResourceLocation,
 * FriendlyByteBuf)}), which installs a codec for the channel so the bytes survive decoding again.
 * <p>
 * {@link #bootstrap()} must run during client initialisation, before a server connection is opened,
 * which is why it is called from the 1.20.4 entrypoint rather than lazily.
 */
public final class PluginMessages {

	/**
	 * Payloads larger than this are rejected by the server exactly like in 1.20.1
	 * ({@code ServerboundCustomPayloadPacket} refuses bodies over 32 KiB).
	 */
	public static final int MAX_PAYLOAD_SIZE = 32767;

	private static final String[] CHANNELS = {
			"the5zigmod:5zig",
			"the5zigmod:5zig_reg",
			"the5zigmod:5zig_set"
	};

	private static boolean bootstrapped;

	private PluginMessages() {
	}

	public static synchronized void bootstrap() {
		if (bootstrapped) {
			return;
		}
		bootstrapped = true;
		for (String channel : CHANNELS) {
			ResourceLocation id = new ResourceLocation(channel);
			ClientPlayNetworking.registerGlobalReceiver(id, (client, handler, buffer, responseSender) -> {
				byte[] data = new byte[buffer.readableBytes()];
				buffer.readBytes(data);
				client.execute(() -> deliver(channel, data));
			});
		}
	}

	/**
	 * @return true if {@code channel} is one of the channels registered with Fabric.
	 */
	public static boolean isRegistered(String channel) {
		for (String registered : CHANNELS) {
			if (registered.equals(channel)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sends raw bytes on one of the 5zig API channels.
	 *
	 * @return {@code false} if {@code channel} is not one of the registered ones, or the server did
	 * not announce support for it. Callers fall back to a plain {@code ServerboundCustomPayloadPacket},
	 * which 1.20.4 still accepts for any channel.
	 */
	public static boolean send(String channel, byte[] data) {
		if (!isRegistered(channel)) {
			return false;
		}
		ResourceLocation id = new ResourceLocation(channel);
		if (!ClientPlayNetworking.canSend(id)) {
			return false;
		}
		ClientPlayNetworking.send(id, new FriendlyByteBuf(Unpooled.wrappedBuffer(data)));
		return true;
	}

	private static void deliver(String channel, byte[] data) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			The5zigMod.getListener().handlePluginMessage(channel, new FriendlyByteBuf(Unpooled.wrappedBuffer(data)));
		} catch (Throwable throwable) {
			The5zigMod.logger.warn("[5zig Renewed] Failed to handle a payload on channel {}", channel, throwable);
		}
	}
}
