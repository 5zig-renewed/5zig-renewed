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

package eu.the5zig.mod.fabric;

import eu.the5zig.mod.platform.PluginMessages;

/**
 * 1.20.2 Fabric entrypoint.
 * <p>
 * Since 1.20.2 the client decodes custom payloads before any packet listener runs and drops the body
 * of unknown channels, so the 5zig API channels have to be declared while the mod initialises. That
 * is the only difference to the shared {@link FiveZigFabric} entrypoint, which still does all of the
 * actual setup.
 */
public class FiveZigFabric1202 extends FiveZigFabric {

	@Override
	public void onInitializeClient() {
		PluginMessages.bootstrap();
		super.onInitializeClient();
	}
}
