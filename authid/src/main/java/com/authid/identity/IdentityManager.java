package com.authid.identity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IdentityManager {

    private static final int MAX_IDENTITIES = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path storageFile;
    private final Map<String, PlayerIdentities> data = new ConcurrentHashMap<>();

    public IdentityManager(Path storageFile) {
        this.storageFile = storageFile;
    }

    public synchronized void load() {
        if (!Files.exists(storageFile)) return;
        try {
            String json = Files.readString(storageFile, StandardCharsets.UTF_8);
            if (json.isBlank()) return;
            Type type = new TypeToken<Map<String, PlayerIdentities>>() {}.getType();
            Map<String, PlayerIdentities> loaded = GSON.fromJson(json, type);
            if (loaded != null) {
                data.putAll(loaded);
            }
        } catch (IOException e) {
            System.err.println("[AuthId] Failed to load identity manager: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(storageFile.getParent());
            Files.writeString(storageFile, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[AuthId] Failed to save identity manager: " + e.getMessage());
        }
    }

    public PlayerIdentities getPlayer(String playerName) {
        return data.computeIfAbsent(playerName.toLowerCase(), k -> new PlayerIdentities());
    }

    public boolean hasIdentity(String playerName) {
        PlayerIdentities pid = data.get(playerName.toLowerCase());
        return pid != null && !pid.identities.isEmpty();
    }

    public UUID getSelectedIdentityUuid(String playerName) {
        PlayerIdentities pid = data.get(playerName.toLowerCase());
        if (pid == null || pid.selectedIdentityId == 0) return null;
        for (Identity id : pid.identities) {
            if (id.id == pid.selectedIdentityId) return UUID.fromString(id.uuid);
        }
        return null;
    }

    public Identity createIdentity(String playerName, String identityName) {
        PlayerIdentities pid = getPlayer(playerName);
        if (pid.identities.size() >= MAX_IDENTITIES) return null;

        int nextId = pid.identities.stream().mapToInt(i -> i.id).max().orElse(0) + 1;
        UUID uuid = UUID.nameUUIDFromBytes(("authid:identity:" + playerName.toLowerCase() + ":" + identityName).getBytes(StandardCharsets.UTF_8));

        Identity identity = new Identity();
        identity.id = nextId;
        identity.name = identityName;
        identity.uuid = uuid.toString();
        identity.createdAt = System.currentTimeMillis();

        pid.identities.add(identity);
        save();
        return identity;
    }

    public boolean selectIdentity(String playerName, int identityId) {
        PlayerIdentities pid = getPlayer(playerName);
        for (Identity id : pid.identities) {
            if (id.id == identityId) {
                pid.selectedIdentityId = identityId;
                id.lastUsedAt = System.currentTimeMillis();
                save();
                return true;
            }
        }
        return false;
    }

    public boolean deleteIdentity(String playerName, int identityId) {
        PlayerIdentities pid = getPlayer(playerName);
        boolean removed = pid.identities.removeIf(i -> i.id == identityId);
        if (removed) {
            if (pid.selectedIdentityId == identityId) {
                pid.selectedIdentityId = 0;
            }
            save();
        }
        return removed;
    }

    public static class PlayerIdentities {
        public List<Identity> identities = new ArrayList<>();
        public int selectedIdentityId = 0;
    }

    public static class Identity {
        public int id;
        public String name;
        public String uuid;
        public long createdAt;
        public long lastUsedAt = 0;
    }
}
