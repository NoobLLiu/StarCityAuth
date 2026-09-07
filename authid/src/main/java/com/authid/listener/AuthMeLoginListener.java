package com.authid.listener;

import com.authid.AuthIdPlugin;
import com.authid.identity.IdentityManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public final class AuthMeLoginListener implements Listener {

    private final AuthIdPlugin plugin;
    private final IdentityManager identityManager;

    public AuthMeLoginListener(AuthIdPlugin plugin, IdentityManager identityManager) {
        this.plugin = plugin;
        this.identityManager = identityManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAuthMeLogin(org.bukkit.event.player.PlayerJoinEvent event) {
        // We listen to PlayerJoinEvent and check AuthMe status after a delay
        // because AuthMe LoginEvent may not be directly accessible as a compile-time dependency
        Player player = event.getPlayer();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // Debug: show current state
            sendDebug(player, "§e[AuthId] §7PlayerJoinEvent 已触发，开始检查 AuthMe 状态...");

            boolean authenticated = isAuthMeAuthenticated(player.getName());
            sendDebug(player, "§e[AuthId] §7AuthMe 认证状态: " + (authenticated ? "§a已认证" : "§c未认证"));

            if (!authenticated) {
                sendDebug(player, "§e[AuthId] §7等待 AuthMe 登录完成...");
                // Schedule a check loop
                waitForAuthMeLogin(player, 0);
                return;
            }

            onAuthMeLoginComplete(player);
        }, 60L); // 3 seconds after join
    }

    private void waitForAuthMeLogin(Player player, int attempt) {
        if (!player.isOnline()) return;
        if (attempt > 60) { // 30 seconds max
            sendDebug(player, "§c[AuthId] §7等待 AuthMe 登录超时");
            return;
        }

        boolean authenticated = isAuthMeAuthenticated(player.getName());
        if (authenticated) {
            sendDebug(player, "§e[AuthId] §7AuthMe 登录完成！");
            onAuthMeLoginComplete(player);
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            waitForAuthMeLogin(player, attempt + 1);
        }, 10L); // Check every 0.5 seconds
    }

    private void onAuthMeLoginComplete(Player player) {
        String playerName = player.getName();
        UUID currentUuid = player.getUniqueId();

        sendDebug(player, "§e[AuthId] §7当前 UUID: §f" + currentUuid);
        sendDebug(player, "§e[AuthId] §7玩家名称: §f" + playerName);

        // Check email
        String email = getAuthMeEmail(playerName);
        sendDebug(player, "§e[AuthId] §7AuthMe 邮箱: " + (email != null ? "§a" + email : "§c未绑定"));

        if (email == null || email.isEmpty()) {
            sendDebug(player, "§c[AuthId] §7请先绑定邮箱: §f/email add <邮箱> <确认邮箱>");
            sendDebug(player, "§c[AuthId] §7绑定邮箱后，执行 §f/authid §7打开身份管理");
            return;
        }

        // Check identities
        boolean hasIdentity = identityManager.hasIdentity(playerName);
        sendDebug(player, "§e[AuthId] §7已有身份: " + (hasIdentity ? "§a是" : "§c否"));

        if (!hasIdentity) {
            sendDebug(player, "§e[AuthId] §7你还没有创建身份，请执行: §f/authid create <名称>");
            sendDebug(player, "§e[AuthId] §7例如: §f/authid create 战士");
            return;
        }

        // Check selected identity
        UUID selectedUuid = identityManager.getSelectedIdentityUuid(playerName);
        sendDebug(player, "§e[AuthId] §7已选择身份 UUID: " + (selectedUuid != null ? "§f" + selectedUuid : "§c未选择"));

        if (selectedUuid != null && selectedUuid.equals(currentUuid)) {
            sendDebug(player, "§a[AuthId] §7当前 UUID 与选择的身份一致，无需切换");
            sendDebug(player, "§a[AuthId] §7身份系统就绪！");
            return;
        }

        // Show identity list
        sendDebug(player, "§e[AuthId] §7你的身份列表:");
        IdentityManager.PlayerIdentities pid = identityManager.getPlayer(playerName);
        for (IdentityManager.Identity id : pid.identities) {
            boolean selected = id.id == pid.selectedIdentityId;
            String prefix = selected ? "§a✔ " : "§7  ";
            sendDebug(player, prefix + "§f" + id.id + ". " + id.name + " §7(" + id.uuid + ")");
        }

        if (selectedUuid == null) {
            sendDebug(player, "§e[AuthId] §7请执行 §f/authid select <编号> §7选择身份");
        } else {
            sendDebug(player, "§e[AuthId] §7身份已切换，请执行 §f/authid select <编号> §7重新选择");
        }
    }

    private boolean isAuthMeAuthenticated(String playerName) {
        try {
            Class<?> authMeApiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
            Object api = authMeApiClass.getMethod("getInstance").invoke(null);
            Method isAuthenticatedMethod = authMeApiClass.getMethod("isAuthenticated", String.class);
            return (boolean) isAuthenticatedMethod.invoke(api, playerName);
        } catch (Exception e) {
            plugin.getLogger().fine("[AuthId] AuthMe API check failed: " + e.getMessage());
            return false;
        }
    }

    private String getAuthMeEmail(String playerName) {
        try {
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
            plugin.getLogger().fine("[AuthId] AuthMe API email check failed: " + e.getMessage());
        }
        return null;
    }

    private void sendDebug(Player player, String message) {
        player.sendMessage(Component.text(message));
    }
}
