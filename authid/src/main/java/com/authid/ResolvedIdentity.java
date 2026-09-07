package com.authid;

import java.util.Objects;
import java.util.UUID;

public final class ResolvedIdentity {

    private final String rawName;
    private final UUID channelUuid;
    private final PlayerChannel channel;
    private final UUID canonicalUuid;
    private final String mojangName;
    private final boolean mojangVerified;
    private final long createdAt;
    private long lastSeenAt;

    public ResolvedIdentity(String rawName,
                            UUID channelUuid,
                            PlayerChannel channel,
                            UUID canonicalUuid,
                            String mojangName,
                            boolean mojangVerified) {
        this.rawName = Objects.requireNonNull(rawName);
        this.channelUuid = Objects.requireNonNull(channelUuid);
        this.channel = Objects.requireNonNull(channel);
        this.canonicalUuid = Objects.requireNonNull(canonicalUuid);
        this.mojangName = mojangName;
        this.mojangVerified = mojangVerified;
        this.createdAt = System.currentTimeMillis();
        this.lastSeenAt = this.createdAt;
    }

    public String getRawName() {
        return rawName;
    }

    public UUID getChannelUuid() {
        return channelUuid;
    }

    public PlayerChannel getChannel() {
        return channel;
    }

    public UUID getCanonicalUuid() {
        return canonicalUuid;
    }

    public String getMojangName() {
        return mojangName;
    }

    public boolean isMojangVerified() {
        return mojangVerified;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastSeenAt() {
        return lastSeenAt;
    }

    public void touch() {
        this.lastSeenAt = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResolvedIdentity that)) return false;
        return channelUuid.equals(that.channelUuid) && channel == that.channel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelUuid, channel);
    }

    @Override
    public String toString() {
        return "ResolvedIdentity{" +
                "rawName='" + rawName + '\'' +
                ", channel=" + channel +
                ", channelUuid=" + channelUuid +
                ", canonicalUuid=" + canonicalUuid +
                ", mojangVerified=" + mojangVerified +
                '}';
    }
}
