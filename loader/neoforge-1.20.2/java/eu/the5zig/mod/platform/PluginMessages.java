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
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.event.EventNetworkChannel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Bridges the core's raw plugin-channel API onto 1.20.2's typed payload system, for NeoForge.
 * <p>
 * This is the NeoForge counterpart of the platform's Fabric-API implementation
 * ({@code platform/1.20.2/eu/the5zig/mod/platform/PluginMessages.java}); the build excludes that
 * file and compiles this one instead, so {@code Variables#sendCustomPayload} stays loader-neutral.
 * <p>
 * 1.20.2 is the first version whose {@code CustomPacketPayload} carries a {@link ResourceLocation}
 * and a buffer, and the client decodes a payload <em>before</em> the packet listener runs: a
 * channel nobody declared becomes a body-less {@code DiscardedPayload}. NeoForge 20.2 has no
 * payload registrar yet — the equivalent is {@link NetworkRegistry#newEventChannel(ResourceLocation,
 * java.util.function.Supplier, java.util.function.Predicate, java.util.function.Predicate)}, whose
 * channel reads the remaining bytes and fires {@link NetworkEvent.ClientCustomPayloadEvent}.
 * Channels must be created while the network registry is still unlocked, i.e. during mod
 * construction.
 * <p>
 * Sending needs no registration: {@link ServerboundCustomPayloadPacket} still writes any id
 * followed by the payload body on 1.20.2.
 */
public final class PluginMessages {

	/** Payloads larger than this are rejected by 1.20.2 servers (the body cap is 32 KiB). */
	public static final int MAX_PAYLOAD_SIZE = 32767;

	private static final String[] CHANNELS = {
			"the5zigmod:5zig",
			"the5zigmod:5zig_reg",
			"the5zigmod:5zig_set"
	};

	private static final Map<String, ResourceLocation> IDS = new LinkedHashMap<>();

	private static boolean bootstrapped;
	private static boolean unsupportedChannelLogged;
	private static boolean sendFailureLogged;

	private PluginMessages() {
	}

	/**
	 * A payload that carries one channel's raw bytes untouched.
	 */
	public record RawPayload(ResourceLocation id, byte[] data) implements CustomPacketPayload {

		@Override
		public void write(FriendlyByteBuf buffer) {
			buffer.writeBytes(data);
		}
	}

	/**
	 * Creates the three 5zig event channels. Must run during mod construction: the network registry
	 * is locked once mod loading finishes, and channel creation after that throws.
	 */
	public static synchronized void bootstrap() {
		if (bootstrapped) {
			return;
		}
		bootstrapped = true;
		for (String channel : CHANNELS) {
			ResourceLocation id = new ResourceLocation(channel);
			IDS.put(channel, id);
			// acceptMissingOr("1"): a peer that does not know the channel at all (vanilla server,
			// single-player world without the 5zig plugin) is still accepted.
			EventNetworkChannel eventChannel = NetworkRegistry.newEventChannel(id, () -> "1",
					NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"));
			Consumer<NetworkEvent.ClientCustomPayloadEvent> listener = event -> {
				FriendlyByteBuf payload = event.getPayload();
				byte[] data = new byte[payload.readableBytes()];
				payload.readBytes(data);
				// The event runs on the network thread; the core expects the client thread.
				event.getSource().enqueueWork(() -> deliver(channel, data));
			};
			eventChannel.addListener(listener);
		}
	}

	/**
	 * Sends raw bytes on one of the 5zig API channels.
	 *
	 * @return {@code false} if {@code channel} is not one of the registered ones; the caller then
	 * falls back to a plain {@code ServerboundCustomPayloadPacket}, which 1.20.2 accepts for any
	 * channel.
	 */
	public static boolean send(String channel, byte[] data) {
		bootstrap();
		ResourceLocation id = IDS.get(channel);
		if (id == null) {
			if (!unsupportedChannelLogged) {
				unsupportedChannelLogged = true;
				The5zigMod.logger.warn("[5zig Renewed] Plugin channel {} is not one of the 5zig channels; "
						+ "it is sent as a raw 1.20.2 payload instead", channel);
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
