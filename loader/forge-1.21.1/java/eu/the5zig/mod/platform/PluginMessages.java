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
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the core's raw plugin-channel API onto 1.21.1's typed payload system, for Forge.
 * <p>
 * This is the Forge counterpart of the platform's Fabric-API implementation
 * ({@code platform/1.21.1/eu/the5zig/mod/platform/PluginMessages.java}); the build excludes that
 * file and compiles this one instead, so {@code Variables#sendCustomPayload} stays loader-neutral.
 * <p>
 * 1.21.1 decodes every custom payload into a registered {@code CustomPacketPayload} type
 * <em>before</em> the packet listener runs and discards unknown channels without their bodies, so
 * the 5zig API channels have to be declared up front. Forge 21.1/52.1 only accepts channel
 * declarations while the mod is being constructed ({@code NetworkRegistry} is locked right after),
 * hence {@link #bootstrap()} is called from the mod constructor.
 * <p>
 * Each 5zig channel is a {@link SimpleChannel} of its own with a single raw-bytes message, so the
 * payload id on the wire stays the historical channel name ({@code the5zigmod:5zig}, ...). Forge
 * encodes SimpleChannel traffic through its own {@code ForgePayload} wrapper — a vanilla server
 * still cannot decode it, exactly like the 1.20.1 build, and {@link #send} returns {@code false}
 * when the other side never announced the channel.
 */
public final class PluginMessages {

	/** Payloads larger than this are rejected by the server exactly like in 1.20.1. */
	public static final int MAX_PAYLOAD_SIZE = 1048576;

	private static final String[] CHANNELS = {
			"the5zigmod:5zig",
			"the5zigmod:5zig_reg",
			"the5zigmod:5zig_set"
	};

	private static final Map<String, SimpleChannel> CHANNEL_BY_NAME = new HashMap<>();
	private static boolean bootstrapped;
	private static boolean unsupportedChannelLogged;
	private static boolean sendFailureLogged;

	private PluginMessages() {
	}

	/**
	 * One message type per channel; the body is passed through untouched.
	 */
	public record RawMessage(byte[] data) {
	}

	/**
	 * Opens the three 5zig channels. Must run from the mod constructor: Forge locks its network
	 * registry once mod construction is over.
	 */
	public static synchronized void bootstrap() {
		if (bootstrapped) {
			return;
		}
		bootstrapped = true;
		for (String name : CHANNELS) {
			SimpleChannel channel = ChannelBuilder.named(ResourceLocation.parse(name))
					.networkProtocolVersion(1)
					// optional(): joining a server that does not know the channel must stay possible,
					// the same behaviour as the Fabric build's ClientPlayNetworking.canSend guard.
					.optional()
					.simpleChannel();
			channel.messageBuilder(RawMessage.class)
					.encoder((message, buffer) -> buffer.writeBytes(message.data()))
					.decoder(PluginMessages::decode)
					// Handlers run on the network thread; the core expects the client thread.
					.consumerMainThread((message, context) -> deliver(name, message.data()))
					.add();
			channel.build();
			CHANNEL_BY_NAME.put(name, channel);
		}
	}

	private static RawMessage decode(FriendlyByteBuf buffer) {
		byte[] data = new byte[buffer.readableBytes()];
		buffer.readBytes(data);
		return new RawMessage(data);
	}

	/**
	 * Sends raw bytes on one of the 5zig API channels.
	 *
	 * @return {@code false} if the channel is not one of the registered ones, the client is not
	 * connected, or the server did not announce support for it.
	 */
	public static boolean send(String channel, byte[] data) {
		SimpleChannel target = CHANNEL_BY_NAME.get(channel);
		if (target == null) {
			if (!unsupportedChannelLogged) {
				unsupportedChannelLogged = true;
				The5zigMod.logger.warn("[5zig Renewed] Plugin channel {} is not registered on 1.21.1; "
						+ "custom payloads on arbitrary channels are no longer possible", channel);
			}
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener listener = minecraft == null ? null : minecraft.getConnection();
		if (listener == null || !target.isRemotePresent(listener.getConnection())) {
			return false;
		}
		try {
			target.send(new RawMessage(data), PacketDistributor.SERVER.noArg());
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
