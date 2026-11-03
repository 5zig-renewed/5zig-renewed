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

// 26.x core overlay: this file mirrors core/eu/the5zig/mod/account/AccountManager.java.
// authlib 7 removed UserAuthentication, Agent and the whole e-mail/password Yggdrasil client
// (Mojang accounts are Microsoft-only since 2021), so the password login path cannot exist any
// more. The account *list* (local accounts, offline names, persistence) is kept as-is; the
// password operations fail with a precise message instead of pretending to work.
package eu.the5zig.mod.account;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
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
        accountsType = new TypeToken<List<Account>>() {
        }.getType();

        POOL = Executors.newFixedThreadPool(1);
    }

    public AccountManager() {
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

    /**
     * @throws UnsupportedOperationException always: 26.x has no password authentication any more.
     */
    public void addAccount(String email, String password) throws AuthenticationException {
        throw new UnsupportedOperationException("5zig 26.2: e-mail/password account login is not available - "
                + "Minecraft 26.x uses Microsoft authentication (authlib 7 removed UserAuthentication)");
    }

    public void addOfflineAccount(String username) {
        // Offline accounts only swap the local session; they never talk to the auth servers, so they
        // keep working unchanged.
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
        if ("legacy".equals(account.getAccountType())) {
            The5zigMod.getVars().setSession(account.getName(), account.getName(), "0", "legacy");
        } else {
            throw new UnsupportedOperationException("5zig 26.2: switching to the online account '"
                    + account.getEmail() + "' needs a Microsoft sign-in, which this build cannot perform");
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
        if (The5zigMod.getVars().getServer() != null) {
            String host = The5zigMod.getVars().getServer();
            int port = 25565;
            if (host.contains(":")) {
                String[] data = host.split(":");
                host = data[0];
                port = Integer.parseInt(data[1]);
            }
            The5zigMod.getVars().joinServer(host, port);
        }
    }

    public void read() throws Exception {
        if (isNewManager) {
            create();
            return;
        }

        JsonObject json = AccountsFileEncryption.decrypt(password);
        accounts = gson.fromJson(json.getAsJsonArray("accounts"), accountsType);

        Optional<Account> self = accounts.stream().filter(account -> {
            if (The5zigMod.getDataManager().getGameProfile().id() != null) {
                return The5zigMod.getDataManager().getGameProfile().id().toString().equals(account.getUuid());
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
