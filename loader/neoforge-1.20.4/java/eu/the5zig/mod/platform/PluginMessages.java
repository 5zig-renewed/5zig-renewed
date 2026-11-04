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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.handling.IPlayPayloadHandler;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges the core's raw plugin-channel API onto 1.20.4's typed payload system, for NeoForge.
 * <p>
 * This is the NeoForge counterpart of the platform's Fabric-API implementation
 * ({@code platform/1.20.4/eu/the5zig/mod/platform/PluginMessages.java}); the build excludes that
 * file and compiles this one instead, so {@code Variables#sendCustomPayload} stays loader-neutral.
 * <p>
 * Since 1.20.2 the client decodes every custom payload into a {@link CustomPacketPayload}
 * <em>before</em> the packet listener runs, and a channel the client has not registered decodes to
 * a {@code DiscardedPayload} that carries only the id — the body is gone. The 5zig API channels
 * therefore have to be declared up front with {@link RegisterPayloadHandlerEvent}, which NeoForge
 * only fires on the mod bus. They are declared {@code optional()} so a vanilla server (or a
 * single-player world without the 5zig plugin) is unaffected.
 * <p>
 * Sending needs no registration at all on 1.20.4: {@link ServerboundCustomPayloadPacket} still
 * writes any id followed by the payload body, which is exactly what the 1.20.1-era 5zig server
 * plugin expects.
 */
public final class PluginMessages {

	/**
	 * Payloads larger than this are rejected by 1.20.4 servers ({@code ServerboundCustomPayloadPacket}
	 * caps the body at 32 KiB minus the channel name).
	 */
	public static final int MAX_PAYLOAD_SIZE = 32767;

	private static final String[] CHANNELS = {
			"the5zigmod:5zig",
			"the5zigmod:5zig_reg",
			"the5zigmod:5zig_set"
	};

	private static final Map<String, ResourceLocation> IDS = new LinkedHashMap<>();

	private static boolean bootstrapped;
	private static boolean registered;
	private static boolean unsupportedChannelLogged;
	private static boolean sendFailureLogged;

	private PluginMessages() {
	}

	/**
	 * A payload that carries one channel's raw bytes untouched.
	 * <p>
	 * 1.20.4 has no {@code CustomPacketPayload.Type}: payloads are identified by
	 * {@link #id()} and registered by {@link ResourceLocation} through the payload registrar.
	 */
	public record RawPayload(ResourceLocation id, byte[] data) implements CustomPacketPayload {

		@Override
		public void write(FriendlyByteBuf buffer) {
			buffer.writeBytes(data);
		}
	}

	/**
	 * Resolves the three 5zig channels. Safe to call from anywhere; the registrar needs it before
	 * any payload can be declared.
	 */
	public static synchronized void bootstrap() {
		if (bootstrapped) {
			return;
		}
		bootstrapped = true;
		for (String channel : CHANNELS) {
			IDS.put(channel, new ResourceLocation(channel));
		}
	}

	/**
	 * Mod-bus hook: declares every 5zig channel in both directions.
	 * <p>
	 * {@code optional()} keeps the channels out of the mandatory handshake, so joining a server
	 * (or a single-player world) that does not know them is still possible.
	 */
	public static synchronized void register(RegisterPayloadHandlerEvent event) {
		if (registered) {
			return;
		}
		bootstrap();
		registered = true;

		IPayloadRegistrar registrar = event.registrar("1").optional();
		for (ResourceLocation id : IDS.values()) {
			FriendlyByteBuf.Reader<RawPayload> reader = buffer -> {
				byte[] data = new byte[buffer.readableBytes()];
				buffer.readBytes(data);
				return new RawPayload(id, data);
			};
			IPlayPayloadHandler<RawPayload> handler = (payload, context) ->
					// Payload handlers run on the network thread; the core expects the client thread.
					context.workHandler().execute(() -> deliver(payload.id().toString(), payload.data()));
			registrar.play(id, reader, handler);
		}
	}

	/**
	 * Sends raw bytes on one of the 5zig API channels.
	 *
	 * @return {@code false} if the channel is not one of the registered ones or the client is not
	 * connected. Callers fall back to a plain {@code ServerboundCustomPayloadPacket}, which 1.20.4
	 * still accepts for any channel.
	 */
	public static boolean send(String channel, byte[] data) {
		bootstrap();
		ResourceLocation id = IDS.get(channel);
		if (id == null) {
			if (!unsupportedChannelLogged) {
				unsupportedChannelLogged = true;
				The5zigMod.logger.warn("[5zig Renewed] Plugin channel {} is not one of the 5zig channels; "
						+ "it is sent as a raw 1.20.4 payload instead", channel);
			}
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener listener = minecraft == null ? null : minecraft.getConnection();
		if (listener == null) {
			return false;
		}
		try {
			listener.send(new ServerboundCustomPayloadPacket(new RawPayload(id, data)));
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
