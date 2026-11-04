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
import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plugin messages ("MC|Brand", ...) on the NeoForge 20.x line.
 * <p>
 * NeoForge rewrites {@code ClientboundCustomPayloadPacket} handling: it adds a packet-aware
 * {@code handleCustomPayload(ClientboundCustomPayloadPacket)} to
 * {@link ClientCommonPacketListenerImpl} and that method owns the {@link BrandPayload} branch
 * (it stores the brand and returns <em>before</em> delegating to the subclass hook). The
 * {@code ClientPacketListener} override therefore never sees a brand payload; on 20.2/20.4 its
 * parameter list differs from vanilla's anyway (the packet is a first argument).
 * <p>
 * So the brand is re-encoded into the legacy {@code ByteBuf} form here, on the method that
 * actually receives it. The {@link ClientPacketListener} guard keeps the hook on the play
 * connection, exactly like the platform mixin it replaces: the same method also backs the
 * configuration-phase listener, and firing {@code ServerConnectEvent} before the play listener
 * exists would populate and then discard the server entry.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class MixinClientCommonPacketListener {

	@Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V", at = @At("RETURN"))
	private void the5zig$handleBrandPayload(ClientboundCustomPayloadPacket packet, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || packet == null) {
			return;
		}
		CustomPacketPayload payload = packet.payload();
		if (!(payload instanceof BrandPayload brandPayload)) {
			return;
		}
		if (!(((Object) this) instanceof ClientPacketListener)) {
			// Configuration phase: the core is not playing yet, and brand is normally only sent here.
			return;
		}
		try {
			FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
			buffer.writeUtf(brandPayload.brand());
			The5zigMod.getListener().handlePluginMessage("minecraft:brand", buffer);
		} catch (Throwable ignored) {
			// Unknown payloads must not break the packet loop.
		}
	}
}
