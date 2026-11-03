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
import eu.the5zig.mod.gui.ingame.ItemStack;
import eu.the5zig.mod.util.LegacyText;
import eu.the5zig.mod.util.TabList;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
import io.netty.buffer.Unpooled;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of 1.15's {@code MixinNetHandlerPlayClient}: everything the core needs from the play
 * connection.
 * <p>
 * Chat itself is handled in {@code MixinChatComponent}, because 1.20.1 builds player chat
 * components inside {@code ChatListener} and no longer hands the finished component to
 * {@code ClientPacketListener}.
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

	/**
	 * 1.15 hooked {@code ClientPlayerEntity#sendChatMessage}; 1.20.1 sends chat through
	 * {@code ClientPacketListener#sendChat}.
	 */
	@Inject(method = "sendChat(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
	private void the5zig$sendChat(String message, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || message == null) {
			return;
		}
		try {
			if (The5zigMod.getListener().onSendChatMessage(message)) {
				callbackInfo.cancel();
			}
		} catch (Throwable ignored) {
			// Send the message unmodified if a listener fails.
		}
	}

	/**
	 * Plugin messages ("MC|Brand", the 5zig server API channel, ...).
	 */
	/**
	 * Plugin messages ("MC|Brand", the 5zig server API channel, ...).
	 * <p>
	 * 1.21.1 replaced the raw {@code Identifier + byte[]} packet with typed {@code CustomPacketPayload}s
	 * which are decoded <em>before</em> the listener sees them; unknown channels become a
	 * {@code DiscardedPayload} that carries only the channel id, so their bytes never reach this hook.
	 * The brand payload is still readable and is the one the core needs for server detection, so it is
	 * re-encoded into the legacy {@code ByteBuf} form here.
	 */
	@Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V", at = @At("TAIL"))
	private void the5zig$handleCustomPayload(CustomPacketPayload payload, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || payload == null) {
			return;
		}
		try {
			if (payload instanceof BrandPayload brandPayload) {
				FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
				buffer.writeUtf(brandPayload.brand());
				The5zigMod.getListener().handlePluginMessage("minecraft:brand", buffer);
			} else {
				// The 5zig API channels are forwarded by {@code ServerAPIPayloadHandler} (Fabric payload
				// registration) instead of this hook; see the platform notes in the port report.
				The5zigMod.getListener().handlePluginMessage(payload.type().id().toString(),
					new FriendlyByteBuf(Unpooled.EMPTY_BUFFER));
			}
		} catch (Throwable ignored) {
			// Unknown payloads must not break the packet loop.
		}
	}

	@Inject(method = "handleTabListCustomisation(Lnet/minecraft/network/protocol/game/ClientboundTabListPacket;)V", at = @At("TAIL"))
	private void the5zig$handleTabList(ClientboundTabListPacket packet, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || packet == null) {
			return;
		}
		try {
			TabList tabList = new TabList(
					LegacyText.toLegacy(packet.header().getVisualOrderText()),
					LegacyText.toLegacy(packet.footer().getVisualOrderText()));
			The5zigMod.getListener().onPlayerListHeaderFooter(tabList);
		} catch (Throwable ignored) {
			// Tab list decoration is optional.
		}
	}

	@Inject(method = "handleContainerSetSlot(Lnet/minecraft/network/protocol/game/ClientboundContainerSetSlotPacket;)V", at = @At("HEAD"))
	private void the5zig$handleSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || packet == null) {
			return;
		}
		try {
			Object wrapped = Class.forName("eu.the5zig.mod.platform.WrappedItemStack")
					.getConstructor(net.minecraft.world.item.ItemStack.class)
					.newInstance(packet.getItem());
			The5zigMod.getListener().onInventorySetSlot(packet.getSlot(), (ItemStack) wrapped);
		} catch (Throwable ignored) {
			// The wrapper only exists once the platform layer is complete.
		}
	}

	@Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V", at = @At("TAIL"))
	private void the5zig$handleMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || packet == null) {
			return;
		}
		try {
			The5zigMod.getListener().onTeleport(packet.getX(), packet.getY(), packet.getZ(), packet.getYRot(), packet.getXRot());
		} catch (Throwable ignored) {
			// Teleport tracking is optional.
		}
	}

	/**
	 * 1.15 used the server-difficulty packet as the "server is ready" signal that fires
	 * {@code ServerConnectEvent}.
	 */
	@Inject(method = "handleChangeDifficulty(Lnet/minecraft/network/protocol/game/ClientboundChangeDifficultyPacket;)V", at = @At("TAIL"))
	private void the5zig$handleChangeDifficulty(net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket packet, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || packet == null) {
			return;
		}
		try {
			The5zigMod.getListener().handleServerDifficulty();
		} catch (Throwable ignored) {
			// Server detection is optional.
		}
	}
}
