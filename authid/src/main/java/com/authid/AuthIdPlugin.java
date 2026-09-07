package com.authid;

import com.authid.command.AuthIdCommand;
import com.authid.identity.IdentityManager;
import com.authid.listener.AuthMeLoginListener;
import com.authid.listener.HandshakeListener;
import com.authid.listener.PlayerJoinListener;
import com.authid.resolver.IdentityResolver;
import com.authid.resolver.MojangProfileResolver;
import com.authid.store.PlayerIdentityStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public final class AuthIdPlugin extends JavaPlugin {

    private AuthIdConfig config;
    private PlayerIdentityStore identityStore;
    private MojangProfileResolver mojangResolver;
    private IdentityResolver identityResolver;
    private IdentityManager identityManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = loadConfig();

        Path dataDir = getDataFolder().toPath();

        // Initialize identity manager (new system)
        identityManager = new IdentityManager(dataDir.resolve("player-identities.json"));
        identityManager.load();
        getLogger().info("[AuthId] IdentityManager loaded: " + identityManager.getPlayer("test").identities.size() + " test entries");

        // Initialize legacy identity store
        identityStore = new PlayerIdentityStore(dataDir.resolve("identities.json"));
        identityStore.load();

        mojangResolver = new MojangProfileResolver(config, dataDir.resolve("mojang-cache"));
        identityResolver = new IdentityResolver(identityStore, mojangResolver, config);

        // Register command
        AuthIdCommand authIdCommand = new AuthIdCommand(this, identityManager);
        getCommand("authid").setExecutor(authIdCommand);
        getServer().getPluginManager().registerEvents(authIdCommand, this);

        // Register listeners
        getServer().getPluginManager().registerEvents(
                new HandshakeListener(this, identityResolver, config, identityManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(this, identityStore), this);
        getServer().getPluginManager().registerEvents(
                new AuthMeLoginListener(this, identityManager), this);

        getLogger().info("[AuthId] Enabled. IdentityManager entries: " + identityManager.getPlayer("test").identities.size());
        getLogger().info("[AuthId] Legacy store: " + identityStore.size() + " identities.");
    }

    @Override
    public void onDisable() {
        if (identityManager != null) {
            identityManager.save();
        }
        if (identityStore != null) {
            identityStore.save();
        }
        if (mojangResolver != null) {
            mojangResolver.shutdown();
        }
        getLogger().info("[AuthId] Disabled.");
    }

    public IdentityResolver getIdentityResolver() {
        return identityResolver;
    }

    public PlayerIdentityStore getIdentityStore() {
        return identityStore;
    }

    public IdentityManager getIdentityManager() {
        return identityManager;
    }

    public AuthIdConfig getAuthIdConfig() {
        return config;
    }

    private AuthIdConfig loadConfig() {
        AuthIdConfig cfg = new AuthIdConfig();
        var fileConfig = getConfig();
        cfg.enableMojangLookup = fileConfig.getBoolean("mojang-lookup.enabled", cfg.enableMojangLookup);
        cfg.mojangCacheTtlMs = fileConfig.getLong("mojang-lookup.cache-ttl-ms", cfg.mojangCacheTtlMs);
        cfg.autoLinkByMojangName = fileConfig.getBoolean("mojang-lookup.auto-link-by-name", cfg.autoLinkByMojangName);
        return cfg;
    }
}
