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
import eu.the5zig.mod.fabric.FiveZigFabric;
import eu.the5zig.mod.gui.ingame.ItemStack;
import eu.the5zig.mod.util.LegacyText;
import eu.the5zig.mod.util.TabList;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
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
		if (!FiveZigFabric.isCoreReady() || message == null) {
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
	@Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/game/ClientboundCustomPayloadPacket;)V", at = @At("TAIL"))
	private void the5zig$handleCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo callbackInfo) {
		if (!FiveZigFabric.isCoreReady() || packet == null) {
			return;
		}
		try {
			The5zigMod.getListener().handlePluginMessage(packet.getIdentifier().toString(), packet.getData());
		} catch (Throwable ignored) {
			// Unknown payloads must not break the packet loop.
		}
	}

	@Inject(method = "handleTabListCustomisation(Lnet/minecraft/network/protocol/game/ClientboundTabListPacket;)V", at = @At("TAIL"))
	private void the5zig$handleTabList(ClientboundTabListPacket packet, CallbackInfo callbackInfo) {
		if (!FiveZigFabric.isCoreReady() || packet == null) {
			return;
		}
		try {
			TabList tabList = new TabList(
					LegacyText.toLegacy(packet.getHeader().getVisualOrderText()),
					LegacyText.toLegacy(packet.getFooter().getVisualOrderText()));
			The5zigMod.getListener().onPlayerListHeaderFooter(tabList);
		} catch (Throwable ignored) {
			// Tab list decoration is optional.
		}
	}

	@Inject(method = "handleContainerSetSlot(Lnet/minecraft/network/protocol/game/ClientboundContainerSetSlotPacket;)V", at = @At("HEAD"))
	private void the5zig$handleSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo callbackInfo) {
		if (!FiveZigFabric.isCoreReady() || packet == null) {
			return;
		}
		try {
			Object wrapped = Class.forName("WrappedItemStack")
					.getConstructor(net.minecraft.world.item.ItemStack.class)
					.newInstance(packet.getItem());
			The5zigMod.getListener().onInventorySetSlot(packet.getSlot(), (ItemStack) wrapped);
		} catch (Throwable ignored) {
			// The wrapper only exists once the platform layer is complete.
		}
	}

	@Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V", at = @At("TAIL"))
	private void the5zig$handleMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo callbackInfo) {
		if (!FiveZigFabric.isCoreReady() || packet == null) {
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
		if (!FiveZigFabric.isCoreReady() || packet == null) {
			return;
		}
		try {
			The5zigMod.getListener().handleServerDifficulty();
		} catch (Throwable ignored) {
			// Server detection is optional.
		}
	}
}
