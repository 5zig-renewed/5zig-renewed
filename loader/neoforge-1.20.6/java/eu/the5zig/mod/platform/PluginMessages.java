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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges the core's raw plugin-channel API onto 1.20.6's typed payload system, for NeoForge.
 * <p>
 * This is the NeoForge counterpart of the platform's Fabric-API implementation
 * ({@code platform/1.20.6/eu/the5zig/mod/platform/PluginMessages.java}); the build excludes that
 * file and compiles this one instead, so {@code Variables#sendCustomPayload} stays loader-neutral.
 * <p>
 * 1.20.5 removed Fabric's raw channel overloads and the fallback encoder writes no body for an
 * unknown payload, so on 1.20.6 a channel must be registered to be received <em>or</em> sent. The
 * three 5zig API channels are declared {@code optional()} here so a vanilla server (or a
 * single-player world without the 5zig plugin) is unaffected.
 */
public final class PluginMessages {

	/** Payloads larger than this are rejected by the server exactly like in 1.20.1. */
	public static final int MAX_PAYLOAD_SIZE = 32767;

	private static final String[] CHANNELS = {
			"the5zigmod:5zig",
			"the5zigmod:5zig_reg",
			"the5zigmod:5zig_set"
	};

	private static final Map<String, CustomPacketPayload.Type<RawPayload>> TYPES = new LinkedHashMap<>();

	private static boolean bootstrapped;
	private static boolean registered;
	private static boolean unsupportedChannelLogged;
	private static boolean sendFailureLogged;

	private PluginMessages() {
	}

	/**
	 * A payload type that carries the channel's raw bytes untouched.
	 */
	public record RawPayload(CustomPacketPayload.Type<RawPayload> type, byte[] data) implements CustomPacketPayload {
	}

	/**
	 * Creates the payload types for the three 5zig channels. Does not touch the network registry:
	 * NeoForge only accepts registrations from {@link RegisterPayloadHandlersEvent}.
	 */
	public static synchronized void bootstrap() {
		if (bootstrapped) {
			return;
		}
		bootstrapped = true;
		for (String channel : CHANNELS) {
			TYPES.put(channel, new CustomPacketPayload.Type<>(new ResourceLocation(channel)));
		}
	}

	/**
	 * Mod-bus hook: declares every 5zig channel in both directions.
	 * <p>
	 * {@code optional()} keeps the channels out of the mandatory handshake, so joining a server
	 * (or a single-player world) that does not know them is still possible.
	 */
	public static synchronized void register(RegisterPayloadHandlersEvent event) {
		if (registered) {
			return;
		}
		bootstrap();
		registered = true;

		PayloadRegistrar registrar = event.registrar("1").optional();
		for (CustomPacketPayload.Type<RawPayload> type : TYPES.values()) {
			StreamCodec<RegistryFriendlyByteBuf, RawPayload> codec = new StreamCodec<>() {
				@Override
				public RawPayload decode(RegistryFriendlyByteBuf buffer) {
					byte[] data = new byte[buffer.readableBytes()];
					buffer.readBytes(data);
					return new RawPayload(type, data);
				}

				@Override
				public void encode(RegistryFriendlyByteBuf buffer, RawPayload payload) {
					buffer.writeBytes(payload.data());
				}
			};
			registrar.playBidirectional(type, codec, PluginMessages::handle);
		}
	}

	private static void handle(RawPayload payload, IPayloadContext context) {
		// Payload handlers run on the network thread; the core expects the client thread.
		context.enqueueWork(() -> deliver(payload.type().id().toString(), payload.data()));
	}

	/**
	 * Sends raw bytes on one of the 5zig API channels.
	 *
	 * @return {@code false} if the channel is not one of the registered ones, the client is not
	 * connected, or the server did not announce support for it.
	 */
	public static boolean send(String channel, byte[] data) {
		bootstrap();
		CustomPacketPayload.Type<RawPayload> type = TYPES.get(channel);
		if (type == null) {
			if (!unsupportedChannelLogged) {
				unsupportedChannelLogged = true;
				The5zigMod.logger.warn("[5zig Renewed] Plugin channel {} is not registered on 1.20.6; "
						+ "custom payloads on arbitrary channels are no longer possible", channel);
			}
			return false;
		}
		if (!canSend(type)) {
			return false;
		}
		try {
			PacketDistributor.sendToServer(new RawPayload(type, data));
			return true;
		} catch (Throwable throwable) {
			if (!sendFailureLogged) {
				sendFailureLogged = true;
				The5zigMod.logger.warn("[5zig Renewed] Could not send a payload on channel {}; "
						+ "further send errors will be suppressed", channel, throwable);
			}
			return false;
		}
	}

	/**
	 * NeoForge equivalent of Fabric's {@code ClientPlayNetworking.canSend}: the server must have
	 * announced the channel during configuration/play.
	 */
	private static boolean canSend(CustomPacketPayload.Type<?> type) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener listener = minecraft == null ? null : minecraft.getConnection();
		if (listener == null) {
			return false;
		}
		return NetworkRegistry.hasChannel(listener.getConnection(), ConnectionProtocol.PLAY, type.id());
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
