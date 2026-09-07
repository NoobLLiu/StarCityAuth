package com.authid;

import com.authid.resolver.ChannelUUIDGenerator;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = loadConfig();

        Path dataDir = getDataFolder().toPath();
        identityStore = new PlayerIdentityStore(dataDir.resolve("identities.json"));
        identityStore.load();

        mojangResolver = new MojangProfileResolver(config, dataDir.resolve("mojang-cache"));
        identityResolver = new IdentityResolver(identityStore, mojangResolver, config);

        getServer().getPluginManager().registerEvents(
                new com.authid.listener.HandshakeListener(this, identityResolver, config), this);
        getServer().getPluginManager().registerEvents(
                new com.authid.listener.PlayerJoinListener(this, identityStore), this);

        getLogger().info("[AuthId] Enabled. Tracking " + identityStore.size() + " identities.");
    }

    @Override
    public void onDisable() {
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
