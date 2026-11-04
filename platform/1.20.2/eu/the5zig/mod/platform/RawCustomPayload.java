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

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A custom payload that carries raw bytes for an arbitrary plugin channel.
 * <p>
 * 1.20.4 still lets the client send a payload it has not registered: the packet writes
 * {@link #id()} followed by {@link #write(FriendlyByteBuf)}, and a server without a handler for the
 * channel simply discards it. The 5zig API channels go through {@link PluginMessages} instead, because
 * <em>incoming</em> unregistered payloads lose their body when the client decodes them.
 */
public record RawCustomPayload(ResourceLocation id, byte[] data) implements CustomPacketPayload {

	@Override
	public void write(FriendlyByteBuf buffer) {
		buffer.writeBytes(data);
	}
}
