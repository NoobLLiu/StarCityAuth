package com.authid.resolver;

import com.authid.AuthIdConfig;
import com.authid.PlayerChannel;
import com.authid.ResolvedIdentity;
import com.authid.store.PlayerIdentityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class IdentityResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("authid");

    private final PlayerIdentityStore store;
    private final MojangProfileResolver mojang;
    private final AuthIdConfig config;

    public IdentityResolver(PlayerIdentityStore store, MojangProfileResolver mojang, AuthIdConfig config) {
        this.store = store;
        this.mojang = mojang;
        this.config = config;
    }

    public ResolvedIdentity resolve(String rawName, UUID channelUuid, PlayerChannel channel, String geyserExtra) {
        PlayerIdentityStore.StoredEntry byChannel = store.findByChannelUuid(channelUuid);
        if (byChannel != null) {
            return new ResolvedIdentity(
                    byChannel.rawName,
                    byChannel.channelUuid,
                    PlayerChannel.fromId(byChannel.channel),
                    byChannel.canonicalUuid,
                    byChannel.mojangName,
                    byChannel.mojangVerified
            );
        }

        UUID mojangUuid = null;
        if (config.autoLinkByMojangName && config.enableMojangLookup && MojangProfileResolver.isEnabledForConfig(config)) {
            mojangUuid = mojang.lookup(rawName);
            if (mojangUuid == null) {
                mojang.fetchAsync(rawName, uuid -> {
                    if (uuid != null) {
                        LOGGER.info("[AuthId] Deferred Mojang UUID resolved for {} -> {}", rawName, uuid);
                    }
                });
            }
        }

        UUID canonical;
        String mojangName = null;
        boolean mojangVerified = false;

        if (channel == PlayerChannel.JAVA_PREMIUM) {
            canonical = channelUuid;
            mojangName = rawName;
            mojangVerified = true;
        } else if (mojangUuid != null) {
            PlayerIdentityStore.StoredEntry byMojang = store.findByCanonicalUuid(mojangUuid);
            if (byMojang != null) {
                canonical = byMojang.canonicalUuid;
                mojangName = byMojang.mojangName != null ? byMojang.mojangName : rawName;
                mojangVerified = byMojang.mojangVerified;
            } else {
                canonical = mojangUuid;
                mojangName = rawName;
                mojangVerified = true;
            }
        } else {
            canonical = ChannelUUIDGenerator.canonicalFromChannel(channel, rawName, channelUuid, geyserExtra);
            PlayerIdentityStore.StoredEntry byCanonical = store.findByCanonicalUuid(canonical);
            if (byCanonical != null) {
                canonical = byCanonical.canonicalUuid;
                mojangName = byCanonical.mojangName;
                mojangVerified = byCanonical.mojangVerified;
            }
        }

        ResolvedIdentity id = new ResolvedIdentity(rawName, channelUuid, channel, canonical, mojangName, mojangVerified);
        store.upsert(id);
        LOGGER.info("[AuthId] Resolved {} ({}) channelUuid={} canonical={} mojangVerified={}",
                rawName, channel.getId(), channelUuid, canonical, mojangVerified);
        return id;
    }
}
