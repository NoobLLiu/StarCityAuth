package com.authid.command;

import com.authid.AuthIdPlugin;
import com.authid.identity.IdentityManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuthIdCommand implements CommandExecutor, Listener {

    private static final String GUI_TITLE = "§6§lAuthId - 身份选择";

    private final AuthIdPlugin plugin;
    private final IdentityManager identityManager;

    public AuthIdCommand(AuthIdPlugin plugin, IdentityManager identityManager) {
        this.plugin = plugin;
        this.identityManager = identityManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            openIdentityGui(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /authid create <名称>").color(NamedTextColor.RED));
                    return true;
                }
                String name = args[1];
                createIdentity(player, name);
            }
            case "select" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /authid select <编号>").color(NamedTextColor.RED));
                    return true;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    selectIdentity(player, id);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("编号必须是数字").color(NamedTextColor.RED));
                }
            }
            case "list" -> listIdentities(player);
            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /authid delete <编号>").color(NamedTextColor.RED));
                    return true;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    deleteIdentity(player, id);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("编号必须是数字").color(NamedTextColor.RED));
                }
            }
            default -> {
                player.sendMessage(Component.text("未知子命令。用法: /authid [create|select|list|delete]").color(NamedTextColor.RED));
            }
        }
        return true;
    }

    private void createIdentity(Player player, String name) {
        sendDebug(player, "§e[AuthId] §7正在创建身份: §f" + name);

        if (identityManager.getPlayer(player.getName()).identities.size() >= 3) {
            sendDebug(player, "§c[AuthId] §7已达到最大身份数量(3)，无法创建更多");
            return;
        }

        IdentityManager.Identity identity = identityManager.createIdentity(player.getName(), name);
        if (identity == null) {
            sendDebug(player, "§c[AuthId] §7创建失败");
            return;
        }

        sendDebug(player, "§a[AuthId] §7身份创建成功！");
        sendDebug(player, "§e[AuthId] §7  编号: §f" + identity.id);
        sendDebug(player, "§e[AuthId] §7  名称: §f" + identity.name);
        sendDebug(player, "§e[AuthId] §7  UUID: §f" + identity.uuid);

        // Auto-select if it's the first identity
        if (identityManager.getPlayer(player.getName()).identities.size() == 1) {
            sendDebug(player, "§e[AuthId] §7这是你的第一个身份，自动选择...");
            selectIdentity(player, identity.id);
        } else {
            sendDebug(player, "§e[AuthId] §7执行 §f/authid select " + identity.id + " §7切换到此身份");
        }
    }

    private void selectIdentity(Player player, int identityId) {
        sendDebug(player, "§e[AuthId] §7正在选择身份 #" + identityId + "...");

        boolean success = identityManager.selectIdentity(player.getName(), identityId);
        if (!success) {
            sendDebug(player, "§c[AuthId] §7身份 #" + identityId + " 不存在");
            return;
        }

        UUID targetUuid = identityManager.getSelectedIdentityUuid(player.getName());
        UUID currentUuid = player.getUniqueId();

        sendDebug(player, "§a[AuthId] §7身份选择成功！");
        sendDebug(player, "§e[AuthId] §7  当前 UUID: §f" + currentUuid);
        sendDebug(player, "§e[AuthId] §7  目标 UUID: §f" + targetUuid);

        if (targetUuid != null && targetUuid.equals(currentUuid)) {
            sendDebug(player, "§a[AuthId] §7当前 UUID 已经是目标身份，无需切换");
            return;
        }

        sendDebug(player, "§e[AuthId] §7正在切换身份，需要重新连接...");
        sendDebug(player, "§e[AuthId] §7下次连接将自动使用身份 #" + identityId + " 的 UUID");

        // Kick player to apply new UUID on reconnect
        player.kick(Component.text("§a§l身份已切换！\n\n§7请重新连接服务器以应用新身份。\n§7身份 UUID: §f" + targetUuid, NamedTextColor.GREEN));
    }

    private void listIdentities(Player player) {
        sendDebug(player, "§e[AuthId] §7===== 身份列表 =====");
        IdentityManager.PlayerIdentities pid = identityManager.getPlayer(player.getName());

        if (pid.identities.isEmpty()) {
            sendDebug(player, "§7  （空）");
            sendDebug(player, "§e[AuthId] §7执行 §f/authid create <名称> §7创建新身份");
        } else {
            for (IdentityManager.Identity id : pid.identities) {
                boolean selected = id.id == pid.selectedIdentityId;
                String prefix = selected ? "§a✔ " : "§7  ";
                sendDebug(player, prefix + "§f" + id.id + ". " + id.name + " §7(UUID: " + id.uuid + ")");
            }
        }
        sendDebug(player, "§e[AuthId] §7====================");
    }

    private void deleteIdentity(Player player, int identityId) {
        sendDebug(player, "§e[AuthId] §7正在删除身份 #" + identityId + "...");
        boolean success = identityManager.deleteIdentity(player.getName(), identityId);
        if (success) {
            sendDebug(player, "§a[AuthId] §7身份 #" + identityId + " 已删除");
        } else {
            sendDebug(player, "§c[AuthId] §7身份 #" + identityId + " 不存在");
        }
    }

    private void openIdentityGui(Player player) {
        sendDebug(player, "§e[AuthId] §7打开身份管理界面...");

        Inventory gui = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE));

        // Fill border with gray glass pane
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glass.setItemMeta(glassMeta);

        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glass);
        }

        // Set identity items
        IdentityManager.PlayerIdentities pid = identityManager.getPlayer(player.getName());
        List<IdentityManager.Identity> identities = pid.identities;

        int[] slots = {10, 13, 16}; // Slots for 3 identities
        for (int i = 0; i < Math.min(identities.size(), 3); i++) {
            IdentityManager.Identity identity = identities.get(i);
            boolean selected = identity.id == pid.selectedIdentityId;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
            skullMeta.displayName(Component.text((selected ? "§a✔ " : "§f") + identity.name));

            List<String> lore = new ArrayList<>();
            lore.add("§7编号: " + identity.id);
            lore.add("§7UUID: " + identity.uuid);
            lore.add("§7创建时间: " + formatTime(identity.createdAt));
            if (identity.lastUsedAt > 0) {
                lore.add("§7上次使用: " + formatTime(identity.lastUsedAt));
            }
            lore.add("");
            if (selected) {
                lore.add("§a§l当前使用中");
            } else {
                lore.add("§e§l点击切换到此身份");
            }
            skullMeta.lore(lore.stream().map(Component::text).toList());

            skull.setItemMeta(skullMeta);
            gui.setItem(slots[i], skull);
        }

        // Create button (slot 22)
        if (identities.size() < 3) {
            ItemStack createBtn = new ItemStack(Material.EMERALD);
            ItemMeta createMeta = createBtn.getItemMeta();
            createMeta.displayName(Component.text("§a创建新身份"));
            List<String> createLore = new ArrayList<>();
            createLore.add("§7创建一个新的游戏身份");
            createLore.add("§7当前已有 §f" + identities.size() + "/3 §7个身份");
            createLore.add("");
            createLore.add("§e§l点击创建");
            createMeta.lore(createLore.stream().map(Component::text).toList());
            createBtn.setItemMeta(createMeta);
            gui.setItem(22, createBtn);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        // Identity slots
        if (slot == 10 || slot == 13 || slot == 16) {
            int index = slot == 10 ? 0 : (slot == 13 ? 1 : 2);
            IdentityManager.PlayerIdentities pid = identityManager.getPlayer(player.getName());
            if (index < pid.identities.size()) {
                player.closeInventory();
                selectIdentity(player, pid.identities.get(index).id);
            }
        }

        // Create button
        if (slot == 22) {
            player.closeInventory();
            player.sendMessage(Component.text("§e[AuthId] §7请输入新身份的名称:"));
            player.sendMessage(Component.text("§e[AuthId] §7执行: §f/authid create <名称>"));
        }
    }

    private void sendDebug(Player player, String message) {
        player.sendMessage(Component.text(message));
    }

    private String formatTime(long timestamp) {
        if (timestamp == 0) return "从未";
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(timestamp));
    }
}
