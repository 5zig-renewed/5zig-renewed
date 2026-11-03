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
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the core's raw plugin-channel API onto 1.21.1's typed payload system.
 * <p>
 * 1.15/1.20.1 delivered {@code (channel, byte[])} packets; 1.21.1 decodes every custom payload into a
 * registered {@link CustomPacketPayload} type <em>before</em> the packet listener runs, and unknown
 * channels are discarded without their bodies. The 5zig server API therefore has to declare its
 * channels up front: {@link #bootstrap()} registers a raw payload type for each of them on both
 * directions and installs a receiver that hands the bytes back to
 * {@code EventListener#handlePluginMessage}.
 * <p>
 * {@link #bootstrap()} must run during client initialisation, before a server connection is opened,
 * which is why it is called from the 1.21.1 entrypoint rather than lazily.
 */
public final class PluginMessages {

	/** Payloads larger than this are rejected by the server exactly like in 1.20.1. */
	public static final int MAX_PAYLOAD_SIZE = 1048576;

	private static final String[] CHANNELS = {
			"the5zigmod:5zig",
			"the5zigmod:5zig_reg",
			"the5zigmod:5zig_set"
	};

	private static final Map<String, CustomPacketPayload.Type<RawPayload>> TYPES = new HashMap<>();
	private static boolean bootstrapped;
	private static boolean unsupportedChannelLogged;

	private PluginMessages() {
	}

	/**
	 * A payload type that carries the channel's raw bytes untouched.
	 */
	public record RawPayload(CustomPacketPayload.Type<RawPayload> type, byte[] data) implements CustomPacketPayload {
	}

	public static synchronized void bootstrap() {
		if (bootstrapped) {
			return;
		}
		bootstrapped = true;
		for (String channel : CHANNELS) {
			CustomPacketPayload.Type<RawPayload> type =
					new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.parse(channel));
			TYPES.put(channel, type);
			StreamCodec<FriendlyByteBuf, RawPayload> codec = new StreamCodec<>() {
				@Override
				public RawPayload decode(FriendlyByteBuf buffer) {
					byte[] data = new byte[buffer.readableBytes()];
					buffer.readBytes(data);
					return new RawPayload(type, data);
				}

				@Override
				public void encode(FriendlyByteBuf buffer, RawPayload payload) {
					buffer.writeBytes(payload.data());
				}
			};
			PayloadTypeRegistry.playC2S().register(type, codec);
			PayloadTypeRegistry.playS2C().register(type, codec);
			ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
					context.client().execute(() -> deliver(type.id().toString(), payload.data())));
		}
	}

	/**
	 * Sends raw bytes on one of the 5zig API channels.
	 *
	 * @return {@code false} if the channel is not one of the registered ones or the server did not
	 * announce support for it.
	 */
	public static boolean send(String channel, byte[] data) {
		CustomPacketPayload.Type<RawPayload> type = TYPES.get(channel);
		if (type == null) {
			if (!unsupportedChannelLogged) {
				unsupportedChannelLogged = true;
				The5zigMod.logger.warn("[5zig Renewed] Plugin channel {} is not registered on 1.21.1; "
						+ "custom payloads on arbitrary channels are no longer possible", channel);
			}
			return false;
		}
		if (!ClientPlayNetworking.canSend(type)) {
			return false;
		}
		ClientPlayNetworking.send(new RawPayload(type, data));
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
