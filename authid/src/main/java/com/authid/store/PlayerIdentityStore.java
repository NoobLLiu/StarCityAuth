package com.authid.store;

import com.authid.ResolvedIdentity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerIdentityStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("authid");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path storageFile;
    private final Map<UUID, StoredEntry> byChannelUuid = new ConcurrentHashMap<>();
    private final Map<UUID, StoredEntry> byCanonicalUuid = new ConcurrentHashMap<>();
    private final Map<String, StoredEntry> byMojangName = new ConcurrentHashMap<>();

    public PlayerIdentityStore(Path storageFile) {
        this.storageFile = storageFile;
    }

    public synchronized void load() {
        if (!Files.exists(storageFile)) {
            return;
        }
        try {
            String json = Files.readString(storageFile, StandardCharsets.UTF_8);
            if (json.isBlank()) return;
            Type listType = new TypeToken<List<StoredEntry>>() {}.getType();
            List<StoredEntry> entries = GSON.fromJson(json, listType);
            if (entries != null) {
                for (StoredEntry e : entries) {
                    index(e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[AuthId] Failed to load identity store: {}", e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(storageFile.getParent());
            List<StoredEntry> all = new ArrayList<>(byChannelUuid.values());
            String json = GSON.toJson(all);
            Files.writeString(storageFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[AuthId] Failed to save identity store: {}", e.getMessage());
        }
    }

    private void index(StoredEntry e) {
        byChannelUuid.put(e.channelUuid, e);
        byCanonicalUuid.put(e.canonicalUuid, e);
        if (e.mojangName != null && e.mojangVerified) {
            byMojangName.put(e.mojangName.toLowerCase(), e);
        }
    }

    public synchronized void upsert(ResolvedIdentity id) {
        StoredEntry existing = byChannelUuid.get(id.getChannelUuid());
        StoredEntry entry;
        if (existing != null) {
            entry = existing;
            entry.rawName = id.getRawName();
            entry.channelUuid = id.getChannelUuid();
            entry.channel = id.getChannel().getId();
            entry.canonicalUuid = id.getCanonicalUuid();
            entry.mojangName = id.getMojangName();
            entry.mojangVerified = id.isMojangVerified();
            entry.lastSeenAt = id.getLastSeenAt();
        } else {
            entry = new StoredEntry(
                    id.getRawName(),
                    id.getChannelUuid(),
                    id.getChannel().getId(),
                    id.getCanonicalUuid(),
                    id.getMojangName(),
                    id.isMojangVerified(),
                    id.getCreatedAt(),
                    id.getLastSeenAt()
            );
        }
        index(entry);
    }

    public StoredEntry findByChannelUuid(UUID channelUuid) {
        return byChannelUuid.get(channelUuid);
    }

    public StoredEntry findByCanonicalUuid(UUID canonicalUuid) {
        return byCanonicalUuid.get(canonicalUuid);
    }

    public StoredEntry findByMojangName(String mojangName) {
        if (mojangName == null) return null;
        return byMojangName.get(mojangName.toLowerCase());
    }

    public Optional<StoredEntry> findByCanonicalOrAnyLink(UUID canonicalUuid) {
        return Optional.ofNullable(byCanonicalUuid.get(canonicalUuid));
    }

    public List<StoredEntry> all() {
        return List.copyOf(byChannelUuid.values());
    }

    public int size() {
        return byChannelUuid.size();
    }

    public static final class StoredEntry {
        public String rawName;
        public UUID channelUuid;
        public String channel;
        public UUID canonicalUuid;
        public String mojangName;
        public boolean mojangVerified;
        public long createdAt;
        public long lastSeenAt;

        public StoredEntry() {}

        public StoredEntry(String rawName, UUID channelUuid, String channel, UUID canonicalUuid,
                           String mojangName, boolean mojangVerified, long createdAt, long lastSeenAt) {
            this.rawName = rawName;
            this.channelUuid = channelUuid;
            this.channel = channel;
            this.canonicalUuid = canonicalUuid;
            this.mojangName = mojangName;
            this.mojangVerified = mojangVerified;
            this.createdAt = createdAt;
            this.lastSeenAt = lastSeenAt;
        }
    }
}
