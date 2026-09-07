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
        // Paper 的 PlayerHandshakeEvent 不提供用户名（用户名在随后的 Login Start 包中才发送）。
        // 无法拿到用户名时无法按名字查找身份，只能跳过重映射，避免用错误的名字生成 UUID。
        String rawName = extractUsernameFromHandshake(event.getOriginalHandshake());
        if (rawName == null || rawName.isBlank()) {
            plugin.getLogger().info("[AuthId] Handshake: username not available in handshake event; skipping identity remap");
            return;
        }
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

    /**
     * 从原始握手字符串中提取用户名（若存在）。
     * 标准 Java 客户端的握手地址为 "host:port" 或 "host:port\u0000ip:port"，
     * 不包含用户名；仅当首段看起来像纯用户名（3-16 位字母数字下划线）时才返回。
     */
    private String extractUsernameFromHandshake(String originalHandshake) {
        if (originalHandshake == null || originalHandshake.isBlank()) return null;
        String first = originalHandshake.split("\u0000", 2)[0].trim();
        return first.matches("[A-Za-z0-9_]{3,16}") ? first : null;
    }
}
