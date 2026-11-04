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

import com.mojang.blaze3d.vertex.PoseStack;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.util.CoreState;
import eu.the5zig.mod.util.LegacyText;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of 1.15's {@code MixinGuiChatNew}.
 * <p>
 * 1.17 funnels every displayed chat line (system chat, player chat, the local echo and messages
 * added by the mod itself) through {@code ChatComponent#addMessage(Component)}. Player chat first
 * passes through {@code enqueueMessage} (the chat-delay queue) but always lands here before it is
 * drawn. There is no message signature and no chat tag in this version — both were added by the
 * 1.19 signing rewrite.
 * The re-entrancy flag stops a message that the chat filters rewrite from being processed a second
 * time when the mod re-adds it.
 */
@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {

	@Unique
	private static boolean the5zig$processingChat;

	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;I)V", at = @At("HEAD"))
	private void the5zig$drawSecondChat(PoseStack poseStack, int ticks, CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			The5zigMod.getVars().get2ndChat().draw(ticks);
		} catch (Throwable ignored) {
			// The second chat is an optional feature.
		}
	}

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
	private void the5zig$onChatMessage(Component component, CallbackInfo callbackInfo) {
		if (!CoreState.isReady() || component == null || the5zig$processingChat) {
			return;
		}
		the5zig$processingChat = true;
		try {
			// 1.15 handed over ITextComponent#getFormattedText(), i.e. a '§'-coded string; keep that
			// contract because the chat filters, the server listeners and the second chat all parse
			// legacy codes. RESET codes are stripped, exactly like the 1.15 hook did.
			String message = LegacyText.toLegacy(component.getVisualOrderText()).replace("\u00a7r", "");
			if (The5zigMod.getListener().onServerChat(message, component, component)) {
				callbackInfo.cancel();
			}
		} catch (Throwable ignored) {
			// Never swallow a chat line because a listener failed.
		} finally {
			the5zig$processingChat = false;
		}
	}

	/**
	 * Prepends the configured time stamp to every incoming line.
	 */
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
			at = @At("HEAD"), argsOnly = true)
	private Component the5zig$withTimePrefix(Component component) {
		if (!CoreState.isReady() || component == null) {
			return component;
		}
		try {
			if (The5zigMod.getConfig().getBool("chatTimePrefixEnabled")) {
				Object prefixed = The5zigMod.getDataManager().getChatComponentWithTime(component);
				if (prefixed instanceof Component) {
					return (Component) prefixed;
				}
			}
		} catch (Throwable ignored) {
			// Fall back to the untouched component.
		}
		return component;
	}

	@Inject(method = "clearMessages(Z)V", at = @At("HEAD"))
	private void the5zig$clearMessages(boolean clearHistory, CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			The5zigMod.getVars().get2ndChat().clear();
		} catch (Throwable ignored) {
			// The second chat is an optional feature.
		}
	}

	@Inject(method = "scrollChat(D)V", at = @At("HEAD"))
	private void the5zig$scrollChat(double amount, CallbackInfo callbackInfo) {
		if (!CoreState.isReady()) {
			return;
		}
		try {
			The5zigMod.getVars().get2ndChat().scroll((int) amount);
		} catch (Throwable ignored) {
			// The second chat is an optional feature.
		}
	}
}
