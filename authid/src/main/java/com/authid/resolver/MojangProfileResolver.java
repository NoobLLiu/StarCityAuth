package com.authid.resolver;

import com.authid.AuthIdConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MojangProfileResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("authid");
    private static final String MOJANG_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final Gson GSON = new Gson();

    private final AuthIdConfig config;
    private final Path cacheDir;
    private final Map<String, CacheEntry> inMemory = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "authid-mojang-io");
        t.setDaemon(true);
        return t;
    });

    public MojangProfileResolver(AuthIdConfig config, Path cacheDir) {
        this.config = config;
        this.cacheDir = cacheDir;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public boolean isEnabled() {
        return config.enableMojangLookup;
    }

    public UUID lookup(String name) {
        if (!config.enableMojangLookup || name == null || name.isBlank()) return null;
        CacheEntry entry = inMemory.get(name.toLowerCase());
        if (entry != null && !entry.isExpired(config.mojangCacheTtlMs)) {
            return entry.uuid;
        }
        UUID disk = readDiskCache(name);
        if (disk != null) {
            CacheEntry e = new CacheEntry(disk, System.currentTimeMillis());
            inMemory.put(name.toLowerCase(), e);
            return disk;
        }
        return null;
    }

    public UUID fetch(String name) {
        UUID cached = lookup(name);
        if (cached != null) return cached;
        CacheEntry online = fetchOnline(name);
        if (online != null) {
            inMemory.put(name.toLowerCase(), online);
            writeDiskCache(name, online.uuid);
            return online.uuid;
        }
        return null;
    }

    public void fetchAsync(String name, java.util.function.Consumer<UUID> callback) {
        executor.submit(() -> {
            UUID result;
            try {
                result = fetch(name);
            } catch (Exception e) {
                LOGGER.warn("[AuthId] Mojang lookup failed for {}: {}", name, e.getMessage());
                result = null;
            }
            callback.accept(result);
        });
    }

    private CacheEntry fetchOnline(String name) {
        try {
            URLConnection conn = URI.create(MOJANG_API + name).toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(body, JsonObject.class);
            if (!obj.has("id")) return null;
            String id = obj.get("id").getAsString();
            UUID uuid = uuidFromUndashed(id);
            return new CacheEntry(uuid, System.currentTimeMillis());
        } catch (IOException e) {
            LOGGER.debug("[AuthId] Mojang offline/timeout for {}: {}", name, e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("[AuthId] Mojang parse error for {}: {}", name, e.getMessage());
            return null;
        }
    }

    private UUID readDiskCache(String name) {
        Path p = cacheDir.resolve(name.toLowerCase() + ".uuid");
        try {
            if (!Files.exists(p)) return null;
            String content = Files.readString(p, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) return null;
            String[] parts = content.split("\\|", 2);
            UUID uuid = uuidFromUndashed(parts[0]);
            if (parts.length > 1) {
                long ts = Long.parseLong(parts[1].trim());
                if (System.currentTimeMillis() - ts > config.mojangCacheTtlMs * 4) {
                    return null;
                }
            }
            return uuid;
        } catch (Exception e) {
            return null;
        }
    }

    private void writeDiskCache(String name, UUID uuid) {
        try {
            Files.createDirectories(cacheDir);
            Path p = cacheDir.resolve(name.toLowerCase() + ".uuid");
            Files.writeString(p, uuid.toString().replace("-", "") + "|" + System.currentTimeMillis(),
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static UUID uuidFromUndashed(String undashed) {
        String s = undashed.replace("-", "");
        if (s.length() != 32) throw new IllegalArgumentException("Not a UUID: " + undashed);
        long msb = Long.parseUnsignedLong(s.substring(0, 16), 16);
        long lsb = Long.parseUnsignedLong(s.substring(16, 32), 16);
        return new UUID(msb, lsb);
    }

    private static final class CacheEntry {
        final UUID uuid;
        final long fetchedAt;

        CacheEntry(UUID uuid, long fetchedAt) {
            this.uuid = uuid;
            this.fetchedAt = fetchedAt;
        }

        boolean isExpired(long ttl) {
            return System.currentTimeMillis() - fetchedAt > ttl;
        }
    }
}
