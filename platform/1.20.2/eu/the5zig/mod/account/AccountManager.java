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
import com.mojang.authlib.exceptions.AuthenticationException;
import eu.the5zig.mod.The5zigMod;
import eu.the5zig.mod.event.AccountSwitchEvent;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 1.20.2 build of the account manager.
 * <p>
 * The 1.20.1 implementation logged in through {@code YggdrasilAuthenticationService#createUserAuthentication}
 * plus {@code UserAuthentication} (email/password against the legacy Yggdrasil endpoint). authlib
 * 5.0.47, which Minecraft 1.20.2 ships, already removed both types together with the
 * {@code (Proxy, String)} constructor (the same removals that authlib 6 is known for): authentication
 * is Microsoft-only and is owned by the launcher, and the client can no longer mint a session for
 * another account. The account list, its encryption-at-rest and the "log in as this offline name"
 * path are kept unchanged; the legacy password login reports {@link AuthenticationException} instead
 * of silently doing nothing.
 * <p>
 * This class lives in the 1.20.2 platform because the core copy cannot compile against authlib 5 and
 * must not be edited for a single version (it still has to build for 1.20.1).
 */
public class AccountManager {

    private static ExecutorService POOL;

    private static Gson gson;
    private static Type accountsType;

    private String password;

    private boolean isNewManager;

    private ArrayList<Account> accounts;

    static {
        gson = new Gson();
        accountsType = new TypeToken<List<Account>>(){}.getType();

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
     * @throws AuthenticationException always: Minecraft 1.20.2 (authlib 5) has no email/password login.
     */
    public void addAccount(String email, String password) throws AuthenticationException {
        throw new AuthenticationException("Minecraft 1.20.2 only supports Microsoft accounts; "
                + "add the account in the launcher instead of in the 5zig account manager");
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
        if ("legacy".equals(account.getAccountType())) {
            The5zigMod.getVars().setSession(account.getName(), account.getName(), "0", "legacy");
        } else {
            throw new AuthenticationException("Minecraft 1.20.2 only supports Microsoft accounts; "
                    + "the session is managed by the launcher");
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
