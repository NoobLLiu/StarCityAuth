package com.authid.listener;

import com.authid.AuthIdPlugin;
import com.authid.store.PlayerIdentityStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

        // Test demo: show UUID and identity info
        sendIdentityInfo(player, currentUuid);
    }

    private void sendIdentityInfo(Player player, UUID currentUuid) {
        // Delay slightly to ensure AuthMe login is complete
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("===== AuthId 身份信息 =====", NamedTextColor.GOLD));
            player.sendMessage(Component.text("当前 UUID: ", NamedTextColor.YELLOW)
                    .append(Component.text(currentUuid.toString(), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("玩家名称: ", NamedTextColor.YELLOW)
                    .append(Component.text(player.getName(), NamedTextColor.WHITE)));

            // Check identity store
            PlayerIdentityStore.StoredEntry entry = store.findByCanonicalUuid(currentUuid);
            if (entry != null) {
                player.sendMessage(Component.text("绑定身份: ", NamedTextColor.YELLOW)
                        .append(Component.text(entry.rawName, NamedTextColor.WHITE)));
                player.sendMessage(Component.text("原始渠道: ", NamedTextColor.YELLOW)
                        .append(Component.text(entry.channel, NamedTextColor.WHITE)));
                player.sendMessage(Component.text("原始 UUID: ", NamedTextColor.YELLOW)
                        .append(Component.text(entry.channelUuid.toString(), NamedTextColor.WHITE)));
                player.sendMessage(Component.text("Mojang 验证: ", NamedTextColor.YELLOW)
                        .append(Component.text(entry.mojangVerified ? "是" : "否",
                                entry.mojangVerified ? NamedTextColor.GREEN : NamedTextColor.RED)));
            } else {
                player.sendMessage(Component.text("绑定身份: ", NamedTextColor.YELLOW)
                        .append(Component.text("无（首次登录）", NamedTextColor.GRAY)));
            }

            // Check AuthMe email
            String authmeEmail = getAuthMeEmail(player.getName());
            player.sendMessage(Component.text("AuthMe 邮箱: ", NamedTextColor.YELLOW)
                    .append(Component.text(authmeEmail != null ? authmeEmail : "未绑定",
                            authmeEmail != null ? NamedTextColor.GREEN : NamedTextColor.RED)));

            player.sendMessage(Component.text("========================", NamedTextColor.GOLD));
            player.sendMessage(Component.text(""));
        }, 40L); // 2 seconds delay
    }

    private String getAuthMeEmail(String playerName) {
        try {
            // Use AuthMe API to get email
            Class<?> authMeApiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
            Object api = authMeApiClass.getMethod("getInstance").invoke(null);
            Object auth = authMeApiClass.getMethod("getAuth", String.class).invoke(api, playerName);
            if (auth != null) {
                Object email = auth.getClass().getMethod("getEmail").invoke(auth);
                if (email instanceof String emailStr && !emailStr.isEmpty()) {
                    return emailStr;
                }
            }
        } catch (Exception e) {
            // AuthMe not available or other error
            plugin.getLogger().fine("[AuthId] AuthMe API not available: " + e.getMessage());
        }
        return null;
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
