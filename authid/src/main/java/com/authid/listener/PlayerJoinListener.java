package com.authid.listener;

import com.authid.AuthIdPlugin;
import com.authid.store.PlayerIdentityStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class PlayerJoinListener implements Listener {

    private final AuthIdPlugin plugin;
    private final PlayerIdentityStore store;

    public PlayerJoinListener(AuthIdPlugin plugin, PlayerIdentityStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID currentUuid = player.getUniqueId();

        // Save identity store periodically on player join
        store.save();

        // Check if player data migration is needed
        checkAndMigrateData(player, currentUuid);
    }

    private void checkAndMigrateData(Player player, UUID currentUuid) {
        PlayerIdentityStore.StoredEntry entry = store.findByCanonicalUuid(currentUuid);
        if (entry == null) return;

        // If the canonical UUID differs from the channel UUID, check if old data exists
        UUID channelUuid = entry.channelUuid;
        if (channelUuid.equals(currentUuid)) return;

        Path worldDir = plugin.getServer().getWorldContainer().toPath();
        Path playerDataDir = worldDir.resolve("world/playerdata");

        if (!Files.exists(playerDataDir)) return;

        Path oldData = playerDataDir.resolve(channelUuid.toString() + ".dat");
        Path newData = playerDataDir.resolve(currentUuid.toString() + ".dat");

        // Migrate if old data exists but new data doesn't
        if (Files.exists(oldData) && !Files.exists(newData)) {
            try {
                Files.copy(oldData, newData);
                plugin.getLogger().info("[AuthId] Migrated player data for " + player.getName()
                        + ": " + channelUuid + " -> " + currentUuid);
            } catch (Exception e) {
                plugin.getLogger().warning("[AuthId] Failed to migrate player data for " + player.getName()
                        + ": " + e.getMessage());
            }
        }
    }
}
