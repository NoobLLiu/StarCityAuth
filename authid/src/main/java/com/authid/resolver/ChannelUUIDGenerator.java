package com.authid.resolver;

import com.authid.PlayerChannel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class ChannelUUIDGenerator {

    private static final byte[] SALT_PREMIUM = "authid:premium:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SALT_OFFLINE_CUSTOM = "authid:offline:name:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SALT_BEDROCK_ZERO_FALLBACK = "authid:bedrock:zero:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SALT_GEYSER_EXTRA = ":geyser:".getBytes(StandardCharsets.UTF_8);

    private ChannelUUIDGenerator() {}

    public static boolean isAllZeroUuid(UUID uuid) {
        return uuid != null && uuid.getMostSignificantBits() == 0L && uuid.getLeastSignificantBits() == 0L;
    }

    public static UUID javaOfflineUuidCustom(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Offline name must not be blank");
        }
        return nameHashToUuid(SALT_OFFLINE_CUSTOM, name.toLowerCase());
    }

    public static UUID bedrockUuidFromZeroFallback(String name, String geyserExtra) {
        String payload = name.toLowerCase()
                + (geyserExtra != null ? SALT_GEYSER_EXTRA + geyserExtra : "");
        return nameHashToUuid(SALT_BEDROCK_ZERO_FALLBACK, payload);
    }

    public static UUID canonicalFromChannel(PlayerChannel channel, String name, UUID channelUuid, String geyserExtra) {
        return switch (channel) {
            case JAVA_PREMIUM -> channelUuid;
            case JAVA_OFFLINE -> javaOfflineUuidCustom(name);
            case BEDROCK_GEYSER -> {
                if (isAllZeroUuid(channelUuid)) {
                    yield bedrockUuidFromZeroFallback(name, geyserExtra);
                }
                yield channelUuid;
            }
        };
    }

    private static UUID nameHashToUuid(byte[] salt, String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(payload.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            long msb = 0, lsb = 0;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (digest[i] & 0xff);
                lsb = (lsb << 8) | (digest[8 + i] & 0xff);
            }
            msb = (msb & 0xFFFFFFFFFFFF0FFFBL) | 0x0000000000004000L;
            lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
            return new UUID(msb, lsb);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
