package com.authid;

public enum PlayerChannel {

    JAVA_PREMIUM("java_premium", "Java Premium"),
    JAVA_OFFLINE("java_offline", "Java Offline"),
    BEDROCK_GEYSER("bedrock_geyser", "Bedrock (Geyser)");

    private final String id;
    private final String displayName;

    PlayerChannel(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PlayerChannel fromId(String id) {
        for (PlayerChannel c : values()) {
            if (c.id.equals(id)) return c;
        }
        throw new IllegalArgumentException("Unknown PlayerChannel id: " + id);
    }
}
