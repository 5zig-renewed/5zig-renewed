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

import eu.the5zig.mod.ModAPIImpl;
import eu.the5zig.mod.fabric.FiveZigKeybinds;
import eu.the5zig.mod.util.IKeybinding;
import eu.the5zig.mod.util.IVariables;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Plugin key bindings ({@code ModAPI#registerKeyBinding}) get the same treatment as the core's own
 * bindings: they are registered through {@link FiveZigKeybinds}, which knows how to add a mapping
 * after {@code Options} has been constructed, instead of going through
 * {@code IVariables#registerKeybindings} (that one uses {@code KeyBindingHelper} and throws once
 * the game is running).
 */
@Mixin(ModAPIImpl.class)
public abstract class MixinModAPIImpl {

	@Redirect(method = "registerKeyBinding",
			at = @At(value = "INVOKE",
					target = "Leu/the5zig/mod/util/IVariables;createKeybinding(Ljava/lang/String;ILjava/lang/String;)Leu/the5zig/mod/util/IKeybinding;"))
	private IKeybinding the5zig$createKeybinding(IVariables variables, String description, int keyCode, String category) {
		return FiveZigKeybinds.wrap(description, keyCode, category);
	}

	@Redirect(method = "registerKeyBinding",
			at = @At(value = "INVOKE",
					target = "Leu/the5zig/mod/util/IVariables;registerKeybindings(Ljava/util/List;)V"))
	private void the5zig$skipRegistration(IVariables variables, List<IKeybinding> keybindings) {
		// Registered on demand by FiveZigKeybinds.
	}
}
