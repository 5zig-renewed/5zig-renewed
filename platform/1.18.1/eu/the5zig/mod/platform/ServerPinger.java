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
import eu.the5zig.mod.util.LegacyText;
import eu.the5zig.mod.util.IServerPinger;
import eu.the5zig.util.minecraft.ChatColor;
import net.minecraft.client.multiplayer.ServerStatusPinger;

import java.net.UnknownHostException;

/**
 * Pings servers for the mod's own server list (friends list server icons).
 * <p>
 * 1.15's {@code net.minecraft.client.network.ServerPinger} pinged synchronously and handed back a
 * filled {@code ServerData}. 1.18.1 renamed it to {@link ServerStatusPinger}, made the ping
 * asynchronous (the result is written into the {@code ServerData} once the netty connection
 * answers) and split it into {@code pingServer}/{@code tick}/{@code removeAll}. The
 * {@link IServerPinger} contract still holds: {@link #ping(IServerData)} starts a ping and
 * {@link #callPingPendingNetworks()} has to be ticked by the caller for it to progress.
 */
public class ServerPinger implements IServerPinger {

	private final ServerStatusPinger handle = new ServerStatusPinger();

	@Override
	public IServerData createServerData(String serverIP) {
		return new ServerData(serverIP);
	}

	@Override
	public void ping(IServerData serverData) {
		if (!(serverData instanceof net.minecraft.client.multiplayer.ServerData)) {
			return;
		}
		net.minecraft.client.multiplayer.ServerData data = (net.minecraft.client.multiplayer.ServerData) serverData;
		try {
			this.handle.pingServer(data, () -> {
			});
		} catch (UnknownHostException e) {
			data.ping = -1L;
			data.motd = LegacyText.fromLegacy(ChatColor.DARK_RED + "Can't resolve hostname");
		} catch (Exception e) {
			data.ping = -1L;
			data.motd = LegacyText.fromLegacy(ChatColor.DARK_RED + "Can't connect to server");
		}
	}

	@Override
	public void callPingPendingNetworks() {
		this.handle.tick();
	}

	@Override
	public void callClearPendingNetworks() {
		this.handle.removeAll();
	}
}
