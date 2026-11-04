/*
 * Copyright (c) 2019-2020 5zig Reborn
 * Copyright (c) 2015-2019 5zig
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

// 26.x core overlay: this file mirrors core/eu/the5zig/mod/modules/items/server/ServerPing.java
// and only adapts it to authlib 7 (GameProfile#id()/#name()) and commons-lang3 3.19
// (org.apache.commons.lang3.Strings collides with Guava's Strings). core/ is untouched.


package eu.the5zig.mod.modules.items.server;

import com.mojang.authlib.GameProfile;
import eu.the5zig.mod.I18n;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.modules.StringItem;
import eu.the5zig.mod.util.NetworkPlayerInfo;
import eu.the5zig.util.minecraft.ChatColor;

public class ServerPing extends StringItem {

	private long lastPinged;
	private int ping;

	@Override
	protected Object getValue(boolean dummy) {
		if (dummy) {
			return 27;
		}
		if (The5zigMod.getDataManager().getServer() == null) {
			return null;
		}
		int ping = checkPing();
		return ping != 0 ? ping : I18n.translate("ingame.pinging");
	}

	private int checkPing() {
		long l = System.currentTimeMillis();
		if (l - lastPinged < 1000) {
			return ping;
		}
		lastPinged = l;

		GameProfile gameProfile = The5zigMod.getDataManager().getGameProfile();
		for (NetworkPlayerInfo networkPlayerInfo : The5zigMod.getVars().getServerPlayers()) {
			if (gameProfile.equals(networkPlayerInfo.getGameProfile()) || gameProfile.name().equals(ChatColor.stripColor(networkPlayerInfo.getDisplayName()))
					|| (networkPlayerInfo.getGameProfile() != null && gameProfile.name().equals(networkPlayerInfo.getGameProfile().name()))) {
				if (networkPlayerInfo.getPing() <= 0) {
					ping = 0;
				} else {
					ping = networkPlayerInfo.getPing();
				}
				break;
			}
		}

		return ping;
	}

	@Override
	public String getTranslation() {
		return "ingame.ping";
	}
}
