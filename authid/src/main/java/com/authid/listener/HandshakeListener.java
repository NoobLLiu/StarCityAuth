package com.authid.listener;

import com.authid.AuthIdConfig;
import com.authid.AuthIdPlugin;
import com.authid.PlayerChannel;
import com.authid.ResolvedIdentity;
import com.authid.identity.IdentityManager;
import com.authid.resolver.IdentityResolver;
import com.destroystokyo.paper.event.player.PlayerHandshakeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public final class HandshakeListener implements Listener {

    private final AuthIdPlugin plugin;
    private final IdentityResolver resolver;
    private final AuthIdConfig config;
    private final IdentityManager identityManager;

    public HandshakeListener(AuthIdPlugin plugin, IdentityResolver resolver, AuthIdConfig config, IdentityManager identityManager) {
        this.plugin = plugin;
        this.resolver = resolver;
        this.config = config;
        this.identityManager = identityManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHandshake(PlayerHandshakeEvent event) {
        String rawName = event.getUsername();
        UUID originalUuid = event.getUniqueId();
        String serverHostname = event.getServerHostname();

        plugin.getLogger().info("[AuthId] Handshake: " + rawName + " originalUuid=" + originalUuid);

        // Check if player has a selected identity UUID
        UUID identityUuid = identityManager.getSelectedIdentityUuid(rawName);
        if (identityUuid != null) {
            plugin.getLogger().info("[AuthId] Found cached identity for " + rawName + ": " + identityUuid);
            if (!identityUuid.equals(originalUuid)) {
                event.setUniqueId(identityUuid);
                plugin.getLogger().info("[AuthId] UUID remapped (identity): " + originalUuid + " -> " + identityUuid);
            }
            return;
        }

        // Fallback to channel-based resolution
        PlayerChannel channel = detectChannel(originalUuid, serverHostname);
        String geyserExtra = channel == PlayerChannel.BEDROCK_GEYSER ? extractGeyserExtra(serverHostname) : null;

        ResolvedIdentity identity = resolver.resolve(rawName, originalUuid, channel, geyserExtra);

        if (!identity.getCanonicalUuid().equals(originalUuid)) {
            event.setUniqueId(identity.getCanonicalUuid());
            plugin.getLogger().info("[AuthId] UUID remapped (channel): " + rawName
                    + " (" + channel.getId() + "): " + originalUuid + " -> " + identity.getCanonicalUuid());
        }
    }

    private PlayerChannel detectChannel(UUID uuid, String hostname) {
        if (uuid == null) {
            return PlayerChannel.BEDROCK_GEYSER;
        }
        if (isZeroUuid(uuid)) {
            return PlayerChannel.BEDROCK_GEYSER;
        }
        if (isGeyserHostname(hostname)) {
            return PlayerChannel.BEDROCK_GEYSER;
        }
        // Premium players have a valid Mojang UUID (version 3)
        // Offline players have a UUID generated from the username (version 3, specific pattern)
        // Heuristic: if UUID version is 3 and the server is in offline mode, it's offline
        // This is a simplified heuristic; the actual detection depends on server config
        return PlayerChannel.JAVA_OFFLINE;
    }

    private boolean isZeroUuid(UUID uuid) {
        return uuid.getMostSignificantBits() == 0L && uuid.getLeastSignificantBits() == 0L;
    }

    private boolean isGeyserHostname(String hostname) {
        if (hostname == null) return false;
        String lower = hostname.toLowerCase();
        return lower.contains("geyser") || lower.contains("bedrock");
    }

    private String extractGeyserExtra(String hostname) {
        if (hostname == null) return null;
        // Geyser may append extra info to the hostname
        return hostname;
    }
}
