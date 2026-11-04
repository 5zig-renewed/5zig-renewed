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

import java.util.Base64;

/**
 * 1.20.4 view over {@link net.minecraft.client.multiplayer.ServerData}.
 * <p>
 * The fields the core reads were renamed/split when the server list was reworked:
 * {@code serverName/serverIP/populationInfo/serverMOTD/pingToServer/base64 icon} became
 * {@code name/ip/players/motd/ping/iconBytes}.
 */
public class ServerData extends net.minecraft.client.multiplayer.ServerData implements IServerData {

	public ServerData(String serverIP) {
		super(serverIP, serverIP, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
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
		if (players == null) {
			return "";
		}
		return players.online() + "/" + players.max();
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
		byte[] icon = getIconBytes();
		return icon == null ? null : Base64.getEncoder().encodeToString(icon);
	}
}
