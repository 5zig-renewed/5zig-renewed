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
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the core's raw plugin-channel API onto 1.20.5's typed payload system.
 * <p>
 * 1.20.1 delivered {@code (channel, byte[])} packets straight to the packet listener. Since 1.20.2 the
 * wire format is a typed {@link CustomPacketPayload}; 1.20.5 also removed Fabric API's raw channel
 * overloads ({@code registerGlobalReceiver(ResourceLocation, …)} / {@code send(ResourceLocation,
 * FriendlyByteBuf)}), so every channel has to be declared as a payload type before it can be received
 * <em>or</em> sent. Payloads on channels the codec does not know are decoded into a body-less
 * {@code DiscardedPayload}, and the fallback encoder writes no body at all — arbitrary plugin channels
 * are therefore impossible from 1.20.5 on.
 * <p>
 * The 5zig server API channels are declared here in both directions, and the receiver hands the bytes
 * back to {@code EventListener#handlePluginMessage}.
 * <p>
 * {@link #bootstrap()} must run during client initialisation, before a server connection is opened,
 * which is why it is called from the 1.20.5 entrypoint rather than lazily.
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
			CustomPacketPayload.Type<RawPayload> type = new CustomPacketPayload.Type<>(new ResourceLocation(channel));
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
	 * @return true if {@code channel} is one of the channels registered with Fabric.
	 */
	public static boolean isRegistered(String channel) {
		return TYPES.containsKey(channel);
	}

	/**
	 * Sends raw bytes on one of the 5zig API channels.
	 *
	 * @return {@code false} if {@code channel} is not one of the registered ones, or the server did
	 * not announce support for it. Callers must not fall back to a plain
	 * {@code ServerboundCustomPayloadPacket}: since 1.20.5 its codec drops the body of any payload
	 * type it does not know.
	 */
	public static boolean send(String channel, byte[] data) {
		CustomPacketPayload.Type<RawPayload> type = TYPES.get(channel);
		if (type == null) {
			if (!unsupportedChannelLogged) {
				unsupportedChannelLogged = true;
				The5zigMod.logger.warn("[5zig Renewed] Plugin channel {} is not registered on 1.20.5; "
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
