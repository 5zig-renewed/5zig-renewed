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

package eu.the5zig.mod.account;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.event.AccountSwitchEvent;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by RoccoDev on 2019-08-25.
 */
public class AccountManager {

    private static ExecutorService POOL;

    private static Gson gson;
    private static Type accountsType;

    private boolean encrypted;
    private String password;

    private boolean isNewManager;

    private ArrayList<Account> accounts;

    static {
        gson = new Gson();
        accountsType = new TypeToken<List<Account>>(){}.getType();

        POOL = Executors.newFixedThreadPool(1);
    }

    public AccountManager() {
        // authlib 6 (Minecraft 1.21.8) has no YggdrasilAuthenticationService/UserAuthentication:
        // Microsoft/Mojang authentication is owned by the launcher, not by mods. Only the offline
        // ("legacy") account entries of accounts.enc can still be used; see addAccount/login.
        isNewManager = !new File(The5zigMod.getModDirectory(), "accounts.enc").exists();
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isNewManager() {
        return isNewManager;
    }

    public ArrayList<Account> getAccounts() {
        return accounts;
    }

    public void addAccount(String email, String password) throws AuthenticationException {
        // Mojang's legacy Yggdrasil login was removed from authlib and is rejected server side
        // since the Microsoft account migration; there is no client-side API left to call.
        throw new AuthenticationException("Legacy Mojang account login is not available on Minecraft 1.21.8: " +
                "authlib 6 removed YggdrasilAuthenticationService. Use an offline account instead.");
    }

    public void addOfflineAccount(String username) {
        Account account = new Account(null, null, username, username, "legacy");
        accounts.add(account);

        The5zigMod.getVars().setSession(username, username, "0", "legacy");
        updateSettings();
        account.setSelected(true);
        try {
            save();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void login(Account account) throws AuthenticationException {
        if("legacy".equals(account.getAccountType())) {
            The5zigMod.getVars().setSession(account.getName(), account.getName(), "0", "legacy");
        }
        else {
            throw new AuthenticationException("Online account switching is not available on Minecraft 1.21.8: " +
                    "authlib 6 removed the legacy Yggdrasil client. Select the account in the launcher.");
        }
        account.setSelected(true);
        updateSettings();
        The5zigMod.getOverlayMessage().displayMessage("Login Success", "Logged in as " + account.getName());
    }

    public void save() {
        JsonObject json = new JsonObject();

        json.add("accounts", gson.toJsonTree(accounts, accountsType));

        POOL.execute(() -> {
            try {
                AccountsFileEncryption.encrypt(password, json);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateSettings() {
        The5zigMod.reloadDataManager();
        The5zigMod.getNetworkManager().reloadNow();
        The5zigMod.getListener().fireEvent(new AccountSwitchEvent());
        if(The5zigMod.getVars().getServer() != null) {
            String host = The5zigMod.getVars().getServer();
            int port = 25565;
            if(host.contains(":")) {
                String[] data = host.split(":");
                host = data[0];
                port = Integer.parseInt(data[1]);
            }
            The5zigMod.getVars().joinServer(host, port);
        }
    }

    public void read() throws Exception {
        if(isNewManager) {
            create();
            return;
        }

        JsonObject json = AccountsFileEncryption.decrypt(password);
        accounts = gson.fromJson(json.getAsJsonArray("accounts"), accountsType);

        Optional<Account> self = accounts.stream().filter(account -> {
            if(The5zigMod.getDataManager().getGameProfile().getId() != null) {
                return The5zigMod.getDataManager().getGameProfile().getId().toString().equals(account.getUuid());
            }
            return false;
        }).findAny();
        self.ifPresent(account -> account.setSelected(true));
    }

    private void create() throws Exception {
        accounts = new ArrayList<>();
        new File(The5zigMod.getModDirectory(), "accounts.enc").createNewFile();
        save();
    }

}
