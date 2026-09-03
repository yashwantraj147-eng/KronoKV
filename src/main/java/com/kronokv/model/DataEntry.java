package com.kronokv.model;

public record DataEntry(String key, String value, long expiresAt) {

    public boolean hasExpired() {
        if (expiresAt == -1) {
            return false;
        }
        return System.currentTimeMillis() > expiresAt;
    }

    public static DataEntry of(String key, String value) {
        return new DataEntry(key, value, -1);
    }

    public static DataEntry of(String key, String value, long ttlSeconds) {
        long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000);
        return new DataEntry(key, value, expiresAt);
    }
}
