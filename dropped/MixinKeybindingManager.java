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

import eu.the5zig.mod.fabric.FiveZigKeybinds;
import eu.the5zig.mod.manager.KeybindingManager;
import eu.the5zig.mod.util.IKeybinding;
import eu.the5zig.mod.util.IVariables;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Routes the core's key bindings through {@link FiveZigKeybinds}.
 * <p>
 * The core creates its bindings while it initialises, which on Fabric happens after
 * {@code Minecraft.options} already exists — too late for
 * {@code KeyBindingHelper.registerKeyBinding}. {@link FiveZigKeybinds} therefore registers them
 * from the mod initialiser and this mixin hands those very instances to {@code KeybindingManager}
 * instead of letting the platform create a second, unregistered set. The follow-up
 * {@code registerKeybindings} call is swallowed for the same reason: the mappings are live
 * already.
 * <p>
 * Bindings registered by plugins through {@code ModAPI#registerKeyBinding} do not pass through
 * here; their platform implementation has to use {@link FiveZigKeybinds#wrap(String, int, String)}
 * as well.
 */
@Mixin(KeybindingManager.class)
public abstract class MixinKeybindingManager {

	@Redirect(method = "registerKeybinding",
			at = @At(value = "INVOKE",
					target = "Leu/the5zig/mod/util/IVariables;createKeybinding(Ljava/lang/String;ILjava/lang/String;)Leu/the5zig/mod/util/IKeybinding;"))
	private IKeybinding the5zig$createKeybinding(IVariables variables, String description, int keyCode, String category) {
		return FiveZigKeybinds.wrap(description, keyCode, category);
	}

	@Redirect(method = "registerKeybinding",
			at = @At(value = "INVOKE",
					target = "Leu/the5zig/mod/util/IVariables;registerKeybindings(Ljava/util/List;)V"))
	private void the5zig$skipRegistration(IVariables variables, List<IKeybinding> keybindings) {
		// The mappings were registered before Minecraft constructed its Options instance.
	}
}
