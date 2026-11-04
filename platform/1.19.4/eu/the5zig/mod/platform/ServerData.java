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

import eu.the5zig.mod.util.IServerData;

/**
 * 1.19.4 view over {@link net.minecraft.client.multiplayer.ServerData}.
 * <p>
 * 1.19.4 keeps {@code name/ip/motd/ping} on the object, stores the favicon as raw PNG bytes
 * ({@code getIconBytes}) and gained the online/max counters from the ping response
 * ({@code players}), which 1.19.3 did not have.
 */
public class ServerData extends net.minecraft.client.multiplayer.ServerData implements IServerData {

	public ServerData(String serverIP) {
		super(serverIP, serverIP, false);
	}

	@Override
	public String getServerName() {
		return name;
	}

	@Override
	public String getServerIP() {
		return ip;
	}

	@Override
	public String getPopulationInfo() {
		// 1.19.4 exposes the ping response's online/max counters; fall back to the sampled list
		// (what 1.19.3 could offer) when the server did not send them.
		if (players != null) {
			return players.online() + "/" + players.max();
		}
		if (playerList == null || playerList.isEmpty()) {
			return "";
		}
		return Integer.toString(playerList.size());
	}

	@Override
	public String getMOTD() {
		return motd == null ? "" : motd.getString();
	}

	@Override
	public long getPing() {
		return ping;
	}

	@Override
	public String getServerIcon() {
		// 1.19.4 stores the favicon as raw PNG bytes (getIconBytes) and no longer exposes the
		// base64 form; the 5zig API still hands out base64, so encode on demand.
		byte[] iconBytes = getIconBytes();
		return iconBytes == null ? null : java.util.Base64.getEncoder().encodeToString(iconBytes);
	}
}
