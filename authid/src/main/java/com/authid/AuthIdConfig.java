package com.authid;

public class AuthIdConfig {

    public boolean enableMojangLookup = true;
    public long mojangCacheTtlMs = 6_000_000L;
    public boolean autoLinkByMojangName = true;
    public boolean strictCanonicalRegistry = false;
    public long blacklistedZeroUuidTtlMs = 86_400_000L;

    public static final AuthIdConfig DEFAULTS = new AuthIdConfig();
}
