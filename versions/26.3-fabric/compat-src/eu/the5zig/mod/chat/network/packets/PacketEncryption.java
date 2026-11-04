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

// 26.3 core overlay: this file mirrors core/eu/the5zig/mod/chat/network/packets/PacketEncryption.java
// and adapts it to authlib 10 (GameProfile#id()/#name()), authlib 10 having deleted the whole
// com.mojang.authlib.yggdrasil package (MinecraftSessionService is now
// com.mojang.authlib.minecraft.SessionService and there is no YggdrasilAuthenticationService),
// plus commons-lang3 3.19 (org.apache.commons.lang3.Strings collides with Guava's Strings).
// core/ is untouched.


package eu.the5zig.mod.chat.network.packets;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.SessionService;
import eu.the5zig.mod.I18n;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.chat.network.CryptManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.math.BigInteger;
import java.security.PublicKey;
import java.util.UUID;

public class PacketEncryption implements Packet {

	private PublicKey publicKey;
	private byte[] verifyToken;

	private byte[] secretKeyEncrypted;
	private byte[] verifyTokenEncrypted;

	public PacketEncryption(SecretKey secretKey, PublicKey publicKey, byte[] verifyToken) {
		this.secretKeyEncrypted = CryptManager.encryptData(publicKey, secretKey.getEncoded());
		this.verifyTokenEncrypted = CryptManager.encryptData(publicKey, verifyToken);
	}

	public PacketEncryption() {
	}

	@Override
	public void read(ByteBuf buffer) throws IOException {
		this.publicKey = CryptManager.decodePublicKey(PacketBuffer.readByteArray(buffer));
		this.verifyToken = PacketBuffer.readByteArray(buffer);
	}

	@Override
	public void write(ByteBuf buffer) throws IOException {
		PacketBuffer.writeByteArray(buffer, secretKeyEncrypted);
		PacketBuffer.writeByteArray(buffer, verifyTokenEncrypted);
	}

	@Override
	public void handle() {
		final SecretKey secretKey = CryptManager.createNewSharedKey();
		String hash = (new BigInteger(CryptManager.getServerIdHash("", publicKey, secretKey))).toString(16);
		// authlib 10 dropped YggdrasilAuthenticationService and renamed MinecraftSessionService to
		// SessionService. Minecraft already owns a configured instance (Services#sessionService),
		// so borrow that instead of building a private client; joinServer's signature is unchanged.
		SessionService sessionService = Minecraft.getInstance().services().sessionService();
		try {
			// authlib 7+: joinServer takes the profile id, not the GameProfile.
			sessionService.joinServer(The5zigMod.getVars().getGameProfile().id(), The5zigMod.getDataManager().getSession(), hash);
		} catch (AuthenticationException e) {
			The5zigMod.getNetworkManager().disconnect(I18n.translate("connection.bad_login"));
			throw new RuntimeException(e);
		}
		The5zigMod.getNetworkManager().sendPacket(new PacketEncryption(secretKey, publicKey, verifyToken), new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture channelFuture) throws Exception {
				The5zigMod.getNetworkManager().enableEncryption(secretKey);
			}
		});
	}
}
